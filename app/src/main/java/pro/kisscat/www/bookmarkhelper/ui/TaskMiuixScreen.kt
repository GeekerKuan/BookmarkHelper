package pro.kisscat.www.bookmarkhelper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import pro.kisscat.www.bookmarkhelper.sync.task.HistoryRange
import pro.kisscat.www.bookmarkhelper.sync.task.SyncTaskKind
import pro.kisscat.www.bookmarkhelper.sync.task.SyncTaskPhase
import pro.kisscat.www.bookmarkhelper.sync.task.SyncTaskState
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.MiuixBlurredBar
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.MiuixDatePickerBottomSheet
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.localDayStartMillis
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.rememberMiuixBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun TaskMiuixScreen(
    state: SyncTaskState,
    blurEnabled: Boolean,
    onHide: () -> Unit,
    onPreviewRange: (HistoryRange) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    onOpenEdge: () -> Unit,
    onOpenVia: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberMiuixBlurBackdrop(blurEnabled)
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    Scaffold(
        topBar = {
            MiuixBlurredBar(backdrop) {
                TopAppBar(
                    title = state.title.ifBlank { "正在运行的任务" },
                    color = barColor,
                    navigationIcon = {
                        IconButton(onClick = onHide) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回主页")
                        }
                    },
                    actions = {
                        IconButton(onClick = onHide) {
                            Icon(Icons.Default.KeyboardArrowDown, "后台等待")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            when (state.phase) {
                SyncTaskPhase.RANGE -> MiuixRangePage(
                    Modifier, padding, onPreviewRange
                )
                SyncTaskPhase.QUEUED, SyncTaskPhase.PREPARING, SyncTaskPhase.RUNNING -> MiuixWorkingPage(
                    state, onCancel, Modifier.padding(padding)
                )
                SyncTaskPhase.PREVIEW -> MiuixPreviewPage(
                    state, onConfirm, onCancel, Modifier, padding
                )
                SyncTaskPhase.SUCCESS, SyncTaskPhase.ERROR, SyncTaskPhase.CANCELLED -> MiuixResultPage(
                    state, onDone, onOpenEdge, onOpenVia, Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun MiuixRangePage(
    modifier: Modifier,
    contentPadding: PaddingValues,
    onPreview: (HistoryRange) -> Unit,
) {
    val now = System.currentTimeMillis()
    var start by remember { mutableLongStateOf(localDayStartMillis(now - 7L * 86_400_000L)) }
    var end by remember { mutableLongStateOf(localDayStartMillis(now)) }
    var pickerTarget by remember { mutableStateOf<MiuixDatePickerTarget?>(null) }
    val formatter = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    LazyColumn(
        modifier.fillMaxSize().scrollEndHaptic().overScrollVertical().padding(horizontal = 12.dp),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        overscrollEffect = null,
    ) {
        item {
            Card {
                Text(
                    "选择导入范围",
                    modifier = Modifier.padding(20.dp, 18.dp, 20.dp, 8.dp),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                listOf(1 to "最近 1 天", 7 to "最近 7 天", 30 to "最近 30 天").forEach { (days, label) ->
                    BasicComponent(
                        title = label,
                        summary = "只读取并保存这个时间范围",
                        onClick = {
                            val seconds = System.currentTimeMillis() / 1000L
                            onPreview(HistoryRange(seconds - days * 86_400L, seconds, label))
                        },
                    )
                }
                BasicComponent(
                    title = "全部历史",
                    summary = "读取浏览器当前保留的全部历史记录",
                    onClick = { onPreview(HistoryRange(0L, Long.MAX_VALUE, "全部历史")) },
                )
            }
        }
        item {
            Card {
                BasicComponent(
                    title = "开始日期",
                    summary = formatter.format(Date(start)),
                    onClick = { pickerTarget = MiuixDatePickerTarget.START },
                )
                BasicComponent(
                    title = "结束日期",
                    summary = formatter.format(Date(end)),
                    onClick = { pickerTarget = MiuixDatePickerTarget.END },
                )
                TextButton(
                    text = "预览自定义范围",
                    onClick = {
                        if (end < start) return@TextButton
                        onPreview(HistoryRange(
                            start / 1000L,
                            end / 1000L + 86_399L,
                            "${formatter.format(Date(start))} 至 ${formatter.format(Date(end))}",
                        ))
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    pickerTarget?.let { target ->
        MiuixDatePickerBottomSheet(
            show = true,
            title = if (target == MiuixDatePickerTarget.START) "选择开始日期" else "选择结束日期",
            initialDateMillis = if (target == MiuixDatePickerTarget.START) start else end,
            minimumDateMillis = if (target == MiuixDatePickerTarget.END) start else null,
            maximumDateMillis = if (target == MiuixDatePickerTarget.START) end else now,
            onDismissRequest = { pickerTarget = null },
            onDateSelected = { selected ->
                if (target == MiuixDatePickerTarget.START) start = selected.coerceAtMost(end)
                else end = selected.coerceAtLeast(start)
            },
        )
    }
}

private enum class MiuixDatePickerTarget { START, END }

@Composable
private fun MiuixWorkingPage(state: SyncTaskState, onCancel: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InfiniteProgressIndicator()
        Spacer(Modifier.height(18.dp))
        Text(state.detail, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text("${state.progress}%")
        Spacer(Modifier.height(8.dp))
        Text("可点击右上角隐藏页面，任务会继续运行。")
        Spacer(Modifier.height(12.dp))
        TextButton(text = "取消任务", onClick = onCancel)
    }
}

@Composable
private fun MiuixPreviewPage(
    state: SyncTaskState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier.fillMaxSize().scrollEndHaptic().overScrollVertical().padding(horizontal = 12.dp),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        overscrollEffect = null,
    ) {
        item {
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MiuixCount("读取", state.sourceCount)
                    MiuixCount("去重后", state.newCount)
                    MiuixCount("重复", state.duplicateCount)
                }
                Text(state.detail, modifier = Modifier.padding(20.dp, 0.dp, 20.dp, 20.dp))
            }
        }
        item {
            TextButton(
                text = "导入到数据管理",
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
            Spacer(Modifier.height(10.dp))
            TextButton(
                text = "取消任务",
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.kind == SyncTaskKind.BOOKMARKS) {
            items(state.bookmarks) { bookmark ->
                Card {
                    BasicComponent(
                        title = bookmark.title.ifBlank { bookmark.url },
                        summary = buildString {
                            append(bookmark.url)
                            if (bookmark.folder.isNotBlank()) append(" · ").append(bookmark.folder)
                            if (bookmark.duplicate) append(" · 多个来源中重复")
                        },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MiuixCount(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(label)
    }
}

@Composable
private fun MiuixResultPage(
    state: SyncTaskState,
    onDone: () -> Unit,
    onOpenEdge: () -> Unit,
    onOpenVia: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (state.phase == SyncTaskPhase.SUCCESS) Icons.Default.CheckCircle else Icons.Default.Warning,
            null,
            tint = if (state.phase == SyncTaskPhase.SUCCESS) MiuixTheme.colorScheme.primary
            else Color(0xFFFF5B5B),
        )
        Spacer(Modifier.height(16.dp))
        Text(state.detail, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(24.dp))
        TextButton(text = "返回主页", onClick = onDone)
    }
}
