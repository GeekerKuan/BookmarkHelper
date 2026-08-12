package pro.kisscat.www.bookmarkhelper.sync.task

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pro.kisscat.www.bookmarkhelper.activity.TaskActivity

/** Keeps active Root reads/saves alive after their page is hidden. */
class SyncTaskForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification(null))
        observer = scope.launch {
            SyncTaskCoordinator.tasks.collectLatest { tasks ->
                val active = tasks.firstOrNull { it.phase.isForegroundWork() }
                if (active == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, notification(active))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            SyncTaskCoordinator.cancel(intent.getLongExtra(EXTRA_TASK_ID, -1L))
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observer?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(task: SyncTaskState?): android.app.Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(task?.title ?: "正在准备数据导入")
            .setContentText(task?.detail ?: "正在启动安全任务环境")
            .setProgress(100, task?.progress ?: 0, task == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (task != null) {
            builder.setContentIntent(PendingIntent.getActivity(
                this,
                requestCode(task.id),
                TaskActivity.intent(this, task.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ))
            builder.addAction(
                0,
                "取消",
                PendingIntent.getService(
                    this,
                    requestCode(task.id) xor 0x5A5A,
                    Intent(this, SyncTaskForegroundService::class.java)
                        .setAction(ACTION_CANCEL)
                        .putExtra(EXTRA_TASK_ID, task.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        return builder.build()
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "正在运行的导入任务",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "在后台安全读取和保存浏览器数据" }
        )
    }

    companion object {
        private const val CHANNEL_ID = "browser_data_running"
        private const val NOTIFICATION_ID = 2200
        private const val ACTION_CANCEL = "bookmarkhelper.task.CANCEL"
        private const val EXTRA_TASK_ID = "task_id"

        fun start(context: Context?) {
            if (context == null) return
            ContextCompat.startForegroundService(
                context,
                Intent(context, SyncTaskForegroundService::class.java),
            )
        }

        private fun requestCode(taskId: Long) =
            (taskId xor (taskId ushr 32)).toInt() and Int.MAX_VALUE
    }
}

private fun SyncTaskPhase.isForegroundWork() = when (this) {
    SyncTaskPhase.QUEUED, SyncTaskPhase.PREPARING, SyncTaskPhase.RUNNING -> true
    else -> false
}
