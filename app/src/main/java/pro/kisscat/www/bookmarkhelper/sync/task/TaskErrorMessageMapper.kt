package pro.kisscat.www.bookmarkhelper.sync.task

import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.TimeoutException
import pro.kisscat.www.bookmarkhelper.exception.ConverterException
import pro.kisscat.www.bookmarkhelper.sync.adapter.UnsupportedBrowserDataOperationException
import pro.kisscat.www.bookmarkhelper.sync.root.UncertainRootStateException

/** User-facing messages stay actionable while diagnostics retain the original throwable. */
internal object TaskErrorMessageMapper {
    fun message(prefix: String, error: Throwable): String {
        val facts = error.causeChain()
        val text = facts.mapNotNull(Throwable::message).joinToString("\n").lowercase()
        val guidance = when {
            facts.any { it is UnsupportedBrowserDataOperationException } ->
                "所选浏览器暂不支持写入这类数据，请更换数据类型或目标浏览器。"
            facts.any { it is UncertainRootStateException } ->
                "无法确认浏览器文件当前状态。为保护数据，后续写入已停止；请按提示重启设备并检查恢复状态。"
            facts.any { it is SecurityException } ||
                text.contains("未获得 root") || text.contains("root 权限") || text.contains("permission denied") ->
                "没有获得完整的 Root 授权，请在 Root 管理器中允许本应用后重试。"
            text.contains("另一个 edge 数据事务") || text.contains("另一个 root") ||
                text.contains("事务正在运行") ->
                "另一项浏览器数据任务正在执行，请等待当前任务完成后重试。"
            text.contains("重新启动") || text.contains("正在使用 wal") ||
                text.contains("仍有未合并事务日志") || text.contains("存在未完成的回滚日志") ->
                "浏览器仍在运行或刚刚更新了数据。请正常关闭对应浏览器后重试。"
            text.contains("发生变化") || text.contains("file unchanged") ->
                "同步期间浏览器数据发生了变化，本次写入已安全取消。请关闭浏览器后重试。"
            text.contains("结构不受支持") || text.contains("不支持的") ||
                text.contains("缺少 roots") || text.contains("缺少") && text.contains("表") ||
                text.contains("校验不一致") || text.contains("格式") || text.contains("解析") ->
                "当前浏览器的数据格式与此版本不兼容，未修改原数据。请发送诊断日志以便适配。"
            facts.any { it is FileNotFoundException } || text.contains("未找到") ->
                "没有找到浏览器数据。请确认浏览器已安装、至少运行过一次，并使用当前 Android 用户。"
            facts.any { it is TimeoutException } || text.contains("超时") ->
                "Root 操作等待超时。请确认 Root 管理器和浏览器状态正常后重试。"
            facts.any { it is InterruptedException } ->
                "任务执行被系统中断，未完成的步骤已停止。请重新发起任务。"
            facts.any { it is ConverterException } ->
                "浏览器拒绝了本次数据操作，原数据未被覆盖。请发送诊断日志以便确认原因。"
            facts.any { it is IOException } ->
                "读取或写入浏览器数据失败，已在安全边界停止。请重试；若仍失败，请发送诊断日志。"
            else -> "任务遇到未预期的问题，已停止操作。请发送诊断日志以便定位。"
        }
        return "$prefix。$guidance"
    }

    private fun Throwable.causeChain(): List<Throwable> = buildList {
        val seen = HashSet<Throwable>()
        var current: Throwable? = this@causeChain
        while (current != null && addIfNew(current, seen) && size < 8) current = current.cause
    }

    private fun MutableList<Throwable>.addIfNew(
        value: Throwable,
        seen: MutableSet<Throwable>,
    ): Boolean {
        if (!seen.add(value)) return false
        add(value)
        return true
    }
}
