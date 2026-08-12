package pro.kisscat.www.bookmarkhelper.sync.task

import android.content.Context
import java.io.IOException
import java.util.LinkedHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pro.kisscat.www.bookmarkhelper.entry.rule.Rule
import pro.kisscat.www.bookmarkhelper.sync.adapter.BrowserAdapterRegistry
import pro.kisscat.www.bookmarkhelper.sync.adapter.BrowserExportService
import pro.kisscat.www.bookmarkhelper.sync.adapter.BrowserExportStatus
import pro.kisscat.www.bookmarkhelper.sync.adapter.BrowserTargetExportResult
import pro.kisscat.www.bookmarkhelper.sync.adapter.BrowserDataOperation
import pro.kisscat.www.bookmarkhelper.sync.adapter.BrowserDataType
import pro.kisscat.www.bookmarkhelper.sync.model.BrowserDataSnapshot
import pro.kisscat.www.bookmarkhelper.sync.model.BrowserId
import pro.kisscat.www.bookmarkhelper.sync.model.BrowserRecordIdentity
import pro.kisscat.www.bookmarkhelper.sync.model.CanonicalBookmark
import pro.kisscat.www.bookmarkhelper.sync.model.CanonicalHistoryVisit
import pro.kisscat.www.bookmarkhelper.sync.model.CanonicalOpenTab
import pro.kisscat.www.bookmarkhelper.sync.model.IntermediateDataRepository
import pro.kisscat.www.bookmarkhelper.sync.model.IntermediateDataExportSource
import pro.kisscat.www.bookmarkhelper.sync.root.UncertainRootStateException
import pro.kisscat.www.bookmarkhelper.util.log.LogHelper

enum class SyncTaskKind { BOOKMARKS, HISTORY, OPEN_TABS }
enum class SyncTaskDirection { IMPORT, EXPORT }
enum class SyncTaskPhase { RANGE, QUEUED, PREPARING, PREVIEW, RUNNING, SUCCESS, ERROR, CANCELLED }

data class HistoryRange(val fromUnixSeconds: Long, val toUnixSeconds: Long, val label: String)
data class TaskBookmark(
    val title: String,
    val url: String,
    val folder: String,
    val duplicate: Boolean,
)

data class SyncTaskState(
    val id: Long,
    val kind: SyncTaskKind,
    val phase: SyncTaskPhase,
    val title: String,
    val detail: String,
    val direction: SyncTaskDirection = SyncTaskDirection.IMPORT,
    val sources: Set<BrowserId> = emptySet(),
    val targets: Set<BrowserId> = emptySet(),
    val progress: Int = 0,
    val sourceCount: Int = 0,
    val newCount: Int = 0,
    val duplicateCount: Int = 0,
    val selectionRequestedCount: Int = 0,
    val selectionMissingCount: Int = 0,
    val cancellationRequested: Boolean = false,
    val bookmarks: List<TaskBookmark> = emptyList(),
    val targetResults: List<BrowserTargetExportResult> = emptyList(),
    val historyRange: HistoryRange? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
) {
    val isExecuting get() = phase == SyncTaskPhase.PREPARING || phase == SyncTaskPhase.RUNNING
    val isPending get() = phase == SyncTaskPhase.RANGE || phase == SyncTaskPhase.QUEUED || phase == SyncTaskPhase.PREVIEW
    val isFinished get() = phase == SyncTaskPhase.SUCCESS || phase == SyncTaskPhase.ERROR || phase == SyncTaskPhase.CANCELLED
}

private enum class Work { PREVIEW, SAVE_TO_LIBRARY, EXPORT_TO_BROWSERS }
private enum class Resource { ROOT_DATA, VIA_DATA, EDGE_PROCESS, EDGE_BOOKMARKS, EDGE_HISTORY, EDGE_TABS }

private class TaskRecord(
    var state: SyncTaskState,
    var work: Work = Work.PREVIEW,
    var sourceSnapshots: List<BrowserDataSnapshot> = emptyList(),
    val selectedRecordIds: Set<Long>? = null,
    @Volatile var cancelRequested: Boolean = false,
)

