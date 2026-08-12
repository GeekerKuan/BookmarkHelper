/* Layout patterns adapted from SukiSU Ultra and KernelSU Style UI Kit, GPL-3.0. */
package pro.kisscat.www.bookmarkhelper.ui

import android.os.Build
import pro.kisscat.www.bookmarkhelper.BuildConfig
import pro.kisscat.www.bookmarkhelper.sync.environment.ActivationState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import pro.kisscat.www.bookmarkhelper.ui.component.FloatingBottomBar
import pro.kisscat.www.bookmarkhelper.ui.component.FloatingBottomBarItem
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.MiuixBlurredBar
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.MiuixDialogAdvancedMaterial
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.rememberMiuixBlurBackdrop
import pro.kisscat.www.bookmarkhelper.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private data class MiuixDestination(val page: MainPage, val label: String, val icon: ImageVector)

@Composable
fun BookmarkHelperMiuixScreen(state: BookmarkHelperUiState, actions: BookmarkHelperActions) {
    val scrollBehavior = MiuixScrollBehavior()
    val surface = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }
    val blurBackdrop = rememberMiuixBlurBackdrop(state.blurEnabled)
    val contentBackdrop = blurBackdrop ?: backdrop
    val barColor = if (blurBackdrop != null) Color.Transparent else surface
    val destinations = listOf(
        MiuixDestination(MainPage.HOME, "主页", Icons.Default.Home),
        MiuixDestination(MainPage.TRANSFER, "迁移", Icons.AutoMirrored.Rounded.CompareArrows),
        MiuixDestination(MainPage.DATA, "数据", MiuixIcons.Backup),
        MiuixDestination(MainPage.SETTINGS, "设置", Icons.Default.Settings),
    )
    // SukiSU Ultra MainScreen: pages live in a HorizontalPager and the bottom bar
    // drives pager animation instead of replacing composables in-place.
    val pagerState = rememberPagerState(
        initialPage = state.selectedPage.index,
        pageCount = { MainPage.entries.size },
    )
    var programmaticTarget by remember { mutableStateOf<Int?>(null) }
    val currentSelectedPage by rememberUpdatedState(state.selectedPage)
    val currentActions by rememberUpdatedState(actions)
    LaunchedEffect(state.selectedPage) {
        val target = state.selectedPage.index
        if (pagerState.currentPage != target) {
            programmaticTarget = target
            try {
                if (state.transitionsEnabled) pagerState.animateScrollToPage(target)
                else pagerState.scrollToPage(target)
            } finally {
                if (programmaticTarget == target && pagerState.currentPage == target) {
                    programmaticTarget = null
                }
            }
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { index ->
            val page = MainPage.fromIndex(index)
            if (programmaticTarget == null && page != currentSelectedPage) {
                currentActions.selectPage(page)
            }
        }
    }
    Scaffold(
        topBar = {
            MiuixBlurredBar(blurBackdrop) {
                TopAppBar(
                    title = miuixPageTitle(state.selectedPage),
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        bottomBar = {
            if (state.floatingBarEnabled) Box(Modifier.fillMaxWidth()) {
                FloatingBottomBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            bottom = 10.dp + WindowInsets.navigationBars
                                .asPaddingValues().calculateBottomPadding()
                        ),
                    selectedIndex = state.selectedPage.index,
                    onSelected = { actions.selectPage(MainPage.fromIndex(it)) },
                    backdrop = contentBackdrop,
                    tabsCount = destinations.size,
                    isBlurEnabled = state.glassEnabled && blurBackdrop != null && Build.VERSION.SDK_INT >= 33,
                ) {
                    destinations.forEach { destination ->
                        val selected = state.selectedPage == destination.page
                        FloatingBottomBarItem(
                            onClick = { actions.selectPage(destination.page) },
                            modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                        ) {
                            Icon(
                                destination.icon,
                                null,
                                tint = if (selected) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                destination.label,
                                fontSize = 11.sp,
                                color = if (selected) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            } else {
                MiuixBlurredBar(blurBackdrop) {
                    Row(
                        Modifier.fillMaxWidth()
                            .background(
                                if (blurBackdrop != null) Color.Transparent
                                else MiuixTheme.colorScheme.surfaceContainer,
                            )
                            .padding(
                                top = 8.dp,
                                bottom = 8.dp + WindowInsets.navigationBars.asPaddingValues()
                                    .calculateBottomPadding(),
                            ),
                    ) {
                        destinations.forEach { destination ->
                            val selected = state.selectedPage == destination.page
                            Column(
                                Modifier.weight(1f).clickable {
                                    actions.selectPage(destination.page)
                                }.padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    destination.icon,
                                    null,
                                    tint = if (selected) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.onSurface,
                                )
                                Text(
                                    destination.label,
                                    fontSize = 11.sp,
                                    color = if (selected) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        // Intentionally do not apply Scaffold's bottom padding: real page pixels extend
        // behind the glass bar; each list owns a 132dp scroll-safe footer instead.
        Box(
            Modifier.fillMaxSize()
                .background(surface)
                .layerBackdrop(contentBackdrop)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                MiuixPage(MainPage.fromIndex(index), state, actions, padding.calculateTopPadding())
            }
        }
    }
    MiuixDialogs(state, actions)
}

@Composable
private fun MiuixPage(
    page: MainPage,
    state: BookmarkHelperUiState,
    actions: BookmarkHelperActions,
    topPadding: Dp,
) {
    when (page) {
        MainPage.HOME -> MiuixHome(state, actions, topPadding)
        MainPage.TRANSFER -> MiuixTransfer(state, actions, topPadding)
        MainPage.DATA -> DataManagementMiuix(
            state.intermediateData,
            actions,
            topContentPadding = topPadding,
        )
        MainPage.SETTINGS -> MiuixSettings(state, actions, topPadding)
    }
}

@Composable
private fun MiuixHome(state: BookmarkHelperUiState, actions: BookmarkHelperActions, topPadding: Dp) {
    LazyColumn(
        Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = topPadding),
        overscrollEffect = null,
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixEnvironmentCard(
                    "Root", state.rootState, Modifier.weight(1f)
                )
                MiuixEnvironmentCard(
                    "LSPosed", state.lsposedState, Modifier.weight(1f)
                )
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                showIndication = true,
                pressFeedbackType = PressFeedbackType.Sink,
                onClick = { /* Information-only card. */ },
            ) {
                BasicComponent(
                    title = "应用版本",
                    summary = BuildConfig.VERSION_NAME,
                )
                BasicComponent(
                    title = "Root 管理器",
                    summary = state.rootManager,
                )
                BasicComponent(
                    title = "Via",
                    summary = if (state.viaInstalled) state.viaVersion.ifBlank { "已安装" } else "未安装",
                )
                BasicComponent(
                    title = "Microsoft Edge",
                    summary = if (state.edgeInstalled) state.edgeVersion.ifBlank { "已安装" } else "未安装",
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixHomeNavigationCard(
                    title = "数据迁移",
                    summary = "在数据管理与浏览器之间传输",
                    icon = Icons.AutoMirrored.Rounded.CompareArrows,
                    modifier = Modifier.weight(1f),
                    onClick = { actions.selectPage(MainPage.TRANSFER) },
                )
                MiuixHomeNavigationCard(
                    title = "数据管理",
                    summary = "整理收藏、历史与标签页",
                    icon = MiuixIcons.Backup,
                    modifier = Modifier.weight(1f),
                    onClick = { actions.selectPage(MainPage.DATA) },
                )
            }
        }
        item { Spacer(Modifier.height(132.dp)) }
    }
}

@Composable
private fun MiuixEnvironmentCard(
    name: String,
    status: ActivationState,
    modifier: Modifier,
) {
    val active = status == ActivationState.ACTIVE
    val accent = if (active) Color(0xFF36D167) else MiuixTheme.colorScheme.onSurfaceVariantSummary
    val background = when {
        !active -> MiuixTheme.colorScheme.surfaceContainer
        isInDarkTheme() -> Color(0xFF1A3825)
        else -> Color(0xFFDFFAE4)
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.defaultColors(color = background),
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = { /* Status-only card: preserve press depth without re-running probes. */ },
    ) {
        Box(Modifier.fillMaxWidth().height(152.dp)) {
            Icon(
                imageVector = if (active) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = accent.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.BottomEnd).offset(24.dp, 28.dp).size(112.dp),
            )
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(status.environmentLabel(name), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun MiuixBrowserCard(name: String, installed: Boolean, version: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(if (installed) version.ifBlank { "已安装" } else "未安装")
        }
    }
}

@Composable
private fun MiuixHomeNavigationCard(
    title: String,
    summary: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = MiuixTheme.colorScheme.primary)
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun MiuixTransfer(state: BookmarkHelperUiState, actions: BookmarkHelperActions, topPadding: Dp) {
    LazyColumn(
        Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = topPadding),
        overscrollEffect = null,
    ) {
        item {
            Text(
                "在数据管理与浏览器之间安全传输收藏、历史记录和标签页。",
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        item {
            AnimatedVisibility(
                visible = state.unfinishedTaskCount > 0,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    showIndication = true,
                    pressFeedbackType = PressFeedbackType.Sink,
                    onClick = actions.openRunningTask,
                ) {
                    BasicComponent(
                        title = if (state.pendingConfirmationCount > 0) "有任务等待确认" else "正在运行的任务",
                        summary = state.taskSummary ?: "${state.unfinishedTaskCount} 个任务正在处理",
                        startAction = { Icon(Icons.Default.Build, null) },
                        endActions = {
                            if (state.pendingConfirmationCount > 0) {
                                Box(Modifier.size(10.dp).background(Color(0xFFFF4D4F), CircleShape))
                            }
                        },
                    )
                }
            }
        }
        item {
            Card {
                BasicComponent(
                    title = "从浏览器导入",
                    summary = "读取浏览器数据，核对后保存到数据管理",
                    startAction = { Icon(MiuixIcons.Download, null) },
                )
                ArrowPreference(
                    title = "收藏",
                    summary = "导入收藏、文件夹结构与来源信息",
                    startAction = { Icon(MiuixIcons.Favorites, null) },
                    onClick = actions.openBookmarksTask,
                )
                ArrowPreference(
                    title = "历史记录",
                    summary = "选择浏览器和任意时间范围",
                    startAction = { Icon(MiuixIcons.Recent, null) },
                    onClick = actions.openHistoryTask,
                )
                ArrowPreference(
                    title = "标签页",
                    summary = "保存已适配浏览器当前打开的页面",
                    startAction = { Icon(MiuixIcons.Layers, null) },
                    onClick = actions.openTabsTask,
                )
            }
        }
        item {
            Card {
                BasicComponent(
                    title = "导出到浏览器",
                    summary = "把数据管理中的内容写入一个或多个浏览器",
                    startAction = { Icon(MiuixIcons.UploadCloud, null) },
                )
                ArrowPreference(
                    title = "收藏",
                    summary = "导出收藏及文件夹结构",
                    startAction = { Icon(MiuixIcons.Favorites, null) },
                    onClick = actions.openBookmarksExportTask,
                )
                ArrowPreference(
                    title = "历史记录",
                    summary = "按所选时间范围导出访问记录",
                    startAction = { Icon(MiuixIcons.Recent, null) },
                    onClick = actions.openHistoryExportTask,
                )
                BasicComponent(
                    title = "标签页",
                    summary = "Via 与 Edge 暂未提供经过验证的安全写入适配",
                    startAction = { Icon(MiuixIcons.Layers, null) },
                )
            }
        }
        item {
            Card {
                ArrowPreference(
                    title = "数据备份与恢复",
                    summary = "备份或恢复数据管理中的全部内容",
                    startAction = { Icon(MiuixIcons.Backup, null) },
                    onClick = actions.openBackupRestore,
                )
            }
        }
        item { Spacer(Modifier.height(132.dp)) }
    }
}

@Composable
private fun MiuixSettings(state: BookmarkHelperUiState, actions: BookmarkHelperActions, topPadding: Dp) {
    LazyColumn(
        Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = topPadding),
        overscrollEffect = null,
    ) {
        item {
            Card {
                ArrowPreference(
                    title = "主题设置",
                    summary = "主题、动态颜色、模糊、底栏与页面缩放",
                    startAction = { Icon(Icons.Default.Info, null) },
                    onClick = actions.openPersonalization,
                )
                OverlayDropdownPreference(
                    title = "启动时显示",
                    summary = "选择每次打开应用时显示的页面",
                    items = MainPage.entries.map(::miuixPageLabel),
                    selectedIndex = state.startupPage.index,
                    onSelectedIndexChange = { actions.setStartupPage(MainPage.fromIndex(it)) },
                    startAction = { Icon(Icons.Default.Home, null) },
                )
            }
        }
        item {
            Card {
                ArrowPreference(
                    title = "应用信息",
                    summary = "版本、开源许可与开发者选项",
                    startAction = { Icon(Icons.Default.Info, null) },
                    onClick = actions.openAbout,
                )
            }
        }
        item { Spacer(Modifier.height(132.dp)) }
    }
}

@Composable
private fun MiuixDialogs(state: BookmarkHelperUiState, actions: BookmarkHelperActions) {
    state.dialogMessage?.let { message ->
        WindowDialog(show = true, onDismissRequest = actions.dismissMessage) {
            MiuixDialogAdvancedMaterial()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message)
                TextButton(text = "知道了", onClick = actions.dismissMessage, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun miuixPageTitle(page: MainPage) = when (page) {
    MainPage.HOME -> "书签助手"
    MainPage.TRANSFER -> "数据迁移"
    MainPage.DATA -> "数据管理"
    MainPage.SETTINGS -> "设置"
}
private fun miuixPageLabel(page: MainPage) = when (page) {
    MainPage.HOME -> "主页"
    MainPage.TRANSFER -> "迁移"
    MainPage.DATA -> "数据"
    MainPage.SETTINGS -> "设置"
}

private fun ActivationState.environmentLabel(name: String) = when (this) {
    ActivationState.CHECKING -> "正在检测"
    ActivationState.ACTIVE -> if (name == "Root") "权限已激活" else "框架运行中"
    ActivationState.INSTALLED -> "已安装，等待框架启动"
    ActivationState.INACTIVE -> "未激活"
    ActivationState.UNKNOWN -> if (name == "LSPosed") "需要 Root 后检测" else "状态未知"
}
