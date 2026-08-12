package pro.kisscat.www.bookmarkhelper.activity

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.Executors
import pro.kisscat.www.bookmarkhelper.BuildConfig
import pro.kisscat.www.bookmarkhelper.diagnostics.DiagnosticExporter
import pro.kisscat.www.bookmarkhelper.ui.AppHapticProvider
import pro.kisscat.www.bookmarkhelper.ui.MiuixBookmarkTheme
import pro.kisscat.www.bookmarkhelper.ui.UiPreferences
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.MiuixBlurredBar
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.rememberMiuixBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

class DeveloperOptionsActivity : ComponentActivity() {
    private var message by mutableStateOf<String?>(null)
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BindSystemBack(UiPreferences.predictiveBackEnabled(this))
            val systemDensity = LocalDensity.current
            val scaledDensity = Density(
                systemDensity.density * UiPreferences.pageScale(this),
                systemDensity.fontScale,
            )
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                AppHapticProvider(UiPreferences.hapticsEnabled(this)) {
                    MiuixBookmarkTheme(
                        UiPreferences.themeMode(this),
                        UiPreferences.monetEnabled(this),
                    ) {
                        MiuixDeveloperOptions(
                            blurEnabled = UiPreferences.blurEnabled(this),
                            back = ::finishSystemPage,
                            export = ::exportDiagnostics,
                            clear = ::clearDebugData,
                            experiment = {
                                openSystemPage(Intent(this, BridgeExperimentActivity::class.java))
                            },
                            message = message,
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    private fun exportDiagnostics() = worker.execute {
        runCatching { DiagnosticExporter.create(applicationContext) }
            .onSuccess { runOnUiThread { share(it) } }
            .onFailure { runOnUiThread { message = "诊断文件创建失败，请稍后重试。" } }
    }

    private fun clearDebugData() = worker.execute {
        runCatching { DiagnosticExporter.clearDebugData(applicationContext) }
            .onSuccess { count -> runOnUiThread { message = "已清理 $count 个调试文件。" } }
            .onFailure { runOnUiThread { message = "清理失败，请稍后重试。" } }
    }

    private fun share(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("diagnostics", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "发送诊断文件"))
    }
}

@Composable
private fun MiuixDeveloperOptions(
    blurEnabled: Boolean,
    back: () -> Unit,
    export: () -> Unit,
    clear: () -> Unit,
    experiment: () -> Unit,
    message: String?,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberMiuixBlurBackdrop(blurEnabled)
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    Scaffold(topBar = {
        MiuixBlurredBar(backdrop) {
            TopAppBar(
                title = "开发者选项",
                color = barColor,
                navigationIcon = {
                    IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    }) { padding ->
        Box(
            Modifier.fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
        ) {
            LazyColumn(
                Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = padding.calculateTopPadding()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                overscrollEffect = null,
            ) {
                item {
                    Card {
                        BasicComponent(
                            title = "内部版本",
                            summary = "${BuildConfig.INTERNAL_VERSION} · versionCode ${BuildConfig.VERSION_CODE}",
                            startAction = { Icon(Icons.Default.Build, null) },
                        )
                    }
                }
                item {
                    Card {
                        BasicComponent(
                            title = "诊断文件",
                            summary = "隐藏私人信息后，整理应用日志和运行环境，方便反馈问题。",
                            startAction = { Icon(Icons.Default.Share, null) },
                        )
                        TextButton(
                            text = "创建并分享",
                            onClick = export,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                        BasicComponent(
                            title = "清理调试数据",
                            summary = "删除应用日志和以前生成的诊断文件。",
                            startAction = { Icon(Icons.Default.Delete, null) },
                        )
                        TextButton(
                            text = "立即清理",
                            onClick = clear,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                }
                item {
                    Card {
                        ArrowPreference(
                            title = "浏览器桥接实验",
                            summary = "验证 Via 与 Edge 的 LSPosed 桥接行为",
                            startAction = { Icon(Icons.Default.Build, null) },
                            onClick = experiment,
                        )
                    }
                }
                message?.let { current ->
                    item { Card { BasicComponent(title = current) } }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}
