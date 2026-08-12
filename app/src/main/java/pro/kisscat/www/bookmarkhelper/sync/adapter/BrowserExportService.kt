package pro.kisscat.www.bookmarkhelper.sync.adapter

import java.util.LinkedHashMap
import pro.kisscat.www.bookmarkhelper.sync.model.BrowserDataSnapshot
import pro.kisscat.www.bookmarkhelper.sync.model.BrowserId
import pro.kisscat.www.bookmarkhelper.sync.model.BrowserRecordIdentity
import pro.kisscat.www.bookmarkhelper.sync.model.CanonicalBookmark
import pro.kisscat.www.bookmarkhelper.sync.model.CanonicalHistoryVisit
import pro.kisscat.www.bookmarkhelper.sync.model.CanonicalOpenTab

enum class BrowserExportStatus { SUCCESS, NO_DATA, PARTIAL, UNSUPPORTED, FAILED }

data class BrowserTargetExportResult(
    val browser: BrowserId,
    val type: BrowserDataType,
    val status: BrowserExportStatus,
    val requested: Int,
    val inserted: Int = 0,
    val skipped: Int = 0,
    val updated: Int = 0,
    val message: String = "",
) {
    val processed: Int get() = inserted + skipped + updated
    val isSuccessful: Boolean get() = status == BrowserExportStatus.SUCCESS
}

data class BrowserExportBatchResult(
    val type: BrowserDataType,
    val targets: List<BrowserTargetExportResult>,
) {
    val isFullySuccessful: Boolean
        get() = targets.isNotEmpty() && targets.all(BrowserTargetExportResult::isSuccessful)
    val inserted: Int get() = targets.sumOf(BrowserTargetExportResult::inserted)
    val skipped: Int get() = targets.sumOf(BrowserTargetExportResult::skipped)
    val updated: Int get() = targets.sumOf(BrowserTargetExportResult::updated)
}

/**
 * Exports browser-neutral records to one or more explicit browser targets.
 *
 * Records are validated and deduplicated once before any Root transaction. Each
 * target receives an independent result, so one successful browser is never
 * reported as proof that every selected browser succeeded.
 */
