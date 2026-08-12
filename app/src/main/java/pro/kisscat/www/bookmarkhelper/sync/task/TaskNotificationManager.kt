package pro.kisscat.www.bookmarkhelper.sync.task

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import pro.kisscat.www.bookmarkhelper.activity.TaskActivity

/** Owns only task notifications; every terminal transition explicitly reclaims them. */
internal object TaskNotificationManager {
    private const val CHANNEL_ID = "browser_data_tasks"
    private const val NOTIFICATION_ID = 2201
    @Volatile private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID,
                "数据导入任务",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "提醒需要确认的数据导入任务"
            })
            // Tasks are process-scoped. A process restart invalidates every old PendingIntent target.
            manager.cancelAll()
            initialized = true
        }
    }

    fun showPendingConfirmation(context: Context, task: SyncTaskState) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val contentIntent = PendingIntent.getActivity(
            context,
            requestCode(task.id),
            TaskActivity.intent(context, task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("${task.kind.labelForNotification()}等待确认")
            .setContentText("数据已读取完成，点按核对后保存到数据管理")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(tag(task.id), NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context?, taskId: Long) {
        context?.getSystemService(NotificationManager::class.java)
            ?.cancel(tag(taskId), NOTIFICATION_ID)
    }

    private fun tag(taskId: Long) = "bookmarkhelper-task-$taskId"
    private fun requestCode(taskId: Long) = (taskId xor (taskId ushr 32)).toInt() and Int.MAX_VALUE
}

private fun SyncTaskKind.labelForNotification() = when (this) {
    SyncTaskKind.BOOKMARKS -> "收藏"
    SyncTaskKind.HISTORY -> "历史记录"
    SyncTaskKind.OPEN_TABS -> "标签页"
}
