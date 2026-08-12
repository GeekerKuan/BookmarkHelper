@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pro.kisscat.www.bookmarkhelper.activity

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pro.kisscat.www.bookmarkhelper.ui.BookmarkHelperTheme
import pro.kisscat.www.bookmarkhelper.ui.AppHapticProvider
import pro.kisscat.www.bookmarkhelper.ui.MiuixBookmarkTheme
import pro.kisscat.www.bookmarkhelper.ui.UiMode
import pro.kisscat.www.bookmarkhelper.ui.UiPreferences
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar

class BridgeExperimentActivity : ComponentActivity() {
    private var message by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BindSystemBack(UiPreferences.predictiveBackEnabled(this))
            val content: @Composable () -> Unit = {
                if (UiPreferences.uiMode(this) == UiMode.MIUIX) {
                    MiuixBridgeExperiment(::finishSystemPage, ::openTestInVia, message)
                } else {
                    MaterialBridgeExperiment(::finishSystemPage, ::openTestInVia, message)
                }
            }
            AppHapticProvider(UiPreferences.hapticsEnabled(this)) {
                if (UiPreferences.uiMode(this) == UiMode.MIUIX) {
                    MiuixBookmarkTheme(
                        UiPreferences.themeMode(this), UiPreferences.monetEnabled(this), content
                    )
                } else {
                    BookmarkHelperTheme(
                        UiPreferences.themeMode(this), UiPreferences.monetEnabled(this), content
                    )
                }
            }
        }
    }

    private fun openTestInVia() {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, TEST_URI)
                    .setPackage(VIA_PACKAGE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: ActivityNotFoundException) {
            message = "未找到 Via，请先安装并正常打开一次。"
        }
    }

    companion object {
        private const val VIA_PACKAGE = "mark.via"
        private val TEST_URI = Uri.parse(
            "https://example.com/?bookmarkhelper_lsposed_test=1"
        )
    }
}

@Composable
private fun MaterialBridgeExperiment(back: () -> Unit, test: () -> Unit, message: String?) {
    Scaffold(topBar = {
        LargeTopAppBar(
            title = { Text("标签云同步实验") },
            navigationIcon = {
                IconButton(onClick = back) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
            },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("只验证一次真实访问") },
                    supportingContent = { Text(EXPLANATION) },
                    leadingContent = { Icon(Icons.Default.Info, null) },
                )
            }
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("使用前") },
                    supportingContent = { Text(STEPS) },
                )
            }
            Button(onClick = test, modifier = Modifier.fillMaxWidth()) {
                Text("在 Via 中打开安全测试页")
            }
            message?.let { Text(it) }
        }
    }
}

@Composable
private fun MiuixBridgeExperiment(back: () -> Unit, test: () -> Unit, message: String?) {
    MiuixScaffold(topBar = {
        MiuixTopAppBar(
            title = "标签云同步实验",
            navigationIcon = {
                MiuixIconButton(onClick = back) {
                    MiuixIcon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
            },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiuixCard {
                BasicComponent(
                    title = "只验证一次真实访问",
                    summary = EXPLANATION,
                    startAction = { MiuixIcon(Icons.Default.Info, null) },
                )
            }
            MiuixCard {
                BasicComponent(title = "使用前", summary = STEPS)
            }
            MiuixTextButton(
                "在 Via 中打开安全测试页",
                test,
                Modifier.fillMaxWidth(),
            )
            message?.let { top.yukonga.miuix.kmp.basic.Text(it) }
        }
    }
}

private const val EXPLANATION =
    "测试页固定为 example.com。Via 完成加载后，模块会把同一页面交给 Edge 正常打开，从而让 Edge 自己生成可参与标签页与历史同步的访问记录。普通网页不会被转发。"
private const val STEPS =
    "在兼容 libxposed API 101 的框架中启用书签助手，作用域勾选 Via 与 Edge，然后结束并重新打开两个浏览器。测试期间 Edge 会切到前台。"
