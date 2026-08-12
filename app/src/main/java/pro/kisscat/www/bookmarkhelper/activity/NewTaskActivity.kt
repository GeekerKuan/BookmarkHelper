@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pro.kisscat.www.bookmarkhelper.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import pro.kisscat.www.bookmarkhelper.sync.model.BrowserId
import pro.kisscat.www.bookmarkhelper.sync.task.HistoryRange
import pro.kisscat.www.bookmarkhelper.sync.task.SyncTaskCoordinator
import pro.kisscat.www.bookmarkhelper.sync.task.SyncTaskKind
import pro.kisscat.www.bookmarkhelper.ui.AppHapticProvider
import pro.kisscat.www.bookmarkhelper.ui.BookmarkHelperTheme
import pro.kisscat.www.bookmarkhelper.ui.MiuixBookmarkTheme
import pro.kisscat.www.bookmarkhelper.ui.UiMode
import pro.kisscat.www.bookmarkhelper.ui.UiPreferences
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.MiuixBlurredBar
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.HistoryDateRangeSlider
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.HistoryDateScale
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.localDayStartMillis
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.rememberMiuixBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.icon.extended.WorldClock
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

class NewTaskActivity : ComponentActivity() {
    enum class ImportKind { BOOKMARKS, HISTORY, OPEN_TABS }
    enum class MigrationDirection { IMPORT, EXPORT }

    companion object {
        private const val EXTRA_KIND = "import_kind"
        private const val EXTRA_DIRECTION = "migration_direction"
        private const val EXTRA_EXPORT_RECORD_IDS = "managed_export_record_ids"
        fun intent(
            context: Context,
            kind: ImportKind,
            direction: MigrationDirection = MigrationDirection.IMPORT,
            exportRecordIds: LongArray? = null,
        ) = Intent(context, NewTaskActivity::class.java).apply {
            require(exportRecordIds == null || exportRecordIds.isNotEmpty()) {
                "部分导出至少需要一条记录"
            }
            require(exportRecordIds == null || direction == MigrationDirection.EXPORT) {
                "记录选择范围仅适用于导出"
            }
            putExtra(EXTRA_KIND, kind.name)
            putExtra(EXTRA_DIRECTION, direction.name)
            exportRecordIds?.distinct()?.sorted()?.toLongArray()?.let {
                putExtra(EXTRA_EXPORT_RECORD_IDS, it)
            }
        }
    }

