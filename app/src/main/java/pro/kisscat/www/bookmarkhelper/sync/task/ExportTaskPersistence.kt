package pro.kisscat.www.bookmarkhelper.sync.task

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject
import pro.kisscat.www.bookmarkhelper.sync.model.BrowserId
import pro.kisscat.www.bookmarkhelper.util.log.LogHelper

internal data class PersistedExportTask(
    val id: Long,
    val kind: SyncTaskKind,
    val targets: Set<BrowserId>,
    val historyRange: HistoryRange?,
    val selectedRecordIds: Set<Long>?,
    val createdAtMillis: Long,
)

/** Durable, fail-closed queue specs for export tasks that may outlive the app process. */
internal object ExportTaskPersistence {
    private const val VERSION = 1
    private const val DIRECTORY = "pending_export_tasks"
    private const val MAX_TASKS = 100
    private const val MAX_SELECTED_IDS = 100_000
    private const val MAX_FILE_BYTES = 4L * 1024L * 1024L

    fun save(context: Context, task: PersistedExportTask) {
        val directory = directory(context)
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("无法创建待导出任务目录")
        }
        val payload = encode(task).toByteArray(StandardCharsets.UTF_8)
        if (payload.size > MAX_FILE_BYTES) throw IOException("部分导出选择过多，无法安全保存任务")
        val destination = file(directory, task.id)
        val temporary = File(directory, "${destination.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temporary, false).use { output ->
                output.write(payload)
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    fun load(context: Context): List<PersistedExportTask> {
        val directory = directory(context)
        val files = directory.listFiles { file ->
            file.isFile && file.name.matches(Regex("task-[0-9]+\\.json"))
        }?.sortedBy(File::lastModified).orEmpty()
        if (files.size > MAX_TASKS) {
            LogHelper.e("ExportTaskPersistence", "待恢复导出任务超过安全上限，拒绝自动恢复")
            return emptyList()
        }
        return files.mapNotNull { candidate ->
            runCatching {
                if (candidate.length() <= 0L || candidate.length() > MAX_FILE_BYTES) {
                    throw IOException("待导出任务文件大小无效")
                }
                decode(candidate.readText(StandardCharsets.UTF_8))
            }.onFailure { error ->
                LogHelper.e("ExportTaskPersistence", error)
                // A corrupt selection must never be reinterpreted as "all records".
                if (!candidate.delete()) {
                    LogHelper.e("ExportTaskPersistence", "无法清理损坏的待导出任务")
                }
            }.getOrNull()
        }
    }

    fun remove(context: Context?, taskId: Long) {
        if (context == null) return
        val candidate = file(directory(context), taskId)
        if (candidate.exists() && !candidate.delete()) {
            LogHelper.e("ExportTaskPersistence", "无法清理已结束的待导出任务 $taskId")
        }
    }

    internal fun encode(task: PersistedExportTask): String {
        require(task.id > 0L)
        require(task.targets.isNotEmpty())
        require(task.selectedRecordIds == null || task.selectedRecordIds.isNotEmpty())
        require((task.selectedRecordIds?.size ?: 0) <= MAX_SELECTED_IDS)
        return JSONObject().apply {
            put("version", VERSION)
            put("id", task.id)
            put("kind", task.kind.name)
            put("targets", JSONArray(task.targets.sortedBy(BrowserId::ordinal).map(BrowserId::name)))
            put("createdAtMillis", task.createdAtMillis)
            task.historyRange?.let { range ->
                put("historyRange", JSONObject().apply {
                    put("fromUnixSeconds", range.fromUnixSeconds)
                    put("toUnixSeconds", range.toUnixSeconds)
                    put("label", range.label)
                })
            }
            if (task.selectedRecordIds == null) {
                put("selectionMode", "ALL")
            } else {
                put("selectionMode", "IDS")
                put("selectedRecordIds", JSONArray(task.selectedRecordIds.sorted()))
            }
        }.toString()
    }

    internal fun decode(payload: String): PersistedExportTask {
        val root = JSONObject(payload)
        require(root.getInt("version") == VERSION) { "Unsupported export task version" }
        val targetsJson = root.getJSONArray("targets")
        val targets: Set<BrowserId> = buildSet {
            for (index in 0 until targetsJson.length()) {
                add(BrowserId.valueOf(targetsJson.getString(index)))
            }
        }
        require(targets.isNotEmpty()) { "Export task has no targets" }
        val selected: Set<Long>? = when (root.getString("selectionMode")) {
            "ALL" -> null
            "IDS" -> root.getJSONArray("selectedRecordIds").let { values ->
                buildSet {
                    require(values.length() in 1..MAX_SELECTED_IDS) { "Invalid selected ID count" }
                    for (index in 0 until values.length()) add(values.getLong(index))
                }
            }
            else -> error("Invalid export selection mode")
        }
        val range = root.optJSONObject("historyRange")?.let {
            HistoryRange(
                it.getLong("fromUnixSeconds"),
                it.getLong("toUnixSeconds"),
                it.getString("label"),
            )
        }
        return PersistedExportTask(
            id = root.getLong("id"),
            kind = SyncTaskKind.valueOf(root.getString("kind")),
            targets = targets,
            historyRange = range,
            selectedRecordIds = selected,
            createdAtMillis = root.getLong("createdAtMillis"),
        ).also {
            require(it.id > 0L)
            require(it.historyRange == null ||
                (it.historyRange.fromUnixSeconds >= 0L &&
                    it.historyRange.toUnixSeconds >= it.historyRange.fromUnixSeconds))
        }
    }

    private fun directory(context: Context) = File(context.filesDir, DIRECTORY)
    private fun file(directory: File, taskId: Long) = File(directory, "task-$taskId.json")
}