class BrowserExportService(
    private val registry: BrowserAdapterRegistry,
) {
    fun capabilityMatrix(): Map<BrowserId, BrowserAdapterCapabilities> =
        registry.capabilityMatrix()

    fun export(
        type: BrowserDataType,
        snapshots: List<BrowserDataSnapshot>,
        targets: Set<BrowserId>,
        beforeTarget: (BrowserId) -> Unit = {},
    ): BrowserExportBatchResult {
        require(targets.isNotEmpty()) { "至少选择一个目标浏览器" }
        val prepared = prepare(type, snapshots)
        val results = targets.sortedBy(BrowserId::ordinal).map { browser ->
            val adapter = registry[browser]
            when {
                !adapter.capabilities.supports(type, BrowserDataOperation.WRITE) ->
                    unsupported(browser, type, prepared.requested)
                prepared.requested == 0 -> BrowserTargetExportResult(
                    browser = browser,
                    type = type,
                    status = BrowserExportStatus.NO_DATA,
                    requested = 0,
                    message = "数据管理中没有可写入的数据",
                )
                else -> {
                    beforeTarget(browser)
                    writeOne(adapter, type, prepared)
                }
            }
        }
        return BrowserExportBatchResult(type, results)
    }

    private fun writeOne(
        adapter: BrowserDataAdapter,
        type: BrowserDataType,
        prepared: PreparedExport,
    ): BrowserTargetExportResult = try {
        val result = adapter.write(type, prepared.snapshot)
        val skipped = result.skipped + prepared.preSkipped
        val processed = result.inserted + result.updated + skipped
        val complete = processed == prepared.requested
        BrowserTargetExportResult(
            browser = adapter.browser,
            type = type,
            status = if (complete) BrowserExportStatus.SUCCESS else BrowserExportStatus.PARTIAL,
            requested = prepared.requested,
            inserted = result.inserted,
            skipped = skipped,
            updated = result.updated,
            message = if (complete) "写入完成" else
                "浏览器返回的处理数量与请求不一致，未标记为成功",
        )
    } catch (_: UnsupportedBrowserDataOperationException) {
        unsupported(adapter.browser, type, prepared.requested)
    } catch (error: Throwable) {
        BrowserTargetExportResult(
            browser = adapter.browser,
            type = type,
            status = BrowserExportStatus.FAILED,
            requested = prepared.requested,
            message = buildFailureMessage(error),
        )
    }

    private fun unsupported(
        browser: BrowserId,
        type: BrowserDataType,
        requested: Int,
    ) = BrowserTargetExportResult(
        browser = browser,
        type = type,
        status = BrowserExportStatus.UNSUPPORTED,
        requested = requested,
        message = "${browser.name} 尚不支持写入 ${type.name}",
    )

    private fun prepare(
        type: BrowserDataType,
        snapshots: List<BrowserDataSnapshot>,
    ): PreparedExport {
        val source = snapshots.firstOrNull()?.source ?: BrowserId.VIA
        return when (type) {
            BrowserDataType.BOOKMARKS -> prepareBookmarks(source, snapshots)
            BrowserDataType.HISTORY -> prepareHistory(source, snapshots)
            BrowserDataType.OPEN_TABS -> prepareOpenTabs(source, snapshots)
        }
    }

    private fun prepareBookmarks(
        source: BrowserId,
        snapshots: List<BrowserDataSnapshot>,
    ): PreparedExport {
        val all = snapshots.flatMap(BrowserDataSnapshot::bookmarks)
        val unique = LinkedHashMap<String, CanonicalBookmark>()
        all.forEach { record ->
            val url = record.url.trim()
            if (isHttpUrl(url)) {
                unique.putIfAbsent(BrowserRecordIdentity.normalizeUrl(url), record.copy(url = url))
            }
        }
        return PreparedExport(
            BrowserDataSnapshot(source = source, bookmarks = unique.values.toList()),
            all.size,
            all.size - unique.size,
        )
    }

    private fun prepareHistory(
        source: BrowserId,
        snapshots: List<BrowserDataSnapshot>,
    ): PreparedExport {
        val all = snapshots.flatMap(BrowserDataSnapshot::history)
        val unique = LinkedHashMap<String, CanonicalHistoryVisit>()
        all.forEach { record ->
            val url = record.url.trim()
            if (isHttpUrl(url) && record.visitedAtEpochMillis >= 0L) {
                val key = BrowserRecordIdentity.normalizeUrl(url) + '\u0000' +
                    record.visitedAtEpochMillis / 1_000L
                unique.putIfAbsent(key, record.copy(url = url))
            }
        }
        return PreparedExport(
            BrowserDataSnapshot(source = source, history = unique.values.toList()),
            all.size,
            all.size - unique.size,
        )
    }

    private fun prepareOpenTabs(
        source: BrowserId,
        snapshots: List<BrowserDataSnapshot>,
    ): PreparedExport {
        val all = snapshots.flatMap(BrowserDataSnapshot::openTabs)
        val unique = LinkedHashMap<String, CanonicalOpenTab>()
        all.forEach { record ->
            val url = record.url.trim()
            if (isHttpUrl(url)) {
                unique.putIfAbsent(BrowserRecordIdentity.normalizeUrl(url), record.copy(url = url))
            }
        }
        return PreparedExport(
            BrowserDataSnapshot(source = source, openTabs = unique.values.toList()),
            all.size,
            all.size - unique.size,
        )
    }

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)

    private fun buildFailureMessage(error: Throwable): String {
        val detail = error.message?.replace('\n', ' ')?.replace('\r', ' ')?.trim().orEmpty()
        return if (detail.isEmpty()) error.javaClass.simpleName
        else "${error.javaClass.simpleName}：${detail.take(240)}"
    }

    private data class PreparedExport(
        val snapshot: BrowserDataSnapshot,
        val requested: Int,
        val preSkipped: Int,
    )
}
