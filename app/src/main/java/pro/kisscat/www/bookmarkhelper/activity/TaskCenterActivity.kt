@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pro.kisscat.www.bookmarkhelper.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import pro.kisscat.www.bookmarkhelper.sync.task.SyncTaskCoordinator
import pro.kisscat.www.bookmarkhelper.sync.task.SyncTaskPhase
import pro.kisscat.www.bookmarkhelper.sync.task.SyncTaskState
import pro.kisscat.www.bookmarkhelper.ui.BookmarkHelperTheme
import pro.kisscat.www.bookmarkhelper.ui.AppHapticProvider
import pro.kisscat.www.bookmarkhelper.ui.MiuixBookmarkTheme
import pro.kisscat.www.bookmarkhelper.ui.UiMode
import pro.kisscat.www.bookmarkhelper.ui.UiPreferences
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.MiuixBlurredBar
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.rememberMiuixBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

class TaskCenterActivity : ComponentActivity() {
    private var openedTask = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedTask = savedInstanceState?.getBoolean(STATE_OPENED_TASK) == true
        enableEdgeToEdge()
        setContent {
            BindSystemBack(UiPreferences.predictiveBackEnabled(this))
            val tasks by SyncTaskCoordinator.tasks.collectAsState()
            val open: (Long) -> Unit = {
                openedTask = true
                openSystemPage(TaskActivity.intent(this, it))
            }
            AppHapticProvider(UiPreferences.hapticsEnabled(this)) {
                if (UiPreferences.uiMode(this) == UiMode.MIUIX) {
                    MiuixBookmarkTheme(
                        UiPreferences.themeMode(this),
                        UiPreferences.monetEnabled(this),
                    ) {
                        MiuixTaskCenter(
                            tasks,
                            UiPreferences.blurEnabled(this),
                            ::finishSystemPage,
                            open,
                            SyncTaskCoordinator::cancel,
                        )
                    }
                } else {
                    BookmarkHelperTheme(
                        UiPreferences.themeMode(this),
                        UiPreferences.monetEnabled(this),
                    ) {
                        MaterialTaskCenter(tasks, ::finishSystemPage, open, SyncTaskCoordinator::cancel)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (openedTask) {
            val tasks = SyncTaskCoordinator.tasks.value
            if (tasks.isEmpty() || tasks.all(SyncTaskState::isFinished)) {
                finishSystemPage()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_OPENED_TASK, openedTask)
        super.onSaveInstanceState(outState)
    }

    private companion object {
        const val STATE_OPENED_TASK = "opened_task"
    }
}

@Composable
private fun MaterialTaskCenter(
    tasks: List<SyncTaskState>,
    back: () -> Unit,
    open: (Long) -> Unit,
    cancel: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("任务中心") },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (tasks.isEmpty()) item { Text("当前没有任务", modifier = Modifier.padding(20.dp)) }
            items(tasks, key = { it.id }) { task ->
                Card(Modifier.fillMaxWidth().clickable { open(task.id) }) {
                    ListItem(
                        headlineContent = { Text(task.title) },
                        supportingContent = { Text(task.detail) },
                        leadingContent = { Icon(taskIcon(task), null) },
                        trailingContent = {
                            if (task.isFinished) Text("${task.progress}%")
                            else androidx.compose.material3.TextButton(onClick = { cancel(task.id) }) {
                                Text("取消")
                            }
                        },
                    )
                    LinearProgressIndicator(
                        progress = { task.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiuixTaskCenter(
    tasks: List<SyncTaskState>,
    blurEnabled: Boolean,
    back: () -> Unit,
    open: (Long) -> Unit,
    cancel: (Long) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberMiuixBlurBackdrop(blurEnabled)
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    MiuixScaffold(
        topBar = {
            MiuixBlurredBar(backdrop) {
                MiuixTopAppBar(
                    title = "任务中心",
                    color = barColor,
                    navigationIcon = {
                        MiuixIconButton(onClick = back) {
                            MiuixIcon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            LazyColumn(
                Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection).padding(horizontal = 12.dp),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                overscrollEffect = null,
            ) {
            if (tasks.isEmpty()) item { MiuixText("当前没有任务", modifier = Modifier.padding(20.dp)) }
            items(tasks, key = { it.id }) { task ->
                MiuixCard(
                    onClick = { open(task.id) }, showIndication = true,
                    pressFeedbackType = PressFeedbackType.Sink,
                ) {
                    BasicComponent(
                        title = task.title,
                        summary = "${task.detail} · ${task.progress}%",
                        startAction = { MiuixIcon(taskIcon(task), null) },
                        endActions = {
                            if (!task.isFinished) MiuixTextButton(
                                text = "取消",
                                onClick = { cancel(task.id) },
                            )
                        },
                    )
                }
            }
            }
        }
    }
}

private fun taskIcon(task: SyncTaskState) = when (task.phase) {
    SyncTaskPhase.SUCCESS -> Icons.Default.CheckCircle
    SyncTaskPhase.ERROR -> Icons.Default.Warning
    SyncTaskPhase.CANCELLED -> Icons.Default.Warning
    else -> Icons.Default.Build
}
