@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pro.kisscat.www.bookmarkhelper.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import pro.kisscat.www.bookmarkhelper.sync.task.HistoryRange
import pro.kisscat.www.bookmarkhelper.sync.task.SyncTaskCoordinator
import pro.kisscat.www.bookmarkhelper.sync.task.SyncTaskKind
import pro.kisscat.www.bookmarkhelper.sync.task.SyncTaskPhase
import pro.kisscat.www.bookmarkhelper.sync.task.SyncTaskState
import pro.kisscat.www.bookmarkhelper.ui.BookmarkHelperTheme
import pro.kisscat.www.bookmarkhelper.ui.AppHapticProvider
import pro.kisscat.www.bookmarkhelper.ui.MiuixBookmarkTheme
import pro.kisscat.www.bookmarkhelper.ui.TaskMiuixScreen
import pro.kisscat.www.bookmarkhelper.ui.UiMode
import pro.kisscat.www.bookmarkhelper.ui.UiPreferences
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.MiuixDatePickerBottomSheet
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.localDayStartMillis

class TaskActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_TASK_ID = "task_id"
        fun intent(context: Context, id: Long) =
            Intent(context, TaskActivity::class.java).putExtra(EXTRA_TASK_ID, id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        setContent {
            BindSystemBack(UiPreferences.predictiveBackEnabled(this))
            val tasks by SyncTaskCoordinator.tasks.collectAsState()
            val state = tasks.firstOrNull { it.id == taskId }
            if (state == null) {
                LaunchedEffect(taskId) { finish() }
                return@setContent
            }
            LaunchedEffect(state.phase, state.cancellationRequested) {
                if (state.cancellationRequested && state.phase == SyncTaskPhase.CANCELLED) {
                    SyncTaskCoordinator.remove(taskId)
                    setResult(RESULT_CANCELED)
                    finishSystemPage()
                }
            }
            val taskContent = @Composable {
                if (UiPreferences.uiMode(this) == UiMode.MIUIX) {
                    TaskMiuixScreen(
                        state = state,
                        blurEnabled = UiPreferences.blurEnabled(this),
                        onHide = ::hideTaskPage,
                        onPreviewRange = {
                            SyncTaskCoordinator.enqueueHistory(taskId, it)
                            finish()
                        },
                        onConfirm = { SyncTaskCoordinator.confirmImport(taskId) },
                        onCancel = { SyncTaskCoordinator.cancel(taskId) },
                        onDone = {
                            SyncTaskCoordinator.remove(taskId)
                            finish()
                        },
                        onOpenEdge = { launchPackage("com.microsoft.emmx") },
                        onOpenVia = { launchPackage("mark.via") },
                    )
                } else TaskScreen(
                    state = state,
                    onHide = ::hideTaskPage,
                    onPreviewRange = {
                        SyncTaskCoordinator.enqueueHistory(taskId, it)
                        finish()
                    },
                    onConfirm = { SyncTaskCoordinator.confirmImport(taskId) },
                    onCancel = { SyncTaskCoordinator.cancel(taskId) },
                    onDone = {
                        SyncTaskCoordinator.remove(taskId)
                        finish()
                    },
                    onOpenEdge = { launchPackage("com.microsoft.emmx") },
                    onOpenVia = { launchPackage("mark.via") },
                )
            }
            AppHapticProvider(UiPreferences.hapticsEnabled(this)) {
                if (UiPreferences.uiMode(this) == UiMode.MIUIX) {
                    MiuixBookmarkTheme(
                        UiPreferences.themeMode(this),
                        UiPreferences.monetEnabled(this),
                        content = taskContent,
                    )
                } else {
                    BookmarkHelperTheme(
                        UiPreferences.themeMode(this),
                        UiPreferences.monetEnabled(this),
                        content = taskContent,
                    )
                }
            }
        }
    }

    private fun hideTaskPage() {
        finish()
    }

    private fun launchPackage(packageName: String) {
        packageManager.getLaunchIntentForPackage(packageName)?.let(::startActivity)
    }

}

@Composable
private fun TaskScreen(
    state: SyncTaskState,
    onHide: () -> Unit,
    onPreviewRange: (HistoryRange) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    onOpenEdge: () -> Unit,
    onOpenVia: () -> Unit,
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(state.title.ifBlank { "正在运行的任务" }) },
                navigationIcon = {
                    IconButton(onClick = onHide) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回主页")
                    }
                },
                actions = {
                    TextButton(onClick = onHide) {
                        Icon(Icons.Default.KeyboardArrowDown, null)
                        Text("后台等待")
                    }
                },
            )
        },
    ) { padding ->
        when (state.phase) {
            SyncTaskPhase.RANGE -> RangeScreen(
                modifier = Modifier.padding(padding),
                onPreview = onPreviewRange,
            )
            SyncTaskPhase.QUEUED, SyncTaskPhase.PREPARING, SyncTaskPhase.RUNNING ->
                WorkingScreen(state, onCancel, Modifier.padding(padding))
            SyncTaskPhase.PREVIEW -> PreviewScreen(state, onConfirm, onCancel, Modifier.padding(padding))
            SyncTaskPhase.SUCCESS, SyncTaskPhase.ERROR, SyncTaskPhase.CANCELLED -> ResultScreen(
                state, onDone, Modifier.padding(padding), onOpenEdge, onOpenVia
            )
        }
    }
}

