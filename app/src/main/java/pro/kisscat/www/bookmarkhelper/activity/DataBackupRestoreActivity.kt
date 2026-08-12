package pro.kisscat.www.bookmarkhelper.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import pro.kisscat.www.bookmarkhelper.sync.model.InternalDataBackupService
import pro.kisscat.www.bookmarkhelper.ui.AppHapticProvider
import pro.kisscat.www.bookmarkhelper.ui.MiuixBookmarkTheme
import pro.kisscat.www.bookmarkhelper.ui.UiPreferences
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.MiuixBlurredBar
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.MiuixDialogAdvancedMaterial
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.rememberMiuixBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
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
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

class DataBackupRestoreActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var busy by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)
    private var confirmRestore by mutableStateOf(false)

    private val backupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) runOperation("数据备份已保存") {
            InternalDataBackupService.backup(this, uri)
        }
    }

    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) runOperation("数据管理已从备份恢复") {
            InternalDataBackupService.restore(this, uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BindSystemBack(UiPreferences.predictiveBackEnabled(this))
            AppHapticProvider(UiPreferences.hapticsEnabled(this)) {
                MiuixBookmarkTheme(
                    UiPreferences.themeMode(this),
                    UiPreferences.monetEnabled(this),
                ) {
                    BackupRestorePage(
                        busy = busy,
                        blurEnabled = UiPreferences.blurEnabled(this),
                        back = ::finishSystemPage,
                        backup = ::createBackup,
                        restore = { confirmRestore = true },
                    )
                    message?.let { text ->
                        WindowDialog(show = true, onDismissRequest = { message = null }) {
                            MiuixDialogAdvancedMaterial()
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(text)
                                TextButton(
                                    text = "确定",
                                    onClick = { message = null },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    if (confirmRestore) {
                        WindowDialog(show = true, onDismissRequest = { confirmRestore = false }) {
                            MiuixDialogAdvancedMaterial()
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("恢复备份会替换当前数据管理中的全部收藏、历史记录、标签页和文件夹。浏览器内的数据不会被修改。")
                                TextButton(
                                    text = "取消",
                                    onClick = { confirmRestore = false },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                TextButton(
                                    text = "选择备份并恢复",
                                    onClick = {
                                        confirmRestore = false
                                        restoreLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    private fun createBackup() {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        backupLauncher.launch("BookmarkHelper-data-$timestamp.bhbackup")
    }

    private fun runOperation(success: String, block: () -> Unit) {
        if (busy) return
        busy = true
        worker.execute {
            val result = runCatching(block)
            runOnUiThread {
                busy = false
                message = result.fold(
                    onSuccess = { success },
                    onFailure = { "操作失败（${it.javaClass.simpleName}），当前数据未被有意删除。" },
                )
            }
        }
    }
}

@Composable
private fun BackupRestorePage(
    busy: Boolean,
    blurEnabled: Boolean,
    back: () -> Unit,
    backup: () -> Unit,
    restore: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberMiuixBlurBackdrop(blurEnabled)
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    Scaffold(
        topBar = {
            MiuixBlurredBar(backdrop) {
                TopAppBar(
                    title = "数据备份与恢复",
                    color = barColor,
                    navigationIcon = {
                        IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
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
            LazyColumn(
                Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical()
                    .padding(horizontal = 12.dp),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                overscrollEffect = null,
            ) {
                item {
                    Card {
                        BasicComponent(
                            title = "保存数据管理",
                            summary = "备份应用内的收藏、历史记录、标签页、来源信息与文件夹结构。",
                        )
                    }
                }
                item {
                    Card {
                        ArrowPreference(
                            title = "备份数据",
                            summary = "选择一个位置保存可恢复的数据文件",
                            startAction = { Icon(MiuixIcons.Backup, null) },
                            enabled = !busy,
                            onClick = backup,
                        )
                        ArrowPreference(
                            title = "恢复数据",
                            summary = "从备份文件替换当前数据管理内容",
                            startAction = { Icon(MiuixIcons.Reset, null) },
                            enabled = !busy,
                            onClick = restore,
                        )
                    }
                }
                if (busy) item {
                    Card {
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            InfiniteProgressIndicator()
                            Text("正在校验并处理数据，请勿关闭应用。")
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
