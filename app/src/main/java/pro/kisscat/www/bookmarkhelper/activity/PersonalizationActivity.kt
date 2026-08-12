@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pro.kisscat.www.bookmarkhelper.activity

import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
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
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * SukiSU-Ultra ColorPaletteScreen adapted for BookmarkHelper.
 * Kernel/root-manager-only settings are intentionally omitted; the original interaction model is retained.
 */
class PersonalizationActivity : ComponentActivity() {
    private var settings by mutableStateOf(AppearanceSettings())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settings = readSettings()
        setContent {
            BindSystemBack(settings.predictiveBack, settings.predictiveBackMaxProgress)
            val systemDensity = LocalDensity.current
            val scaledDensity = Density(
                density = systemDensity.density * settings.scale,
                fontScale = systemDensity.fontScale,
            )
            val content: @Composable () -> Unit = {
                if (UiPreferences.uiMode(this) == UiMode.MIUIX) {
                    MiuixAppearance(settings, ::finishSystemPage, ::update)
                } else MaterialAppearance(settings, ::finishSystemPage, ::update)
            }
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                AppHapticProvider(settings.haptics) {
                    if (UiPreferences.uiMode(this) == UiMode.MIUIX) {
                        MiuixBookmarkTheme(settings.theme, settings.monet, content)
                    } else BookmarkHelperTheme(settings.theme, settings.monet, content)
                }
            }
        }
    }

    private fun readSettings() = AppearanceSettings(
        theme = UiPreferences.themeMode(this), monet = UiPreferences.monetEnabled(this),
        blur = UiPreferences.blurEnabled(this), floating = UiPreferences.floatingBarEnabled(this),
        glass = UiPreferences.glassEnabled(this), scale = UiPreferences.pageScale(this),
        haptics = UiPreferences.hapticsEnabled(this), transitions = UiPreferences.transitionsEnabled(this),
        predictiveBack = UiPreferences.predictiveBackEnabled(this),
        predictiveBackMaxProgress = UiPreferences.predictiveBackMaxProgress(this),
        showDataCardUrls = UiPreferences.showDataCardUrls(this),
    )

    private fun update(value: AppearanceSettings) {
        if (value.haptics) window.decorView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        settings = value
        UiPreferences.setThemeMode(this, value.theme)
        UiPreferences.setMonetEnabled(this, value.monet)
        UiPreferences.setBlurEnabled(this, value.blur)
        UiPreferences.setFloatingBarEnabled(this, value.floating)
        UiPreferences.setGlassEnabled(this, value.glass)
        UiPreferences.setPageScale(this, value.scale)
        UiPreferences.setHapticsEnabled(this, value.haptics)
        UiPreferences.setTransitionsEnabled(this, value.transitions)
        UiPreferences.setPredictiveBackEnabled(this, value.predictiveBack)
        UiPreferences.setPredictiveBackMaxProgress(this, value.predictiveBackMaxProgress)
        UiPreferences.setShowDataCardUrls(this, value.showDataCardUrls)
    }
}

private data class AppearanceSettings(
    val theme: Int = 0,
    val monet: Boolean = false,
    val blur: Boolean = true,
    val floating: Boolean = true,
    val glass: Boolean = true,
    val scale: Float = 1f,
    val haptics: Boolean = true,
    val transitions: Boolean = true,
    val predictiveBack: Boolean = true,
    val predictiveBackMaxProgress: Float = 1f,
    val showDataCardUrls: Boolean = false,
)

