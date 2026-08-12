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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import pro.kisscat.www.bookmarkhelper.BuildConfig
import pro.kisscat.www.bookmarkhelper.R
import pro.kisscat.www.bookmarkhelper.ui.AppHapticProvider
import pro.kisscat.www.bookmarkhelper.ui.BookmarkHelperTheme
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
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

class AppInfoActivity : ComponentActivity() {
    private var versionTaps by mutableIntStateOf(0)
    private var developerVisible by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        developerVisible = UiPreferences.developerOptionsEnabled(this)
        setContent {
            BindSystemBack(UiPreferences.predictiveBackEnabled(this))
            val uiMode = UiPreferences.uiMode(this)
            val systemDensity = LocalDensity.current
            val scaledDensity = Density(
                systemDensity.density * UiPreferences.pageScale(this),
                systemDensity.fontScale,
            )
            val content: @Composable () -> Unit = {
                if (uiMode == UiMode.MIUIX) {
                    MiuixAppInfo(
                        developerVisible,
                        UiPreferences.blurEnabled(this),
                        ::finishSystemPage,
                        ::tapVersion,
                        ::openChangelog,
                        ::openDeveloperOptions,
                        ::openProject,
                        message,
                        { message = null },
                    )
                } else {
                    MaterialAppInfo(
                        developerVisible,
                        ::finishSystemPage,
                        ::tapVersion,
                        ::openChangelog,
                        ::openDeveloperOptions,
                        ::openProject,
                    )
                }
            }
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                AppHapticProvider(UiPreferences.hapticsEnabled(this)) {
                    if (uiMode == UiMode.MIUIX) {
                        MiuixBookmarkTheme(
                            UiPreferences.themeMode(this),
                            UiPreferences.monetEnabled(this),
                            content,
                        )
                    } else {
                        BookmarkHelperTheme(
                            UiPreferences.themeMode(this),
                            UiPreferences.monetEnabled(this),
                            content,
                        )
                    }
                }
            }
        }
    }

    private fun tapVersion() {
        if (developerVisible) {
            message = "开发者选项已经开启。"
            return
        }
        versionTaps++
        val remaining = 7 - versionTaps
        if (remaining <= 0) {
            UiPreferences.setDeveloperOptionsEnabled(this, true)
            developerVisible = true
            message = "开发者选项已开启。"
        } else if (remaining <= 3) {
            message = "再点击 $remaining 次即可开启开发者选项。"
        }
    }

    private fun openChangelog() = openSystemPage(Intent(this, ChangelogActivity::class.java))

    private fun openDeveloperOptions() =
        openSystemPage(Intent(this, DeveloperOptionsActivity::class.java))

    private fun openProject() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.aboutMeURL))))
        } catch (_: ActivityNotFoundException) {
            message = "没有浏览器可以打开项目页面。"
        }
    }
}

@Composable
private fun MiuixAppInfo(
    developerVisible: Boolean,
    blurEnabled: Boolean,
    back: () -> Unit,
    tapVersion: () -> Unit,
    changelog: () -> Unit,
    developer: () -> Unit,
    project: () -> Unit,
    message: String?,
    dismissMessage: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberMiuixBlurBackdrop(blurEnabled)
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    MiuixScaffold(topBar = {
        MiuixBlurredBar(backdrop) {
            MiuixTopAppBar(
                title = "应用信息",
                color = barColor,
                navigationIcon = {
                    MiuixIconButton(back) {
                        MiuixIcon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            LazyColumn(
                Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection).padding(horizontal = 12.dp),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                overscrollEffect = null,
            ) {
                item {
                    MiuixCard(
                        onClick = tapVersion,
                        pressFeedbackType = PressFeedbackType.Sink,
                        showIndication = true,
                    ) {
                        BasicComponent(
                            title = "书签助手",
                            summary = "${BuildConfig.VERSION_NAME}\nAndroid 12 及以上 · Root 测试版",
                            startAction = { MiuixIcon(Icons.Default.Info, null) },
                        )
                    }
                }
                item {
                    MiuixCard {
                        ArrowPreference(
                            title = "版本更新日志",
                            summary = "从原项目 0.0.1 到当前测试版",
                            startAction = { MiuixIcon(Icons.Default.Refresh, null) },
                            onClick = changelog,
                        )
                        if (developerVisible) {
                            ArrowPreference(
                                title = "开发者选项",
                                summary = "诊断日志、实验功能与调试数据",
                                startAction = { MiuixIcon(Icons.Default.Build, null) },
                                onClick = developer,
                            )
                        }
                    }
                }
                item {
                    MiuixCard {
                        ArrowPreference(
                            title = "项目主页",
                            summary = "viceyy/BookmarkHelper",
                            startAction = { MiuixIcon(Icons.Default.Settings, null) },
                            onClick = project,
                        )
                        BasicComponent(
                            title = "开源许可",
                            summary = "原 BookmarkHelper：Apache-2.0\n" +
                                "当前组合分支及 SukiSU/UI Kit 衍生界面：GPL-3.0\n" +
                                "Miuix 与液态玻璃相关组件：Apache-2.0\n" +
                                "发布组合版本时须提供对应源码并遵守 GPL-3.0。",
                        )
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
    message?.let {
        WindowDialog(show = true, onDismissRequest = dismissMessage) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                top.yukonga.miuix.kmp.basic.Text(it)
                MiuixTextButton("知道了", dismissMessage, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MaterialAppInfo(
    developerVisible: Boolean,
    back: () -> Unit,
    tapVersion: () -> Unit,
    changelog: () -> Unit,
    developer: () -> Unit,
    project: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("应用信息") },
                navigationIcon = {
                    IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Card(onClick = tapVersion, modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("书签助手") },
                    supportingContent = { Text(BuildConfig.VERSION_NAME) },
                    leadingContent = { Icon(Icons.Default.Info, null) },
                )
            } }
            item { Card(Modifier.fillMaxWidth()) {
                InfoRow("版本更新日志", "从原项目 0.0.1 到当前测试版", changelog)
                if (developerVisible) InfoRow("开发者选项", "诊断、实验与调试数据", developer)
                InfoRow("项目主页", "viceyy/BookmarkHelper", project)
            } }
            item { Card(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("开源许可") },
                    supportingContent = {
                        Text("原项目 Apache-2.0；当前组合分支须遵守 GPL-3.0；Miuix 组件 Apache-2.0。")
                    },
                )
            } }
        }
    }
}

@Composable
private fun InfoRow(title: String, summary: String, click: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        modifier = Modifier.fillMaxWidth(),
        trailingContent = { androidx.compose.material3.TextButton(click) { Text("打开") } },
    )
}