private class TaskCancellation : RuntimeException()

/**
 * Resource-aware transfer coordinator. Export queue specs are persisted so a
 * selected subset can never become an all-record export after process recreation.
 */
object SyncTaskCoordinator {
    private val ids = AtomicLong(System.currentTimeMillis())
    private val executor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "bookmarkhelper-task-worker").apply { isDaemon = false }
    }
    private val records = LinkedHashMap<Long, TaskRecord>()
    private val activeResources = mutableSetOf<Resource>()
    private val mutableTasks = MutableStateFlow<List<SyncTaskState>>(emptyList())
    val tasks: StateFlow<List<SyncTaskState>> = mutableTasks.asStateFlow()

    @Volatile private var adapters: BrowserAdapterRegistry? = null
    @Volatile private var appContext: Context? = null

    @Synchronized
    fun configure(context: Context, rule: Rule) {
        appContext = context.applicationContext
        adapters = BrowserAdapterRegistry.viaAndEdge(context, rule.source, rule.target)
        TaskNotificationManager.initialize(context.applicationContext)
        restorePendingExports(context.applicationContext)
    }

    fun supportedBrowsers(
        kind: SyncTaskKind,
        direction: SyncTaskDirection,
    ): Set<BrowserId> {
        val registry = requireNotNull(adapters) { "浏览器适配器尚未初始化" }
        val operation = if (direction == SyncTaskDirection.IMPORT) {
            BrowserDataOperation.READ
        } else {
            BrowserDataOperation.WRITE
        }
        return registry.supporting(kind.dataType(), operation).toSet()
    }

    @Synchronized
    private fun restorePendingExports(context: Context) {
        val registry = requireNotNull(adapters)
        var restoredWork = false
        ExportTaskPersistence.load(context).forEach { pending ->
            if (records.containsKey(pending.id)) return@forEach
            ids.updateAndGet { current -> maxOf(current, pending.id) }
            val supported = registry.supporting(
                pending.kind.dataType(),
                BrowserDataOperation.WRITE,
            ).toSet()
            val unsupported = pending.targets - supported
            if (unsupported.isNotEmpty()) {
                records[pending.id] = TaskRecord(
                    SyncTaskState(
                        id = pending.id,
                        kind = pending.kind,
                        phase = SyncTaskPhase.ERROR,
                        title = "无法恢复导出任务",
                        detail = "${unsupported.browserLabels()} 已不再支持写入，没有执行写入",
                        direction = SyncTaskDirection.EXPORT,
                        targets = pending.targets,
                        historyRange = pending.historyRange,
                        selectionRequestedCount = pending.selectedRecordIds?.size ?: 0,
                        createdAtMillis = pending.createdAtMillis,
                    ),
                    work = Work.EXPORT_TO_BROWSERS,
                    selectedRecordIds = pending.selectedRecordIds,
                )
                ExportTaskPersistence.remove(context, pending.id)
                return@forEach
            }
            records[pending.id] = TaskRecord(
                SyncTaskState(
                    id = pending.id,
                    kind = pending.kind,
                    phase = SyncTaskPhase.QUEUED,
                    title = "导出${pending.kind.label()}",
                    detail = "应用进程曾在任务完成前结束；已按原选择恢复，等待写入 ${pending.targets.browserLabels()}",
                    direction = SyncTaskDirection.EXPORT,
                    targets = pending.targets,
                    historyRange = pending.historyRange,
                    selectionRequestedCount = pending.selectedRecordIds?.size ?: 0,
                    createdAtMillis = pending.createdAtMillis,
                ),
                work = Work.EXPORT_TO_BROWSERS,
                selectedRecordIds = pending.selectedRecordIds,
            )
            restoredWork = true
        }
        publishAndPump()
        if (restoredWork) SyncTaskForegroundService.start(context)
    }

    @Synchronized
    fun enqueueImport(
        kind: SyncTaskKind,
        sources: Set<BrowserId>,
        historyRange: HistoryRange? = null,
    ): Long {
        require(sources.isNotEmpty()) { "至少选择一个浏览器" }
        if (kind == SyncTaskKind.HISTORY) requireNotNull(historyRange) { "历史记录需要时间范围" }
        val registry = requireNotNull(adapters) { "浏览器适配器尚未初始化" }
        val supported = registry.supporting(kind.dataType(), BrowserDataOperation.READ).toSet()
        require(sources.all(supported::contains)) { "所选浏览器尚不支持读取${kind.label()}" }
        val id = ids.incrementAndGet()
        records[id] = TaskRecord(
            SyncTaskState(
                id = id,
                kind = kind,
                phase = SyncTaskPhase.QUEUED,
                title = kind.label(),
                detail = "已加入队列，等待读取 ${sources.browserLabels()}",
                sources = sources,
                historyRange = historyRange,
            )
        )
        publishAndPump()
        SyncTaskForegroundService.start(appContext)
        return id
    }

    /** Exports all managed records of [kind] into one or more target browsers. */
    @Synchronized
    fun enqueueExport(
        kind: SyncTaskKind,
        targets: Set<BrowserId>,
        historyRange: HistoryRange? = null,
        selectedRecordIds: Set<Long>? = null,
    ): Long {
        require(targets.isNotEmpty()) { "至少选择一个目标浏览器" }
        require(selectedRecordIds == null || selectedRecordIds.isNotEmpty()) {
            "至少选择一条要导出的数据"
        }
        val stableSelection = selectedRecordIds?.toSet()
        val registry = requireNotNull(adapters) { "浏览器适配器尚未初始化" }
        val supported = registry.supporting(kind.dataType(), BrowserDataOperation.WRITE).toSet()
        val unsupported = targets - supported
        val id = ids.incrementAndGet()
        if (unsupported.isNotEmpty()) {
            records[id] = TaskRecord(
                SyncTaskState(
                    id = id,
                    kind = kind,
                    phase = SyncTaskPhase.ERROR,
                    title = "无法导出${kind.label()}",
                    detail = "${unsupported.browserLabels()} 尚不支持写入${kind.label()}，没有执行写入",
                    direction = SyncTaskDirection.EXPORT,
                    targets = targets,
                    historyRange = historyRange,
                    selectionRequestedCount = stableSelection?.size ?: 0,
                ),
                work = Work.EXPORT_TO_BROWSERS,
                selectedRecordIds = stableSelection,
            )
            publish()
            return id
        }
        val createdAt = System.currentTimeMillis()
        val state = SyncTaskState(
                id = id,
                kind = kind,
                phase = SyncTaskPhase.QUEUED,
                title = "导出${kind.label()}",
                detail = "已加入队列，等待写入 ${targets.browserLabels()}",
                direction = SyncTaskDirection.EXPORT,
                targets = targets,
                historyRange = historyRange,
                selectionRequestedCount = stableSelection?.size ?: 0,
                createdAtMillis = createdAt,
            )
        ExportTaskPersistence.save(
            requireNotNull(appContext) { "应用环境尚未初始化" },
            PersistedExportTask(
                id = id,
                kind = kind,
                targets = targets,
                historyRange = historyRange,
                selectedRecordIds = stableSelection,
                createdAtMillis = createdAt,
            ),
        )
        records[id] = TaskRecord(
            state,
            work = Work.EXPORT_TO_BROWSERS,
            selectedRecordIds = stableSelection,
        )
        publishAndPump()
        SyncTaskForegroundService.start(appContext)
        return id
    }

    /** Compatibility entry points retained for already-created task pages. */
    fun enqueueBookmarks(): Long = enqueueImport(SyncTaskKind.BOOKMARKS, setOf(BrowserId.VIA))
    fun enqueueHistory(range: HistoryRange): Long =
        enqueueImport(SyncTaskKind.HISTORY, setOf(BrowserId.VIA), range)

    @Synchronized
    fun createHistoryDraft(): Long {
        val id = ids.incrementAndGet()
        records[id] = TaskRecord(
            SyncTaskState(
                id = id,
                kind = SyncTaskKind.HISTORY,
                phase = SyncTaskPhase.RANGE,
                title = "历史记录",
                detail = "请选择导入时间范围",
                sources = setOf(BrowserId.VIA),
            )
        )
        publish()
        return id
    }

    @Synchronized
    fun enqueueHistory(id: Long, range: HistoryRange) {
        val record = records[id] ?: return
        if (record.state.phase != SyncTaskPhase.RANGE) return
        record.state = record.state.copy(
            phase = SyncTaskPhase.QUEUED,
            detail = "${range.label} · 已加入队列",
            historyRange = range,
        )
        publishAndPump()
        SyncTaskForegroundService.start(appContext)
    }

    @Synchronized
    fun confirmImport(id: Long) {
        val record = records[id] ?: return
        if (record.state.phase != SyncTaskPhase.PREVIEW) return
        TaskNotificationManager.cancel(appContext, id)
        record.work = Work.SAVE_TO_LIBRARY
        record.state = record.state.copy(
            phase = SyncTaskPhase.QUEUED,
            detail = "已确认，等待保存到数据管理",
            progress = 60,
        )
        publishAndPump()
        SyncTaskForegroundService.start(appContext)
    }

    @Synchronized
    fun remove(id: Long) {
        val record = records[id] ?: return
        if (!record.state.isExecuting) {
            records.remove(id)
            removePersistedExport(record)
            TaskNotificationManager.cancel(appContext, id)
            publish()
        }
    }

    /** Cooperative cancellation: an in-flight Root copy finishes its current safe boundary first. */
    @Synchronized
    fun cancel(id: Long) {
        val record = records[id] ?: return
        if (record.state.isFinished) return
        record.cancelRequested = true
        TaskNotificationManager.cancel(appContext, id)
        if (!record.state.isExecuting) {
            record.sourceSnapshots = emptyList()
            record.state = record.state.copy(
                phase = SyncTaskPhase.CANCELLED,
                title = "任务已取消",
                detail = "任务已取消，未继续读取或写入浏览器数据",
                cancellationRequested = true,
            )
            removePersistedExport(record)
            publishAndPump()
        } else {
            record.state = record.state.copy(
                detail = "正在安全取消，当前 Root 文件操作完成后停止",
                cancellationRequested = true,
            )
            publish()
        }
    }

    fun task(id: Long): SyncTaskState? = synchronized(this) { records[id]?.state }

    @Synchronized
    private fun publishAndPump() {
        publish()
        pump()
    }

    private fun publish() {
        mutableTasks.value = records.values.map { it.state }.sortedByDescending { it.createdAtMillis }
    }

    @Synchronized
    private fun pump() {
        records.values.forEach { record ->
            if (record.state.phase != SyncTaskPhase.QUEUED) return@forEach
            val needed = resourcesFor(record)
            if (needed.any(activeResources::contains)) return@forEach
            activeResources.addAll(needed)
            val saving = record.work == Work.SAVE_TO_LIBRARY
            val exporting = record.work == Work.EXPORT_TO_BROWSERS
            record.state = record.state.copy(
                phase = if (saving || exporting) SyncTaskPhase.RUNNING else SyncTaskPhase.PREPARING,
                detail = when {
                    saving -> "正在保存到数据管理"
                    exporting -> "正在准备写入浏览器"
                    else -> "正在准备读取浏览器数据"
                },
                progress = if (saving) 70 else 8,
            )
            publish()
            executor.execute {
                try {
                    when (record.work) {
                        Work.PREVIEW -> preview(record)
                        Work.SAVE_TO_LIBRARY -> saveToLibrary(record)
                        Work.EXPORT_TO_BROWSERS -> exportToBrowsers(record)
                    }
                } finally {
                    synchronized(this) {
                        activeResources.removeAll(needed)
                        publish()
                        pump()
                    }
                }
            }
        }
    }

    private fun resourcesFor(record: TaskRecord): Set<Resource> = buildSet {
        if (record.work == Work.SAVE_TO_LIBRARY) return@buildSet
        add(Resource.ROOT_DATA)
        val browsers = if (record.state.direction == SyncTaskDirection.EXPORT) {
            record.state.targets
        } else {
            record.state.sources
        }
        if (BrowserId.VIA in browsers) add(Resource.VIA_DATA)
        if (BrowserId.EDGE in browsers) {
            add(Resource.EDGE_PROCESS)
            add(when (record.state.kind) {
                SyncTaskKind.BOOKMARKS -> Resource.EDGE_BOOKMARKS
                SyncTaskKind.HISTORY -> Resource.EDGE_HISTORY
                SyncTaskKind.OPEN_TABS -> Resource.EDGE_TABS
            })
        }
    }

    private fun preview(record: TaskRecord) {
        try {
            val registry = requireNotNull(adapters) { "浏览器适配器尚未初始化" }
            val range = record.state.historyRange
            val snapshots = mutableListOf<BrowserDataSnapshot>()
            record.state.sources.sortedBy(BrowserId::ordinal).forEachIndexed { index, browser ->
                ensureNotCancelled(record)
                update(
                    record,
                    15 + ((index + 1) * 25 / record.state.sources.size),
                    "正在读取 ${browser.label()} ${record.state.kind.label()}",
                )
                snapshots += registry[browser].read(
                    record.state.kind.dataType(),
                    range?.fromUnixSeconds ?: 0L,
                    range?.toUnixSeconds ?: Long.MAX_VALUE,
                )
            }
            ensureNotCancelled(record)
            record.sourceSnapshots = snapshots
            val preview = summarize(record.state.kind, snapshots)
            synchronized(this) {
                ensureNotCancelled(record)
                record.state = record.state.copy(
                    phase = SyncTaskPhase.PREVIEW,
                    title = "确认${record.state.kind.label()}",
                    detail = "已读取 ${record.state.sources.browserLabels()}，确认后保存到数据管理",
                    progress = 55,
                    sourceCount = preview.rawCount,
                    newCount = preview.uniqueCount,
                    duplicateCount = preview.duplicateCount,
                    bookmarks = preview.bookmarks,
                )
                publish()
                TaskNotificationManager.showPendingConfirmation(
                    requireNotNull(appContext), record.state
                )
            }
        } catch (_: TaskCancellation) {
            finishCancelled(record, "读取已在安全边界停止，没有保存未确认的数据")
        } catch (error: Throwable) {
            fail(record, "无法读取浏览器数据", error)
        }
    }

    private fun saveToLibrary(record: TaskRecord) {
        try {
            require(record.sourceSnapshots.isNotEmpty()) { "预览数据已失效，请重新读取" }
            var savedSources = 0
            record.sourceSnapshots.forEach { snapshot ->
                ensureNotCancelled(record)
                val datasetId = "import-${record.state.id}-${snapshot.source.name}-${record.state.kind.name}"
                IntermediateDataRepository.save(
                    datasetId,
                    "${snapshot.source.label()} ${record.state.kind.label()}",
                    snapshot,
                )
                savedSources++
            }
            if (record.cancelRequested) {
                finishCancelled(record, "已在安全边界停止；已完成的 $savedSources 个来源保留在数据管理中")
                return
            }
            synchronized(this) {
                record.sourceSnapshots = emptyList()
                record.state = record.state.copy(
                    phase = SyncTaskPhase.SUCCESS,
                    detail = "${record.state.kind.label()}已保存到数据管理，共 ${record.state.newCount} 条",
                    progress = 100,
                )
                TaskNotificationManager.cancel(appContext, record.state.id)
                publish()
            }
        } catch (_: TaskCancellation) {
            finishCancelled(record, "保存已在安全边界停止")
        } catch (error: Throwable) {
            fail(record, "导入已安全停止", error)
        }
    }

    private fun exportToBrowsers(record: TaskRecord) {
        try {
            ensureNotCancelled(record)
            val range = record.state.historyRange
            update(record, 18, "正在从数据管理整理${record.state.kind.label()}")
            val selection = IntermediateDataExportSource.selection(
                record.state.kind.dataType(),
                range?.fromUnixSeconds ?: 0L,
                range?.toUnixSeconds ?: Long.MAX_VALUE,
                record.selectedRecordIds,
            )
            ensureNotCancelled(record)
            val registry = requireNotNull(adapters) { "浏览器适配器尚未初始化" }
            update(record, 35, "正在写入 ${record.state.targets.browserLabels()}")
            val result = BrowserExportService(registry).export(
                record.state.kind.dataType(),
                selection.snapshots,
                record.state.targets,
                beforeTarget = {
                    ensureNotCancelled(record)
                    update(record, 50, "正在写入 ${it.label()} ${record.state.kind.label()}")
                },
            )
            val inserted = result.inserted + result.updated
            val skipped = result.skipped
            val failures = result.targets.filterNot(BrowserTargetExportResult::isSuccessful)
            synchronized(this) {
                record.state = record.state.copy(
                    phase = if (failures.isEmpty()) SyncTaskPhase.SUCCESS else SyncTaskPhase.ERROR,
                    title = if (failures.isEmpty()) "${record.state.kind.label()}导出完成" else
                        "${record.state.kind.label()}导出未完全成功",
                    detail = exportDetail(
                        result.targets,
                        inserted,
                        skipped,
                        selection.missingRecordCount,
                    ),
                    progress = 100,
                    sourceCount = selection.matchedRecordCount,
                    newCount = inserted,
                    duplicateCount = skipped,
                    selectionRequestedCount = selection.requestedRecordCount,
                    selectionMissingCount = selection.missingRecordCount,
                    targetResults = result.targets,
                )
                removePersistedExport(record)
                TaskNotificationManager.cancel(appContext, record.state.id)
                publish()
            }
        } catch (_: TaskCancellation) {
            finishCancelled(record, "已在浏览器写入事务的安全边界停止；已经完成的目标不会回滚")
        } catch (error: Throwable) {
            fail(record, "写入浏览器已安全停止", error)
        }
    }

    private fun ensureNotCancelled(record: TaskRecord) {
        if (record.cancelRequested) throw TaskCancellation()
    }

    private fun finishCancelled(record: TaskRecord, detail: String) = synchronized(this) {
        record.sourceSnapshots = emptyList()
        record.state = record.state.copy(
            phase = SyncTaskPhase.CANCELLED,
            title = "任务已取消",
            detail = detail,
            cancellationRequested = true,
        )
        removePersistedExport(record)
        TaskNotificationManager.cancel(appContext, record.state.id)
        publish()
    }

    private data class PreviewSummary(
        val rawCount: Int,
        val uniqueCount: Int,
        val duplicateCount: Int,
        val bookmarks: List<TaskBookmark>,
    )

    private fun summarize(kind: SyncTaskKind, snapshots: List<BrowserDataSnapshot>): PreviewSummary {
        return when (kind) {
            SyncTaskKind.BOOKMARKS -> {
                val all = snapshots.flatMap(BrowserDataSnapshot::bookmarks)
                val occurrences = all.groupingBy { it.bookmarkKey() }.eachCount()
                val unique = LinkedHashMap<String, CanonicalBookmark>()
                all.forEach { unique.putIfAbsent(it.bookmarkKey(), it) }
                PreviewSummary(
                    all.size,
                    unique.size,
                    all.size - unique.size,
                    unique.values.take(100).map {
                        TaskBookmark(
                            it.title,
                            it.url,
                            it.folderPath,
                            occurrences.getValue(it.bookmarkKey()) > 1,
                        )
                    },
                )
            }
            SyncTaskKind.HISTORY -> snapshots.flatMap(BrowserDataSnapshot::history).summaryBy {
                BrowserRecordIdentity.history(it.url, it.visitedAtEpochMillis)
            }
            SyncTaskKind.OPEN_TABS -> snapshots.flatMap(BrowserDataSnapshot::openTabs).summaryBy {
                BrowserRecordIdentity.openTab(it.url, it.openedAtEpochMillis)
            }
        }
    }

    private fun <T> List<T>.summaryBy(key: (T) -> String): PreviewSummary {
        val unique = LinkedHashMap<String, T>()
        forEach { unique.putIfAbsent(key(it), it) }
        return PreviewSummary(size, unique.size, size - unique.size, emptyList())
    }

    private fun CanonicalBookmark.bookmarkKey() =
        BrowserRecordIdentity.bookmark(url, title, folderPath)

    private fun update(record: TaskRecord, progress: Int, detail: String) = synchronized(this) {
        record.state = record.state.copy(progress = progress, detail = detail)
        publish()
    }

    private fun exportDetail(
        results: List<BrowserTargetExportResult>,
        inserted: Int,
        skipped: Int,
        missingSelectionCount: Int,
    ): String {
        val missing = if (missingSelectionCount > 0) {
            "；$missingSelectionCount 条所选数据已不存在并被安全忽略"
        } else ""
        if (results.all(BrowserTargetExportResult::isSuccessful)) {
            return "已写入 $inserted 条，跳过 $skipped 条重复或无效数据$missing"
        }
        results.filterNot(BrowserTargetExportResult::isSuccessful).forEach { result ->
            LogHelper.e(
                "BrowserExportMeta",
                "browser=${result.browser.name} type=${result.type.name} " +
                    "status=${result.status.name} message=${result.message}",
            )
        }
        return results.joinToString("；") { result ->
            "${result.browser.label()}${result.status.taskLabel()}"
        } + missing
    }

    private fun removePersistedExport(record: TaskRecord) {
        if (record.state.direction == SyncTaskDirection.EXPORT) {
            ExportTaskPersistence.remove(appContext, record.state.id)
        }
    }

    private fun fail(record: TaskRecord, prefix: String, error: Throwable) {
        val stableCode = buildString {
            append(record.state.kind.name)
            append('_').append(record.work.name)
            append('_').append(record.state.progress.coerceIn(0, 100))
            append('_').append(errorCategory(error))
        }
        LogHelper.e(
            "SyncTaskMeta",
            "code=$stableCode kind=${record.state.kind.name} work=${record.work.name} " +
                "phase=${record.state.phase.name} progress=${record.state.progress}",
        )
        LogHelper.e("SyncTask", error)
        synchronized(this) {
            record.state = record.state.copy(
                phase = SyncTaskPhase.ERROR,
                detail = TaskErrorMessageMapper.message(prefix, error),
            )
            removePersistedExport(record)
            TaskNotificationManager.cancel(appContext, record.state.id)
            publish()
        }
    }

    private fun errorCategory(error: Throwable): String = when (error) {
        is UncertainRootStateException -> "ROOT_STATE_UNKNOWN"
        is SecurityException -> "PERMISSION"
        is IOException -> "IO"
        is IllegalStateException, is IllegalArgumentException -> "STATE"
        else -> "UNEXPECTED"
    }
}

private fun SyncTaskKind.dataType() = when (this) {
    SyncTaskKind.BOOKMARKS -> BrowserDataType.BOOKMARKS
    SyncTaskKind.HISTORY -> BrowserDataType.HISTORY
    SyncTaskKind.OPEN_TABS -> BrowserDataType.OPEN_TABS
}

private fun SyncTaskKind.label() = when (this) {
    SyncTaskKind.BOOKMARKS -> "收藏"
    SyncTaskKind.HISTORY -> "历史记录"
    SyncTaskKind.OPEN_TABS -> "标签页"
}

private fun BrowserId.label() = when (this) {
    BrowserId.VIA -> "Via"
    BrowserId.EDGE -> "Edge"
}

private fun BrowserExportStatus.taskLabel() = when (this) {
    BrowserExportStatus.SUCCESS -> "写入完成"
    BrowserExportStatus.NO_DATA -> "没有可写入数据"
    BrowserExportStatus.PARTIAL -> "仅部分完成"
    BrowserExportStatus.UNSUPPORTED -> "不支持此类写入"
    BrowserExportStatus.FAILED -> "写入失败"
}

private fun Set<BrowserId>.browserLabels() = sortedBy(BrowserId::ordinal).joinToString("、") { it.label() }