@Composable
private fun RangeScreen(
    modifier: Modifier,
    onPreview: (HistoryRange) -> Unit,
) {
    val now = System.currentTimeMillis()
    var start by remember { mutableLongStateOf(localDayStartMillis(now - 7L * 86_400_000L)) }
    var end by remember { mutableLongStateOf(localDayStartMillis(now)) }
    var pickerTarget by remember { mutableStateOf<LegacyDatePickerTarget?>(null) }
    val formatter = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("导入范围", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("只读取所选时间范围，记录会按原访问日期保存到数据管理。")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "1 天", 7 to "7 天", 30 to "30 天").forEach { (days, label) ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            val seconds = System.currentTimeMillis() / 1000L
                            onPreview(HistoryRange(seconds - days * 86_400L, seconds, "最近 $label"))
                        },
                        label = { Text(label) },
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { onPreview(HistoryRange(0L, Long.MAX_VALUE, "全部历史")) },
                    label = { Text("全部") },
                )
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("自定义日期", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { pickerTarget = LegacyDatePickerTarget.START }) {
                        Text("开始：${formatter.format(Date(start))}")
                    }
                    TextButton(onClick = { pickerTarget = LegacyDatePickerTarget.END }) {
                        Text("结束：${formatter.format(Date(end))}")
                    }
                    Button(
                        enabled = end >= start,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val toInclusive = end / 1000L + 86_399L
                            onPreview(HistoryRange(
                                start / 1000L,
                                toInclusive,
                                "${formatter.format(Date(start))} 至 ${formatter.format(Date(end))}",
                            ))
                        },
                    ) { Text("预览这个范围") }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
    pickerTarget?.let { target ->
        MiuixDatePickerBottomSheet(
            show = true,
            title = if (target == LegacyDatePickerTarget.START) "选择开始日期" else "选择结束日期",
            initialDateMillis = if (target == LegacyDatePickerTarget.START) start else end,
            minimumDateMillis = if (target == LegacyDatePickerTarget.END) start else null,
            maximumDateMillis = if (target == LegacyDatePickerTarget.START) end else now,
            onDismissRequest = { pickerTarget = null },
            onDateSelected = { selected ->
                if (target == LegacyDatePickerTarget.START) start = selected.coerceAtMost(end)
                else end = selected.coerceAtLeast(start)
            },
        )
    }
}

private enum class LegacyDatePickerTarget { START, END }

@Composable
private fun WorkingScreen(state: SyncTaskState, onCancel: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(20.dp))
        Text(state.detail, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.LinearProgressIndicator(
            progress = { state.progress / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text("可以点击右上角“后台等待”，稍后从主页重新打开。")
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onCancel) { Text("取消任务") }
    }
}

@Composable
private fun PreviewScreen(
    state: SyncTaskState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SummaryCard(state)
            Spacer(Modifier.height(8.dp))
            Text(state.detail)
        }
        item {
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text("导入到数据管理")
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("取消任务") }
        }
        if (state.kind == SyncTaskKind.BOOKMARKS) {
            items(state.bookmarks) { bookmark ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            bookmark.title.ifBlank { bookmark.url },
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(bookmark.url, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (bookmark.folder.isNotBlank()) Text("文件夹：${bookmark.folder}")
                        if (bookmark.duplicate) Text("多个来源中存在相同收藏", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun SummaryCard(state: SyncTaskState) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SummaryNumber("读取", state.sourceCount)
            SummaryNumber("去重后", state.newCount)
            SummaryNumber("重复", state.duplicateCount)
        }
    }
}

@Composable
private fun SummaryNumber(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label)
    }
}

@Composable
private fun ResultScreen(
    state: SyncTaskState,
    onDone: () -> Unit,
    modifier: Modifier,
    onOpenEdge: () -> Unit = {},
    onOpenVia: () -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (state.phase == SyncTaskPhase.SUCCESS) Icons.Default.CheckCircle else Icons.Default.Warning,
            null,
            tint = if (state.phase == SyncTaskPhase.SUCCESS) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
        Text(state.detail, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone) { Text("返回主页") }
    }
}
