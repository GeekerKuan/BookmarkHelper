package pro.kisscat.www.bookmarkhelper.sync.task

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pro.kisscat.www.bookmarkhelper.sync.root.UncertainRootStateException

class TaskErrorMessageMapperTest {
    @Test
    fun rootDenialGetsActionableAuthorizationMessage() {
        val message = TaskErrorMessageMapper.message(
            "无法读取浏览器数据",
            IOException("未获得 Root 权限，请允许 su"),
        )

        assertTrue(message.contains("Root 授权"))
        assertFalse(message.contains("IOException"))
    }

    @Test
    fun runningBrowserGetsCloseAndRetryGuidance() {
        val message = TaskErrorMessageMapper.message(
            "写入浏览器已安全停止",
            IOException("Edge History 正在使用 WAL；为保护数据，本版本拒绝写入"),
        )

        assertTrue(message.contains("关闭对应浏览器"))
    }

    @Test
    fun incompatibleSchemaDoesNotExposeTechnicalStack() {
        val message = TaskErrorMessageMapper.message(
            "无法读取浏览器数据",
            IOException("Via 标签页数据库结构不受支持"),
        )

        assertTrue(message.contains("数据格式"))
        assertTrue(message.contains("诊断日志"))
        assertFalse(message.contains("IOException"))
    }

    @Test
    fun concurrentChangeIsNotMisreportedAsBackgroundTermination() {
        val message = TaskErrorMessageMapper.message(
            "写入浏览器已安全停止",
            IOException("Edge 收藏夹在同步期间发生变化，已取消写入"),
        )

        assertTrue(message.contains("数据发生了变化"))
        assertFalse(message.contains("后台"))
        assertFalse(message.contains("进程终止"))
    }

    @Test
    fun unknownIoFailureDoesNotClaimAppWasKilledInBackground() {
        val message = TaskErrorMessageMapper.message(
            "无法读取浏览器数据",
            IOException("unexpected pipe failure"),
        )

        assertTrue(message.contains("安全边界"))
        assertFalse(message.contains("后台"))
        assertFalse(message.contains("系统终止"))
    }

    @Test
    fun uncertainRootStateTakesPriorityOverGenericIo() {
        val message = TaskErrorMessageMapper.message(
            "写入浏览器已安全停止",
            UncertainRootStateException("Root command timed out", true, null),
        )

        assertTrue(message.contains("无法确认浏览器文件当前状态"))
        assertTrue(message.contains("保护数据"))
    }
}