    private var exactHistoryRange by mutableStateOf<HistoryRange?>(null)
    private val dateRangeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            exactHistoryRange = DateRangeActivity.readResult(result.data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val kind = intent.getStringExtra(EXTRA_KIND)?.let {
            runCatching { ImportKind.valueOf(it) }.getOrNull()
        } ?: run { finish(); return }
        val direction = intent.getStringExtra(EXTRA_DIRECTION)?.let {
            runCatching { MigrationDirection.valueOf(it) }.getOrNull()
        } ?: MigrationDirection.IMPORT
        val exportRecordIds = if (direction == MigrationDirection.EXPORT) {
            intent.getLongArrayExtra(EXTRA_EXPORT_RECORD_IDS)
                ?.filter { it > 0L }
                ?.distinct()
                ?.sorted()
                ?.toLongArray()
                ?.takeIf { it.isNotEmpty() }
        } else null
        if (direction == MigrationDirection.EXPORT && intent.hasExtra(EXTRA_EXPORT_RECORD_IDS) &&
            exportRecordIds == null
        ) {
            finish()
            return
        }
        val availability = BrowserId.entries.associateWith(::browserInstalled)
        setContent {
            BindSystemBack(UiPreferences.predictiveBackEnabled(this))
            val content: @Composable () -> Unit = {
                ImportSetupScreen(
                    kind = kind,
                    direction = direction,
                    availability = availability,
                    miuix = UiPreferences.uiMode(this) == UiMode.MIUIX,
                    blurEnabled = UiPreferences.blurEnabled(this),
                    exactRange = exactHistoryRange,
                    exportSelectionCount = exportRecordIds?.size,
                    clearExactRange = { exactHistoryRange = null },
                    openExactRange = { currentRange ->
                        dateRangeLauncher.launch(DateRangeActivity.intent(this, currentRange))
                    },
                    back = ::finishSystemPage,
                    start = { browsers, range ->
                        startMigration(kind, direction, browsers, range, exportRecordIds)
                    },
                )
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

    private fun browserInstalled(browser: BrowserId): Boolean = runCatching {
        packageManager.getApplicationInfo(browser.packageName, 0)
    }.isSuccess

    private fun startMigration(
        kind: ImportKind,
        direction: MigrationDirection,
        browsers: Set<BrowserId>,
        range: HistoryRange?,
        exportRecordIds: LongArray?,
    ) {
        if (kind == ImportKind.HISTORY && exportRecordIds == null) requireNotNull(range)
        val id = when (direction) {
            MigrationDirection.IMPORT -> SyncTaskCoordinator.enqueueImport(kind.taskKind(), browsers, range)
            MigrationDirection.EXPORT -> SyncTaskCoordinator.enqueueExport(
                kind.taskKind(), browsers, range, exportRecordIds?.toSet(),
            )
        }
        openSystemPage(TaskActivity.intent(this, id))
        finish()
    }
}

@Composable
private fun ImportSetupScreen(
    kind: NewTaskActivity.ImportKind,
    direction: NewTaskActivity.MigrationDirection,
    availability: Map<BrowserId, Boolean>,
    miuix: Boolean,
    blurEnabled: Boolean,
    exactRange: HistoryRange?,
    exportSelectionCount: Int?,
    clearExactRange: () -> Unit,
    openExactRange: (HistoryRange?) -> Unit,
    back: () -> Unit,
    start: (Set<BrowserId>, HistoryRange?) -> Unit,
) {
    var selected by remember(kind, direction) { mutableStateOf<Set<BrowserId>>(emptySet()) }
    var dayPosition by remember { mutableFloatStateOf(1f) }
    var allHistory by remember { mutableStateOf(false) }
    val days = HistoryDateScale.daysAt(dayPosition)
    LaunchedEffect(exactRange) {
        exactRange?.let { range ->
            val inclusiveSeconds = (range.toUnixSeconds - range.fromUnixSeconds + 1L).coerceAtLeast(1L)
            val rangeDays = ((inclusiveSeconds + 86_399L) / 86_400L).toInt()
            dayPosition = HistoryDateScale.positionOf(rangeDays)
        }
    }
    val selectedRange = when {
        kind != NewTaskActivity.ImportKind.HISTORY -> null
        direction == NewTaskActivity.MigrationDirection.EXPORT && exportSelectionCount != null -> null
        allHistory -> HistoryRange(0L, Long.MAX_VALUE, "全部历史")
        exactRange != null -> exactRange
        else -> {
            val now = System.currentTimeMillis() / 1000L
            HistoryRange(now - days * 86_400L, now, "最近 $days 天")
        }
    }
    val datePickerRange = exactRange ?: if (
        kind == NewTaskActivity.ImportKind.HISTORY && !allHistory
    ) {
        val today = localDayStartMillis(System.currentTimeMillis()) / 1000L
        HistoryRange(today - (days - 1L) * 86_400L, today + 86_399L, "最近 $days 天")
    } else selectedRange
    val selectDayPosition: (Float) -> Unit = {
        dayPosition = it
        clearExactRange()
    }
    val toggle: (BrowserId, Boolean) -> Unit = { browser, checked ->
        selected = if (checked) selected + browser else selected - browser
    }
    if (miuix) MiuixImportSetup(
        kind, direction, availability, selected, toggle, dayPosition, selectDayPosition, days, allHistory,
        { allHistory = it }, exactRange, exportSelectionCount, { openExactRange(datePickerRange) }, blurEnabled, back,
        { start(selected, selectedRange) },
    ) else MaterialImportSetup(
        kind, direction, availability, selected, toggle, dayPosition, selectDayPosition, days, allHistory,
        { allHistory = it }, exactRange, exportSelectionCount, { openExactRange(datePickerRange) }, back,
        { start(selected, selectedRange) },
    )
}

@Composable
private fun MiuixImportSetup(
    kind: NewTaskActivity.ImportKind,
    direction: NewTaskActivity.MigrationDirection,
    availability: Map<BrowserId, Boolean>,
    selected: Set<BrowserId>,
    toggle: (BrowserId, Boolean) -> Unit,
    dayPosition: Float,
    setDayPosition: (Float) -> Unit,
    days: Int,
    allHistory: Boolean,
    setAllHistory: (Boolean) -> Unit,
    exactRange: HistoryRange?,
    exportSelectionCount: Int?,
    openExactRange: () -> Unit,
    blurEnabled: Boolean,
    back: () -> Unit,
    start: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberMiuixBlurBackdrop(blurEnabled)
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    MiuixScaffold(topBar = {
        MiuixBlurredBar(backdrop) {
            MiuixTopAppBar(
                title = direction.pageTitle(kind),
                color = barColor,
                navigationIcon = { MiuixIconButton(back) {
                    MiuixIcon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                } },
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
                item { MiuixCard {
                    BasicComponent(
                        title = kind.title(),
                        summary = kind.description(direction, exportSelectionCount),
                        startAction = { MiuixIcon(Icons.Default.Info, null) },
                    )
                } }
                item { MiuixCard { BasicComponent(
                    title = if (direction == NewTaskActivity.MigrationDirection.IMPORT)
                        "读取前请保存浏览器中的编辑内容" else "写入前请关闭浏览器中的编辑页面",
                    summary = if (direction == NewTaskActivity.MigrationDirection.IMPORT)
                        "为获得一致的数据快照，读取期间会暂时结束所选浏览器的进程。"
                    else "写入期间会暂时结束目标浏览器进程，并在写入前创建安全备份。",
                ) } }
                item { MiuixCard {
                    MiuixText(
                        if (direction == NewTaskActivity.MigrationDirection.IMPORT) "选择来源浏览器"
                        else "选择目标浏览器",
                        Modifier.padding(20.dp, 18.dp, 20.dp, 8.dp),
                    )
                    BrowserId.entries.forEach { browser ->
                        val supported = kind.supports(browser, direction)
                        val installed = availability[browser] == true
                        SwitchPreference(
                            title = browser.label(),
                            summary = browserSummary(installed, supported, direction),
                            checked = browser in selected,
                            onCheckedChange = { toggle(browser, it) },
                            enabled = installed && supported,
                            startAction = { BrowserAppIcon(browser) },
                        )
                    }
                } }
                if (kind == NewTaskActivity.ImportKind.HISTORY && exportSelectionCount == null) item { MiuixCard {
                    SwitchPreference(
                        title = "全部历史记录",
                        summary = "忽略天数，读取浏览器当前保留的全部历史记录",
                        checked = allHistory,
                        onCheckedChange = setAllHistory,
                        startAction = { MiuixIcon(MiuixIcons.Recent, null) },
                    )
                    if (!allHistory) ArrowPreference(
                        title = exactRange?.label ?: "最近 $days 天",
                        summary = "点击选择精确日期，或拖动滑块选择常用范围",
                        onClick = openExactRange,
                        bottomAction = {
                            HistoryDateRangeSlider(
                                position = dayPosition,
                                onPositionChange = setDayPosition,
                            )
                        },
                    )
                } }
                item {
                    if (selected.isNotEmpty()) {
                        MiuixTextButton(
                            if (direction == NewTaskActivity.MigrationDirection.IMPORT)
                                "读取并生成预览" else "导出到所选浏览器",
                            start,
                            Modifier.fillMaxWidth(),
                        )
                    } else {
                        MiuixCard { BasicComponent(
                            title = "请选择至少一个可用浏览器",
                            summary = if (direction == NewTaskActivity.MigrationDirection.IMPORT)
                                "可同时选择多个来源，重复记录会在数据管理中自动合并。"
                            else "可同时写入多个目标；每个浏览器会独立报告结果。",
                        ) }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun BrowserAppIcon(browser: BrowserId) {
    val context = LocalContext.current
    val bitmap = remember(browser) {
        runCatching {
            context.packageManager.getApplicationIcon(browser.packageName)
                .toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)),
        )
    } else {
        MiuixIcon(MiuixIcons.WorldClock, null)
    }
}

@Composable
private fun MaterialImportSetup(
    kind: NewTaskActivity.ImportKind,
    direction: NewTaskActivity.MigrationDirection,
    availability: Map<BrowserId, Boolean>,
    selected: Set<BrowserId>,
    toggle: (BrowserId, Boolean) -> Unit,
    dayPosition: Float,
    setDayPosition: (Float) -> Unit,
    days: Int,
    allHistory: Boolean,
    setAllHistory: (Boolean) -> Unit,
    exactRange: HistoryRange?,
    exportSelectionCount: Int?,
    openExactRange: () -> Unit,
    back: () -> Unit,
    start: () -> Unit,
) {
    Scaffold(topBar = {
        LargeTopAppBar(
            title = { Text(kind.title()) },
            navigationIcon = { IconButton(back) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            } },
        )
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Card(Modifier.fillMaxWidth()) { ListItem(
                headlineContent = { Text(kind.title()) },
                supportingContent = { Text(kind.description(direction, exportSelectionCount)) },
                leadingContent = { Icon(Icons.Default.Info, null) },
            ) } }
            item { Card(Modifier.fillMaxWidth()) { ListItem(
                headlineContent = { Text("读取前请保存浏览器中的编辑内容") },
                supportingContent = { Text("读取期间会暂时结束所选浏览器的进程，以获得一致的数据快照。") },
            ) } }
            BrowserId.entries.forEach { browser -> item {
                        val supported = kind.supports(browser, direction)
                val installed = availability[browser] == true
                Card(Modifier.fillMaxWidth()) { ListItem(
                    headlineContent = { Text(browser.label()) },
                        supportingContent = { Text(browserSummary(installed, supported, direction)) },
                    trailingContent = { Switch(
                        checked = browser in selected,
                        enabled = installed && supported,
                        onCheckedChange = { toggle(browser, it) },
                    ) },
                ) }
            } }
            if (kind == NewTaskActivity.ImportKind.HISTORY && exportSelectionCount == null) item { Card(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("全部历史记录") },
                    supportingContent = { Text("忽略天数，读取当前保留的全部记录") },
                    trailingContent = { Switch(allHistory, setAllHistory) },
                )
                if (!allHistory) Column(Modifier.padding(16.dp)) {
                    ListItem(
                        headlineContent = { Text(exactRange?.label ?: "最近 $days 天") },
                        supportingContent = { Text("点击选择精确开始与结束日期") },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                        modifier = Modifier.fillMaxWidth().clickable(onClick = openExactRange),
                    )
                    Slider(
                        value = dayPosition,
                        onValueChange = setDayPosition,
                        valueRange = 0f..5f,
                    )
                }
            } }
            item {
                Button(start, Modifier.fillMaxWidth(), enabled = selected.isNotEmpty()) {
                    Text("读取并生成预览")
                }
            }
        }
    }
}

private fun NewTaskActivity.ImportKind.taskKind() = when (this) {
    NewTaskActivity.ImportKind.BOOKMARKS -> SyncTaskKind.BOOKMARKS
    NewTaskActivity.ImportKind.HISTORY -> SyncTaskKind.HISTORY
    NewTaskActivity.ImportKind.OPEN_TABS -> SyncTaskKind.OPEN_TABS
}

private fun NewTaskActivity.ImportKind.title() = when (this) {
    NewTaskActivity.ImportKind.BOOKMARKS -> "收藏"
    NewTaskActivity.ImportKind.HISTORY -> "历史记录"
    NewTaskActivity.ImportKind.OPEN_TABS -> "标签页"
}

private fun NewTaskActivity.ImportKind.description(
    direction: NewTaskActivity.MigrationDirection,
    exportSelectionCount: Int? = null,
) =
    if (direction == NewTaskActivity.MigrationDirection.IMPORT) when (this) {
        NewTaskActivity.ImportKind.BOOKMARKS ->
            "从一个或多个浏览器读取收藏与文件夹结构，核对后保存到数据管理。"
        NewTaskActivity.ImportKind.HISTORY ->
            "选择浏览器与时间范围，保留原访问日期并自动合并相同访问记录。"
        NewTaskActivity.ImportKind.OPEN_TABS ->
            "保存浏览器当前打开的标签页；只有已验证结构的浏览器可以选择。"
    } else (when (this) {
        NewTaskActivity.ImportKind.BOOKMARKS ->
            "把数据管理中的收藏与文件夹结构安全写入一个或多个浏览器。"
        NewTaskActivity.ImportKind.HISTORY ->
            "按所选时间范围将数据管理中的历史记录写入目标浏览器。"
        NewTaskActivity.ImportKind.OPEN_TABS ->
            "当前尚无经过验证的浏览器标签页写入适配，不会执行伪成功操作。"
    }) + exportSelectionCount?.let { " 本次仅导出已选择的 $it 条记录。" }.orEmpty()

private fun NewTaskActivity.MigrationDirection.pageTitle(kind: NewTaskActivity.ImportKind) = when (this) {
    NewTaskActivity.MigrationDirection.IMPORT -> "导入${kind.title()}"
    NewTaskActivity.MigrationDirection.EXPORT -> "导出${kind.title()}"
}

private fun NewTaskActivity.ImportKind.supports(
    browser: BrowserId,
    direction: NewTaskActivity.MigrationDirection,
) = browser in BrowserId.entries && when (direction) {
    NewTaskActivity.MigrationDirection.IMPORT -> true
    NewTaskActivity.MigrationDirection.EXPORT -> this != NewTaskActivity.ImportKind.OPEN_TABS
}

private fun BrowserId.label() = when (this) {
    BrowserId.VIA -> "Via 浏览器"
    BrowserId.EDGE -> "Microsoft Edge"
}

private fun browserSummary(
    installed: Boolean,
    supported: Boolean,
    direction: NewTaskActivity.MigrationDirection,
) = when {
    !installed -> "未安装或尚未初始化"
    !supported -> "当前版本尚未完成此类数据写入适配"
    direction == NewTaskActivity.MigrationDirection.IMPORT -> "已安装，可读取"
    else -> "已安装，可安全写入"
}