@Composable
private fun MiuixAppearance(state: AppearanceSettings, back: () -> Unit, update: (AppearanceSettings) -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberMiuixBlurBackdrop(state.blur)
    val barColor = if (backdrop != null) Color.Transparent else top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surface
    MiuixScaffold(
        topBar = {
            MiuixBlurredBar(backdrop) {
                MiuixTopAppBar(
                    title = "主题设置",
                    color = barColor,
                    navigationIcon = { MiuixIconButton(back) {
                        MiuixIcon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    } },
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
            item {
                Spacer(Modifier.height(12.dp))
                TabRow(
                    tabs = listOf("跟随系统", "浅色", "深色"),
                    selectedTabIndex = state.theme.coerceIn(0, 2),
                    onTabSelected = { update(state.copy(theme = it)) },
                    height = 48.dp,
                )
            }
            item { MiuixCard {
                SwitchPreference(
                    title = "启用 Monet 颜色", summary = "从系统壁纸提取应用配色",
                    checked = state.monet, onCheckedChange = { update(state.copy(monet = it)) },
                    startAction = { MiuixIcon(Icons.Rounded.Style, null) },
                )
                if (Build.VERSION.SDK_INT >= 33) SwitchPreference(
                    title = "模糊", summary = "启用顶栏与底栏的模糊效果",
                    checked = state.blur, onCheckedChange = { update(state.copy(blur = it)) },
                    startAction = { MiuixIcon(Icons.Rounded.BlurOn, null) },
                )
                SwitchPreference(
                    title = "悬浮底栏", summary = "使用 Apple 风格的悬浮导航栏",
                    checked = state.floating, onCheckedChange = { update(state.copy(floating = it)) },
                    startAction = { MiuixIcon(Icons.Rounded.CallToAction, null) },
                )
                AnimatedVisibility(state.floating && Build.VERSION.SDK_INT >= 33) {
                    SwitchPreference(
                        title = "悬浮底栏玻璃效果", summary = "在透明底栏中折射并模糊页面内容",
                        checked = state.glass, onCheckedChange = { update(state.copy(glass = it)) },
                        startAction = { MiuixIcon(Icons.Rounded.WaterDrop, null) },
                    )
                }
            } }
            item { MiuixCard {
                SwitchPreference(
                    title = "系统触感反馈", summary = "按压、切换和滑动时使用系统触感",
                    checked = state.haptics, onCheckedChange = { update(state.copy(haptics = it)) },
                    startAction = { MiuixIcon(Icons.Rounded.Fingerprint, null) },
                )
                SwitchPreference(
                    title = "显示网址", summary = "在浏览数据卡片中显示完整网址",
                    checked = state.showDataCardUrls,
                    onCheckedChange = { update(state.copy(showDataCardUrls = it)) },
                    startAction = { MiuixIcon(Icons.Rounded.Wallpaper, null) },
                )
                SwitchPreference(
                    title = "系统过渡动画", summary = "使用 Android 与系统界面的原生页面动画",
                    checked = state.transitions, onCheckedChange = { update(state.copy(transitions = it)) },
                    startAction = { MiuixIcon(Icons.Rounded.Style, null) },
                )
                if (Build.VERSION.SDK_INT >= 34) SwitchPreference(
                    title = "预见式返回", summary = "返回手势期间连续预览上一级页面",
                    checked = state.predictiveBack, onCheckedChange = { update(state.copy(predictiveBack = it)) },
                    startAction = { MiuixIcon(Icons.AutoMirrored.Rounded.MenuOpen, null) },
                )
                if (Build.VERSION.SDK_INT >= 34 && state.predictiveBack) {
                    var predictiveProgress by remember(state.predictiveBackMaxProgress) {
                        mutableFloatStateOf(state.predictiveBackMaxProgress)
                    }
                    BasicComponent(
                        title = "预见式返回最大进度",
                        summary = "调整返回手势中页面位移、缩放与淡出的最大幅度",
                        startAction = { MiuixIcon(Icons.AutoMirrored.Rounded.MenuOpen, null) },
                        endActions = { MiuixText("${(predictiveProgress * 100).toInt()}%") },
                        bottomAction = {
                            MiuixSlider(
                                value = predictiveProgress,
                                onValueChange = { predictiveProgress = it },
                                onValueChangeFinished = {
                                    update(state.copy(predictiveBackMaxProgress = predictiveProgress))
                                },
                                valueRange = .25f..1f,
                                showKeyPoints = true,
                                keyPoints = listOf(.25f, .5f, .75f, 1f),
                                magnetThreshold = .015f,
                                hapticEffect = if (state.haptics) SliderDefaults.SliderHapticEffect.Step
                                else SliderDefaults.SliderHapticEffect.None,
                            )
                        },
                    )
                }
                var slider by remember(state.scale) { mutableFloatStateOf(state.scale) }
                BasicComponent(
                    title = "页面缩放", summary = "调整界面元素与留白尺寸",
                    startAction = { MiuixIcon(Icons.Rounded.AspectRatio, null) },
                    endActions = { MiuixText("${(slider * 100).toInt()}%") },
                    bottomAction = {
                        MiuixSlider(
                            value = slider,
                            onValueChange = { slider = it },
                            onValueChangeFinished = {
                                update(state.copy(scale = slider))
                            },
                            valueRange = .8f..1.1f,
                            showKeyPoints = true,
                            keyPoints = listOf(.8f, .9f, 1f, 1.1f),
                            magnetThreshold = .01f,
                            hapticEffect = if (state.haptics) SliderDefaults.SliderHapticEffect.Step
                            else SliderDefaults.SliderHapticEffect.None,
                        )
                    },
                )
            } }
            item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun MaterialAppearance(state: AppearanceSettings, back: () -> Unit, update: (AppearanceSettings) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(
            title = { Text("主题设置") },
            navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            scrollBehavior = scrollBehavior,
        ) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("跟随系统", "浅色", "深色").forEachIndexed { index, label ->
                        FilterChip(
                            selected = state.theme == index,
                            onClick = { update(state.copy(theme = index)) },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item { Card { Column {
                MaterialSwitch("Monet 动态颜色", "从系统壁纸提取应用配色", state.monet) { update(state.copy(monet = it)) }
                MaterialSwitch("模糊", "启用顶栏与底栏的模糊效果", state.blur) { update(state.copy(blur = it)) }
                MaterialSwitch("悬浮底栏", "使用悬浮式页面导航栏", state.floating) { update(state.copy(floating = it)) }
                AnimatedVisibility(state.floating) {
                    MaterialSwitch("悬浮底栏玻璃效果", "折射并模糊底栏下方内容", state.glass) { update(state.copy(glass = it)) }
                }
            } } }
            item { Card { Column {
                MaterialSwitch("系统触感反馈", "按压、切换和滑动时使用系统触感", state.haptics) { update(state.copy(haptics = it)) }
                MaterialSwitch("系统过渡动画", "使用 Android 与系统界面的原生页面动画", state.transitions) { update(state.copy(transitions = it)) }
                if (Build.VERSION.SDK_INT >= 34) MaterialSwitch("预见式返回", "返回时预览上一级页面", state.predictiveBack) { update(state.copy(predictiveBack = it)) }
                var slider by remember(state.scale) { mutableFloatStateOf(state.scale) }
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("页面缩放", style = MaterialTheme.typography.titleMedium)
                        Text("${(slider * 100).toInt()}%")
                    }
                    Slider(
                        value = slider, onValueChange = {
                            slider = it
                            update(state.copy(scale = it))
                        },
                        valueRange = .8f..1.1f, steps = 2,
                    )
                }
            } } }
        }
    }
}

@Composable
private fun MaterialSwitch(title: String, summary: String, checked: Boolean, changed: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) }, supportingContent = { Text(summary) },
        trailingContent = { Switch(checked, changed) },
    )
}
