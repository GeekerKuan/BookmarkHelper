@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pro.kisscat.www.bookmarkhelper.activity

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.concurrent.Executors
import pro.kisscat.www.bookmarkhelper.diagnostics.DiagnosticExporter
import pro.kisscat.www.bookmarkhelper.BuildConfig
import pro.kisscat.www.bookmarkhelper.ui.BookmarkHelperTheme
import pro.kisscat.www.bookmarkhelper.ui.AppHapticProvider
import pro.kisscat.www.bookmarkhelper.ui.MiuixBookmarkTheme
import pro.kisscat.www.bookmarkhelper.ui.UiMode
import pro.kisscat.www.bookmarkhelper.ui.UiPreferences

class DeveloperOptionsActivity : ComponentActivity() {
    private var message by mutableStateOf<String?>(null)
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!UiPreferences.developerOptionsEnabled(this)) { finish(); return }
        enableEdgeToEdge()
        setContent {
            BindSystemBack(UiPreferences.predictiveBackEnabled(this))
            val content: @Composable () -> Unit = {
                DeveloperOptionsScreen(::finishSystemPage, ::exportDiagnostics, ::clearDebugData,
                    { openSystemPage(Intent(this, BridgeExperimentActivity::class.java)) }, message)
            }
            AppHapticProvider(UiPreferences.hapticsEnabled(this)) {
                if (UiPreferences.uiMode(this) == UiMode.MIUIX) {
                    MiuixBookmarkTheme(UiPreferences.themeMode(this), UiPreferences.monetEnabled(this), content)
                } else BookmarkHelperTheme(UiPreferences.themeMode(this), UiPreferences.monetEnabled(this), content)
            }
        }
    }

    override fun onDestroy() { worker.shutdown(); super.onDestroy() }

    private fun exportDiagnostics() = worker.execute {
        runCatching { DiagnosticExporter.create(applicationContext) }
            .onSuccess { runOnUiThread { share(it) } }
            .onFailure { runOnUiThread { message = "导出失败：${it.javaClass.simpleName}" } }
    }

    private fun clearDebugData() = worker.execute {
        runCatching { DiagnosticExporter.clearDebugData(applicationContext) }
            .onSuccess { count -> runOnUiThread { message = "已清理 $count 个调试文件" } }
            .onFailure { runOnUiThread { message = "清理失败：${it.javaClass.simpleName}" } }
    }

    private fun share(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("diagnostics", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "发送诊断包"))
    }
}

@Composable
private fun DeveloperOptionsScreen(
    back: () -> Unit,
    export: () -> Unit,
    clear: () -> Unit,
    experiment: () -> Unit,
    message: String?,
) {
    Scaffold(topBar = {
        LargeTopAppBar(
            title = { Text("开发者选项") },
            navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("内部版本") },
                    supportingContent = {
                        Text("${BuildConfig.INTERNAL_VERSION} · versionCode ${BuildConfig.VERSION_CODE}")
                    },
                    leadingContent = { Icon(Icons.Default.Build, null) },
                )
            }
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("发送诊断日志") },
                    supportingContent = { Text("仅导出脱敏后的应用日志和环境摘要") },
                    leadingContent = { Icon(Icons.Default.Share, null) },
                )
                Button(export, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("创建并分享诊断包") }
                ListItem(
                    headlineContent = { Text("清理调试数据") },
                    supportingContent = { Text("删除应用日志和已导出的诊断包") },
                    leadingContent = { Icon(Icons.Default.Delete, null) },
                )
                Button(clear, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("立即清理") }
            }
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("浏览器桥接实验") },
                    supportingContent = { Text("用于验证 Via 与 Edge 的 LSPosed 桥接行为") },
                    leadingContent = { Icon(Icons.Default.Build, null) },
                )
                Button(experiment, Modifier.fillMaxWidth().padding(16.dp)) { Text("打开实验") }
            }
            message?.let { Text(it) }
        }
    }
}
