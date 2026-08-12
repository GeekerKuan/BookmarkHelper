@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pro.kisscat.www.bookmarkhelper.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import pro.kisscat.www.bookmarkhelper.sync.model.BrowserId
import pro.kisscat.www.bookmarkhelper.sync.model.IntermediateDataRepository
import pro.kisscat.www.bookmarkhelper.sync.model.IntermediateItemKind
import pro.kisscat.www.bookmarkhelper.sync.model.IntermediateItemRef
import pro.kisscat.www.bookmarkhelper.sync.model.ManagedDataPage
import pro.kisscat.www.bookmarkhelper.sync.model.ManagedDataRow
import pro.kisscat.www.bookmarkhelper.sync.model.BookmarkFolderEntry
import pro.kisscat.www.bookmarkhelper.sync.model.BookmarkFolderOperationResult
import pro.kisscat.www.bookmarkhelper.sync.model.BrowserSpaceCount
import pro.kisscat.www.bookmarkhelper.ui.BookmarkHelperTheme
import pro.kisscat.www.bookmarkhelper.ui.AppHapticProvider
import pro.kisscat.www.bookmarkhelper.ui.MiuixBookmarkTheme
import pro.kisscat.www.bookmarkhelper.ui.UiMode
import pro.kisscat.www.bookmarkhelper.ui.UiPreferences
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.MiuixBlurredBar
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.MiuixDatePickerBottomSheet
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.MiuixDialogAdvancedMaterial
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.rememberMiuixBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.MoveFile
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Paste
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        IntermediateDataRepository.initialize(applicationContext)
        val kind = intent.getStringExtra(EXTRA_KIND)?.let {
            runCatching { IntermediateItemKind.valueOf(it) }.getOrNull()
        } ?: run { finish(); return }
        val folderPath = if (kind == IntermediateItemKind.BOOKMARK) {
            intent.getStringExtra(EXTRA_FOLDER_PATH).orEmpty()
        } else ""
        val hasSpace = intent.hasExtra(EXTRA_BROWSER)
        val browser = intent.getStringExtra(EXTRA_BROWSER)?.takeUnless { it == ALL_SOURCES }?.let {
            runCatching { BrowserId.valueOf(it) }.getOrNull()
        }
        setContent {
            BindSystemBack(UiPreferences.predictiveBackEnabled(this))
            val uiMode = UiPreferences.uiMode(this)
            val content: @Composable () -> Unit = {
                if (!hasSpace) DataSpacePicker(
                    kind = kind,
                    blurEnabled = UiPreferences.blurEnabled(this),
                    back = ::finishSystemPage,
                    open = { openSystemPage(spaceIntent(this, kind, it)) },
                ) else DataManagerScreen(
                        kind = kind,
                        folderPath = folderPath,
                        browser = browser,
                        uiMode = uiMode,
                        blurEnabled = UiPreferences.blurEnabled(this),
                        showCardUrls = UiPreferences.showDataCardUrls(this),
                        back = ::finishSystemPage,
                        openFolder = { openSystemPage(spaceIntent(this, kind, browser, it)) },
                        openInBrowser = ::openInBrowser,
                        exportData = { recordIds ->
                            openSystemPage(
                                NewTaskActivity.intent(
                                    this,
                                    kind.newTaskKind(),
                                    NewTaskActivity.MigrationDirection.EXPORT,
                                    recordIds,
                                ),
                            )
                        },
                    )
            }
            AppHapticProvider(UiPreferences.hapticsEnabled(this)) {
                if (uiMode == UiMode.MIUIX) {
                    MiuixBookmarkTheme(UiPreferences.themeMode(this), UiPreferences.monetEnabled(this), content)
                } else {
                    BookmarkHelperTheme(UiPreferences.themeMode(this), UiPreferences.monetEnabled(this), content)
                }
            }
        }
    }

    companion object {
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_FOLDER_PATH = "folder_path"
        private const val EXTRA_BROWSER = "browser"
        private const val ALL_SOURCES = "ALL"
        fun intent(context: Context, kind: IntermediateItemKind, folderPath: String = "") =
            Intent(context, DataManagerActivity::class.java)
                .putExtra(EXTRA_KIND, kind.name)
                .putExtra(EXTRA_FOLDER_PATH, folderPath)

        private fun spaceIntent(
            context: Context,
            kind: IntermediateItemKind,
            browser: BrowserId?,
            folderPath: String = "",
        ) = intent(context, kind, folderPath)
            .putExtra(EXTRA_BROWSER, browser?.name ?: ALL_SOURCES)
    }

    private fun openInBrowser(url: String) {
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return
        if (uri.scheme != "http" && uri.scheme != "https") return
        runCatching {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "选择浏览器"))
        }
    }
}

@Composable
private fun DataSpacePicker(
    kind: IntermediateItemKind,
    blurEnabled: Boolean,
    back: () -> Unit,
    open: (BrowserId?) -> Unit,
) {
    val state by IntermediateDataRepository.state.collectAsState()
    var counts by remember { mutableStateOf(emptyList<BrowserSpaceCount>()) }
    LaunchedEffect(kind, state.revision) {
        counts = withContext(Dispatchers.IO) { IntermediateDataRepository.browserSpaceCounts(kind) }
    }
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberMiuixBlurBackdrop(blurEnabled)
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    MiuixScaffold(topBar = {
        MiuixBlurredBar(backdrop) {
            MiuixTopAppBar(
                title = kind.title(),
                color = barColor,
                navigationIcon = { MiuixIconButton(back) {
                    MiuixIcon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                } },
                scrollBehavior = scrollBehavior,
            )
        }
    }) { padding ->
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
        ) {
            LazyColumn(
                Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection).padding(horizontal = 12.dp),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                overscrollEffect = null,
            ) {
                item {
                    MiuixText(
                        "按浏览器目录整理，选择后可搜索、批量编辑和移动。",
                        Modifier.padding(horizontal = 8.dp),
                    )
                }
                item {
                    MiuixCard(
                        onClick = { open(null) },
                        showIndication = true,
                        pressFeedbackType = PressFeedbackType.Sink,
                    ) {
                        BasicComponent(
                            title = "全部来源",
                            summary = "查看已经合并去重的全部${kind.title()}",
                            endActions = { MiuixIcon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                        )
                    }
                }
                items(counts, key = { it.browser.name }) { space ->
                    MiuixCard(
                        onClick = { open(space.browser) },
                        showIndication = true,
                        pressFeedbackType = PressFeedbackType.Sink,
                    ) {
                        BasicComponent(
                            title = "${space.browser.browserLabel()} 数据",
                            summary = "${space.count} 条 · 保留为对应浏览器的数据目录",
                            startAction = { MiuixIcon(MiuixIcons.Backup, null) },
                            endActions = { MiuixIcon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DataManagerScreen(
    kind: IntermediateItemKind,
    folderPath: String,
    browser: BrowserId?,
    uiMode: UiMode,
    blurEnabled: Boolean,
    showCardUrls: Boolean,
    back: () -> Unit,
    openFolder: (String) -> Unit,
    openInBrowser: (String) -> Unit,
    exportData: (LongArray?) -> Unit,
) {
    val repositoryState by IntermediateDataRepository.state.collectAsState()
    var search by remember { mutableStateOf("") }
    var historyOffset by remember { mutableIntStateOf(0) }
    var historyJumpGeneration by remember { mutableIntStateOf(0) }
    var limit by remember { mutableIntStateOf(100) }
    var page by remember { mutableStateOf(ManagedDataPage(emptyList(), 0, 0, false)) }
    var editing by remember { mutableStateOf<ManagedDataRow?>(null) }
    var deleting by remember { mutableStateOf<ManagedDataRow?>(null) }
    var selected by remember { mutableStateOf<Set<IntermediateItemRef>>(emptySet()) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var bookmarkClipboard by remember { mutableStateOf<Set<IntermediateItemRef>>(emptySet()) }
    var deletingSelection by remember { mutableStateOf(false) }
    var movingSelection by remember { mutableStateOf(false) }
    var movingFolder by remember { mutableStateOf(false) }
    var deletingFolder by remember { mutableStateOf<pro.kisscat.www.bookmarkhelper.sync.model.BookmarkFolderContents?>(null) }
    var historyPickerDate by remember { mutableStateOf<Long?>(null) }
    val operationScope = rememberCoroutineScope()

    var creatingFolder by remember { mutableStateOf(false) }

    LaunchedEffect(kind, folderPath, browser, search, historyOffset, limit, repositoryState.revision) {
        page = withContext(Dispatchers.IO) {
            if (kind == IntermediateItemKind.BOOKMARK) {
                IntermediateDataRepository.queryBookmarkFolder(folderPath, search, 0, limit, browser)
            } else IntermediateDataRepository.query(kind, search, historyOffset, limit, browser)
        }
        selected = selected.intersect(page.rows.map { it.ref }.toSet())
    }

    val toggle: (IntermediateItemRef) -> Unit = { ref ->
        selectedFolder = null
        selected = if (ref in selected) selected - ref else selected + ref
    }
    val selectGroup: (List<ManagedDataRow>) -> Unit = { rows ->
        selectedFolder = null
        val refs = rows.map { it.ref }.toSet()
        selected = if (refs.all(selected::contains)) selected - refs else selected + refs
    }
    val clearSelection = {
        selected = emptySet()
        selectedFolder = null
    }
    BackHandler(enabled = selected.isNotEmpty() || selectedFolder != null) {
        clearSelection()
    }

    if (uiMode == UiMode.MIUIX) {
        MiuixDataManagerContent(
            kind, page, search, { search = it; historyOffset = 0; limit = 100 }, { limit += 100 },
            { editing = it }, { deleting = it }, selected, toggle, selectGroup,
            clearSelection, { deletingSelection = true },
            { movingSelection = true },
            { bookmarkClipboard = selected; selected = emptySet() },
            {
                val targets = bookmarkClipboard
                operationScope.launch(Dispatchers.IO) {
                    IntermediateDataRepository.copyBookmarks(targets, folderPath)
                }
            },
            bookmarkClipboard.isNotEmpty(), page.folders, openFolder,
            selectedFolder,
            { path ->
                selected = emptySet()
                selectedFolder = if (selectedFolder == path) null else path
            },
            { movingFolder = true },
            {
                val path = selectedFolder ?: return@MiuixDataManagerContent
                operationScope.launch {
                    when (val result = withContext(Dispatchers.IO) {
                        browser?.let { IntermediateDataRepository.deleteBookmarkFolder(it, path) }
                    }) {
                        is BookmarkFolderOperationResult.ConfirmationRequired -> deletingFolder = result.contents
                        is BookmarkFolderOperationResult.Completed -> selectedFolder = null
                        else -> Unit
                    }
                }
            },
            { timestamp -> historyPickerDate = timestamp },
            historyJumpGeneration,
            browser != null,
            when (kind) {
                IntermediateItemKind.BOOKMARK -> repositoryState.bookmarkCount > 0
                IntermediateItemKind.HISTORY -> repositoryState.historyCount > 0
                IntermediateItemKind.OPEN_TAB -> repositoryState.openTabCount > 0
            },
            { exportData(null) },
            selected.isNotEmpty() || selectedFolder?.let { path ->
                page.folders.firstOrNull { it.path == path }?.recordCount?.let { it > 0 }
            } == true,
            {
                val selectedRows = selected.mapTo(linkedSetOf()) { it.rowId }
                val folder = selectedFolder
                operationScope.launch {
                    if (folder != null) {
                        selectedRows += withContext(Dispatchers.IO) {
                            collectBookmarkFolderRecordIds(folder, browser)
                        }.asIterable()
                    }
                    if (selectedRows.isNotEmpty()) exportData(selectedRows.toLongArray())
                }
            },
            "${browser?.browserLabel()?.let { "$it · " }.orEmpty()}${folderPath.pageTitle(kind)}",
            if (kind == IntermediateItemKind.BOOKMARK && browser != null) ({ creatingFolder = true }) else null,
            showCardUrls, blurEnabled, back,
        )
    } else Scaffold(
        modifier = Modifier.nestedScroll(TopAppBarDefaults.exitUntilCollapsedScrollBehavior().nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(folderPath.pageTitle(kind)) },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { padding ->
        val materialListState = rememberLazyListState()
        LaunchedEffect(materialListState, page.hasMore, page.rows.size) {
            snapshotFlow {
                val info = materialListState.layoutInfo
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                page.hasMore && lastVisible >= (info.totalItemsCount - 5).coerceAtLeast(0)
            }.distinctUntilChanged().collect { shouldLoad ->
                if (shouldLoad) limit += 100
            }
        }
        LazyColumn(
            state = materialListState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it; limit = 100 },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    label = { Text("搜索${kind.title()}") },
                )
                Text("共 ${page.total} 条", Modifier.padding(vertical = 8.dp))
            }
            if (selected.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text("已选择 ${selected.size} 条")
                    androidx.compose.foundation.layout.Row {
                        TextButton(onClick = { selected = emptySet() }) { Text("取消选择") }
                        if (kind == IntermediateItemKind.BOOKMARK) {
                            TextButton(onClick = { movingSelection = true }) { Text("移动") }
                        }
                        TextButton(onClick = { deletingSelection = true }) { Text("删除") }
                    }
                } }
            }
            items(page.folders, key = { "folder-${it.path}" }) { folder ->
                Card(onClick = { openFolder(folder.path) }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(folder.name) },
                        supportingContent = { Text("${folder.recordCount} 条收藏") },
                        leadingContent = { Icon(MiuixIcons.Backup, null) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    )
                }
            }
            page.rows.groupBy { it.sectionLabel(kind) }.forEach { (section, rows) ->
                item(key = "section-$section") {
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(section)
                        TextButton(onClick = { selectGroup(rows) }) {
                            Text(if (rows.all { it.ref in selected }) "取消全选" else "全选")
                        }
                    }
                }
                items(rows, key = { it.ref.rowId }) { row ->
                Card(onClick = { toggle(row.ref) }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(row.title.ifBlank { row.url }) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                if (showCardUrls) Text(row.url)
                                if (row.detail.isNotBlank()) Text(row.detail)
                                Text("来源：${row.sources.sourceLabels()}")
                            }
                        },
                        leadingContent = {
                            Checkbox(row.ref in selected, { toggle(row.ref) })
                        },
                        trailingContent = {
                            Column {
                                IconButton(onClick = { editing = row }) { Icon(MiuixIcons.Edit, "编辑") }
                                IconButton(onClick = { deleting = row }) { Icon(MiuixIcons.Delete, "删除") }
                            }
                        },
                    )
                }
                }
            }
        }
    }

    editing?.let { row ->
        val save: (String, String, String) -> Unit = { title, url, detail ->
            editing = null
            operationScope.launch(Dispatchers.IO) {
                IntermediateDataRepository.update(row.ref, title, url, detail)
            }
        }
        if (uiMode == UiMode.MIUIX) MiuixEditRecordDialog(
            row, kind, { editing = null }, save, openInBrowser
        ) else EditRecordDialog(
            row, kind, onDismiss = { editing = null }, onSave = save, onOpen = openInBrowser
        )
    }
    deleting?.let { row ->
        val confirm: () -> Unit = {
            deleting = null
            operationScope.launch(Dispatchers.IO) { IntermediateDataRepository.delete(row.ref) }
        }
        if (uiMode == UiMode.MIUIX) WindowDialog(
            title = "删除这条记录？",
            summary = row.title.ifBlank { row.url },
            show = true,
            onDismissRequest = { deleting = null },
        ) {
            MiuixDialogButtons(
                confirmText = "删除",
                onConfirm = confirm,
                onDismiss = { deleting = null },
                destructive = true,
            )
        } else AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这条记录？") },
            text = { Text(row.title.ifBlank { row.url }) },
            confirmButton = { TextButton(onClick = confirm) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
    if (deletingSelection) {
        val confirm: () -> Unit = {
            deletingSelection = false
            val targets = selected
            selected = emptySet()
            operationScope.launch(Dispatchers.IO) { IntermediateDataRepository.delete(targets) }
        }
        if (uiMode == UiMode.MIUIX) WindowDialog(
            title = "删除选中的 ${selected.size} 条记录？",
            summary = "删除后无法从应用的数据管理中恢复。",
            show = true,
            onDismissRequest = { deletingSelection = false },
        ) {
            MiuixDialogButtons(
                confirmText = "删除",
                onConfirm = confirm,
                onDismiss = { deletingSelection = false },
                destructive = true,
            )
        } else AlertDialog(
            onDismissRequest = { deletingSelection = false },
            title = { Text("删除选中的 ${selected.size} 条记录？") },
            confirmButton = { TextButton(onClick = confirm) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deletingSelection = false }) { Text("取消") } },
        )
    }
    if (movingSelection && kind == IntermediateItemKind.BOOKMARK) {
        var folder by remember { mutableStateOf("") }
        val confirm: () -> Unit = {
            movingSelection = false
            val targets = selected
            selected = emptySet()
            operationScope.launch(Dispatchers.IO) {
                IntermediateDataRepository.moveBookmarks(targets, folder)
            }
        }
        if (uiMode == UiMode.MIUIX) WindowDialog(
            title = "移动到文件夹",
            show = true,
            onDismissRequest = { movingSelection = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixTextField(folder, { folder = it }, label = "文件夹路径", modifier = Modifier.fillMaxWidth())
                MiuixDialogButtons("移动", confirm, { movingSelection = false })
            }
        } else AlertDialog(
            onDismissRequest = { movingSelection = false },
            title = { Text("移动到文件夹") },
            text = { OutlinedTextField(folder, { folder = it }, label = { Text("文件夹路径") }) },
            confirmButton = { TextButton(onClick = confirm) { Text("移动") } },
            dismissButton = { TextButton(onClick = { movingSelection = false }) { Text("取消") } },
        )
    }
    if (creatingFolder && kind == IntermediateItemKind.BOOKMARK && browser != null) {
        var folderName by remember { mutableStateOf("") }
        WindowDialog(
            title = "新建文件夹",
            summary = "文件夹将保存在 ${browser.browserLabel()} 数据目录中。",
            show = true,
            onDismissRequest = { creatingFolder = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixTextField(folderName, { folderName = it }, label = "文件夹名称", modifier = Modifier.fillMaxWidth())
                MiuixDialogButtons("新建", {
                    creatingFolder = false
                    val target = listOf(folderPath, folderName).filter(String::isNotBlank).joinToString("/")
                    operationScope.launch(Dispatchers.IO) {
                        IntermediateDataRepository.createBookmarkFolder(browser, target)
                    }
                }, { creatingFolder = false })
            }
        }
    }
    if (movingFolder && selectedFolder != null && browser != null) {
        var destinationParent by remember(selectedFolder) { mutableStateOf("") }
        WindowDialog(
            title = "移动文件夹",
            summary = "选择目标父文件夹；留空表示移动到收藏根目录。",
            show = true,
            onDismissRequest = { movingFolder = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixTextField(
                    destinationParent,
                    { destinationParent = it },
                    label = "目标父文件夹",
                    modifier = Modifier.fillMaxWidth(),
                )
                MiuixDialogButtons("移动", {
                    val source = selectedFolder ?: return@MiuixDialogButtons
                    operationScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            IntermediateDataRepository.moveBookmarkFolder(browser, source, destinationParent)
                        }
                        if (result is BookmarkFolderOperationResult.Completed) {
                            selectedFolder = null
                            movingFolder = false
                        }
                    }
                }, { movingFolder = false })
            }
        }
    }
    deletingFolder?.let { contents ->
        WindowDialog(
            title = "删除文件夹？",
            summary = "“${contents.path.substringAfterLast('/')}”包含 ${contents.recordCount} 条收藏和 " +
                "${(contents.folderCount - 1).coerceAtLeast(0)} 个子文件夹，删除后无法恢复。",
            show = true,
            onDismissRequest = { deletingFolder = null },
        ) {
            MiuixDialogButtons(
                confirmText = "删除",
                onConfirm = {
                    deletingFolder = null
                    selectedFolder = null
                    operationScope.launch(Dispatchers.IO) {
                        browser?.let { IntermediateDataRepository.deleteBookmarkFolder(it, contents.path, true) }
                    }
                },
                onDismiss = { deletingFolder = null },
                destructive = true,
            )
        }
    }
    historyPickerDate?.let { initialDate ->
        MiuixDatePickerBottomSheet(
            show = true,
            title = "跳转到日期",
            initialDateMillis = initialDate,
            maximumDateMillis = System.currentTimeMillis(),
            onDismissRequest = { historyPickerDate = null },
            onDateSelected = { dayStart ->
                historyPickerDate = null
                operationScope.launch {
                    historyOffset = withContext(Dispatchers.IO) {
                        IntermediateDataRepository.historyOffsetForDate(dayStart, search, browser)
                    }
                    historyJumpGeneration++
                    selected = emptySet()
                    limit = 100
                }
            },
        )
    }
}

@Composable
private fun MiuixDataManagerContent(
    kind: IntermediateItemKind,
    page: ManagedDataPage,
    search: String,
    onSearch: (String) -> Unit,
    loadMore: () -> Unit,
    edit: (ManagedDataRow) -> Unit,
    delete: (ManagedDataRow) -> Unit,
    selected: Set<IntermediateItemRef>,
    toggle: (IntermediateItemRef) -> Unit,
    selectGroup: (List<ManagedDataRow>) -> Unit,
    clearSelection: () -> Unit,
    deleteSelection: () -> Unit,
    moveSelection: () -> Unit,
    copySelection: () -> Unit,
    pasteSelection: () -> Unit,
    canPaste: Boolean,
    folders: List<BookmarkFolderEntry>,
    openFolder: (String) -> Unit,
    selectedFolder: String?,
    toggleFolder: (String) -> Unit,
    moveSelectedFolder: () -> Unit,
    deleteSelectedFolder: () -> Unit,
    requestHistoryDateJump: (Long) -> Unit,
    historyJumpGeneration: Int,
    folderMutationEnabled: Boolean,
    exportAllEnabled: Boolean,
    exportAll: () -> Unit,
    exportSelectionEnabled: Boolean,
    exportSelection: () -> Unit,
    pageTitle: String,
    createFolder: (() -> Unit)?,
    showCardUrls: Boolean,
    blurEnabled: Boolean,
    back: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberMiuixBlurBackdrop(blurEnabled)
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    LaunchedEffect(listState, page.hasMore, page.rows.size, page.folders.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val threshold = (info.totalItemsCount - 5).coerceAtLeast(0)
            page.hasMore && lastVisible >= threshold
        }.distinctUntilChanged().collect { shouldLoad ->
            if (shouldLoad) loadMore()
        }
    }
    LaunchedEffect(historyJumpGeneration) {
        if (historyJumpGeneration > 0 && page.rows.isNotEmpty()) {
            // Search and summary occupy the first item; the target date starts immediately after it.
            listState.scrollToItem(1 + page.folders.size)
        }
    }
    MiuixScaffold(topBar = {
        MiuixBlurredBar(backdrop) {
            MiuixTopAppBar(
                title = pageTitle,
                color = barColor,
                navigationIcon = { MiuixIconButton(back) {
                    MiuixIcon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                } },
                scrollBehavior = scrollBehavior,
            )
        }
    }) { padding ->
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                overscrollEffect = null,
            ) {
            item {
                MiuixTextField(
                    value = search,
                    onValueChange = onSearch,
                    label = "搜索${kind.title()}",
                    modifier = Modifier.fillMaxWidth(),
                )
                MiuixText("共 ${page.total} 条", Modifier.padding(horizontal = 8.dp, vertical = 10.dp))
            }
            items(folders, key = { "folder-${it.path}" }) { folder ->
                MiuixCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = MiuixCardDefaults.defaultColors(
                        color = if (folder.path == selectedFolder) {
                            MiuixTheme.colorScheme.primaryContainer
                        } else MiuixTheme.colorScheme.surfaceContainer,
                        contentColor = if (folder.path == selectedFolder) {
                            MiuixTheme.colorScheme.onPrimaryContainer
                        } else MiuixTheme.colorScheme.onSurfaceContainer,
                    ),
                    showIndication = true,
                    pressFeedbackType = PressFeedbackType.Sink,
                    onClick = {
                        if (selectedFolder != null || selected.isNotEmpty()) toggleFolder(folder.path)
                        else openFolder(folder.path)
                    },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        toggleFolder(folder.path)
                    },
                ) {
                    BasicComponent(
                        title = folder.name,
                        summary = "${folder.recordCount} 条收藏",
                        startAction = {
                            MiuixIcon(
                                if (folder.path == selectedFolder) MiuixIcons.Ok else MiuixIcons.Folder,
                                if (folder.path == selectedFolder) "已选择" else null,
                            )
                        },
                        endActions = { MiuixIcon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    )
                }
            }
            page.rows.groupBy { it.sectionLabel(kind) }.forEach { (section, rows) ->
                item(key = "section-$section") {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        if (kind == IntermediateItemKind.HISTORY) {
                            MiuixTextButton(section, {
                                rows.firstNotNullOfOrNull { it.timestampEpochMillis }
                                    ?.let(requestHistoryDateJump)
                            })
                        } else {
                            MiuixText(section, Modifier.padding(horizontal = 16.dp))
                        }
                        MiuixTextButton(
                            if (rows.all { it.ref in selected }) "取消全选" else "全选",
                            { selectGroup(rows) },
                        )
                    }
                }
                items(rows, key = { it.ref.rowId }) { row ->
                MiuixCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = MiuixCardDefaults.defaultColors(
                        color = if (row.ref in selected) {
                            MiuixTheme.colorScheme.primaryContainer
                        } else MiuixTheme.colorScheme.surfaceContainer,
                        contentColor = if (row.ref in selected) {
                            MiuixTheme.colorScheme.onPrimaryContainer
                        } else MiuixTheme.colorScheme.onSurfaceContainer,
                    ),
                    showIndication = true,
                    pressFeedbackType = PressFeedbackType.Sink,
                    onClick = {
                        if (selected.isNotEmpty() || selectedFolder != null) toggle(row.ref) else edit(row)
                    },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        toggle(row.ref)
                    },
                ) {
                    BasicComponent(
                        title = row.title.ifBlank { row.url },
                        summary = buildString {
                            if (showCardUrls) append(row.url)
                            if (row.detail.isNotBlank()) {
                                if (isNotEmpty()) append("\n")
                                append(row.detail)
                            }
                            if (isNotEmpty()) append("\n")
                            append("来源：").append(row.sources.sourceLabels())
                        },
                        startAction = {
                            MiuixIcon(
                                when {
                                    row.ref in selected -> MiuixIcons.Ok
                                    kind == IntermediateItemKind.BOOKMARK -> MiuixIcons.Favorites
                                    kind == IntermediateItemKind.HISTORY -> MiuixIcons.Recent
                                    else -> MiuixIcons.Layers
                                },
                                if (row.ref in selected) "已选择" else null,
                            )
                        },
                    )
                }
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.size(88.dp)) }
            }
            val hasSelection = selected.isNotEmpty() || selectedFolder != null
            if (hasSelection || canPaste || createFolder != null || exportAllEnabled) {
                FloatingToolbar(
                    modifier = Modifier.align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 12.dp, bottom = 10.dp),
                    showDivider = false,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        fun feedback(action: () -> Unit) {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            action()
                        }
                        val createFolderAction = createFolder
                        ContextToolbarItem(kind == IntermediateItemKind.BOOKMARK && createFolderAction != null && !hasSelection) {
                            MiuixIconButton(onClick = { createFolderAction?.let(::feedback) }, modifier = Modifier.size(44.dp)) {
                                MiuixIcon(MiuixIcons.AddFolder, "新建文件夹")
                            }
                        }
                        ContextToolbarItem(!hasSelection) {
                            MiuixIconButton(
                                onClick = { feedback(exportAll) },
                                modifier = Modifier.size(44.dp),
                                enabled = exportAllEnabled,
                            ) {
                                MiuixIcon(MiuixIcons.UploadCloud, "全部导出")
                            }
                        }
                        ContextToolbarItem(kind == IntermediateItemKind.BOOKMARK && selected.isNotEmpty()) {
                            MiuixIconButton(onClick = { feedback(moveSelection) }, modifier = Modifier.size(44.dp)) {
                                MiuixIcon(MiuixIcons.MoveFile, "移动")
                            }
                            MiuixIconButton(onClick = { feedback(copySelection) }, modifier = Modifier.size(44.dp)) {
                                MiuixIcon(MiuixIcons.Copy, "复制")
                            }
                        }
                        ContextToolbarItem(kind == IntermediateItemKind.BOOKMARK && selectedFolder != null && folderMutationEnabled) {
                            MiuixIconButton(onClick = { feedback(moveSelectedFolder) }, modifier = Modifier.size(44.dp)) {
                                MiuixIcon(MiuixIcons.MoveFile, "移动文件夹")
                            }
                        }
                        ContextToolbarItem(kind == IntermediateItemKind.BOOKMARK && canPaste && !hasSelection) {
                            MiuixIconButton(onClick = { feedback(pasteSelection) }, modifier = Modifier.size(44.dp)) {
                                MiuixIcon(MiuixIcons.Paste, "粘贴")
                            }
                        }
                        ContextToolbarItem(selected.isNotEmpty()) {
                            MiuixIconButton(onClick = { feedback(deleteSelection) }, modifier = Modifier.size(44.dp)) {
                                MiuixIcon(MiuixIcons.Delete, "删除")
                            }
                        }
                        ContextToolbarItem(selectedFolder != null && folderMutationEnabled) {
                            MiuixIconButton(onClick = { feedback(deleteSelectedFolder) }, modifier = Modifier.size(44.dp)) {
                                MiuixIcon(MiuixIcons.Delete, "删除文件夹")
                            }
                        }
                        ContextToolbarItem(hasSelection) {
                            MiuixIconButton(
                                onClick = { feedback(exportSelection) },
                                modifier = Modifier.size(44.dp),
                                enabled = exportSelectionEnabled,
                            ) {
                                MiuixIcon(MiuixIcons.UploadCloud, "导出所选")
                            }
                        }
                        ContextToolbarItem(hasSelection) {
                            MiuixIconButton(onClick = {
                                feedback {
                                    clearSelection()
                                    selectedFolder?.let(toggleFolder)
                                }
                            }, modifier = Modifier.size(44.dp)) {
                                MiuixIcon(MiuixIcons.Close, "取消选择")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextToolbarItem(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
        exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
    ) {
        content()
    }
}

@Composable
private fun MiuixDialogButtons(
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    MiuixDialogAdvancedMaterial()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MiuixTextButton(
            text = "取消",
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(20.dp))
        MiuixTextButton(
            text = confirmText,
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            colors = if (destructive) {
                MiuixButtonDefaults.textButtonColors(
                    color = MiuixTheme.colorScheme.error,
                    textColor = MiuixTheme.colorScheme.onError,
                )
            } else {
                MiuixButtonDefaults.textButtonColorsPrimary()
            },
        )
    }
}

@Composable
private fun EditRecordDialog(
    row: ManagedDataRow,
    kind: IntermediateItemKind,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onOpen: (String) -> Unit,
) {
    var title by remember(row.ref) { mutableStateOf(row.title) }
    var url by remember(row.ref) { mutableStateOf(row.url) }
    var detail by remember(row.ref) { mutableStateOf(row.detail) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑${kind.title()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = { onOpen(url) }) { Text("在浏览器中打开") }
                OutlinedTextField(title, { title = it }, label = { Text("标题") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("网址") }, singleLine = true)
                if (kind == IntermediateItemKind.BOOKMARK) {
                    OutlinedTextField(detail, { detail = it }, label = { Text("文件夹") }, singleLine = true)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, url, detail) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
)
}

@Composable
private fun MiuixEditRecordDialog(
    row: ManagedDataRow,
    kind: IntermediateItemKind,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onOpen: (String) -> Unit,
) {
    var title by remember(row.ref) { mutableStateOf(row.title) }
    var url by remember(row.ref) { mutableStateOf(row.url) }
    var detail by remember(row.ref) { mutableStateOf(row.detail) }
    WindowDialog(
        title = "编辑${kind.title()}",
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MiuixTextButton("在浏览器中打开", { onOpen(url) }, Modifier.fillMaxWidth())
            MiuixTextField(title, { title = it }, label = "标题", modifier = Modifier.fillMaxWidth())
            MiuixTextField(url, { url = it }, label = "网址", modifier = Modifier.fillMaxWidth())
            if (kind == IntermediateItemKind.BOOKMARK) {
                MiuixTextField(detail, { detail = it }, label = "文件夹", modifier = Modifier.fillMaxWidth())
            }
            MiuixDialogButtons("保存", { onSave(title, url, detail) }, onDismiss)
        }
    }
}

private fun IntermediateItemKind.title() = when (this) {
    IntermediateItemKind.BOOKMARK -> "收藏"
    IntermediateItemKind.HISTORY -> "历史记录"
    IntermediateItemKind.OPEN_TAB -> "标签页"
}

private fun IntermediateItemKind.newTaskKind() = when (this) {
    IntermediateItemKind.BOOKMARK -> NewTaskActivity.ImportKind.BOOKMARKS
    IntermediateItemKind.HISTORY -> NewTaskActivity.ImportKind.HISTORY
    IntermediateItemKind.OPEN_TAB -> NewTaskActivity.ImportKind.OPEN_TABS
}

/** Expands a selected folder to stable record IDs before crossing the Activity boundary. */
private fun collectBookmarkFolderRecordIds(folderPath: String, browser: BrowserId?): Set<Long> {
    val pending = ArrayDeque<String>().apply { add(folderPath) }
    val visited = hashSetOf<String>()
    val recordIds = linkedSetOf<Long>()
    while (pending.isNotEmpty()) {
        val folder = pending.removeFirst()
        if (!visited.add(folder)) continue
        var offset = 0
        do {
            val page = IntermediateDataRepository.queryBookmarkFolder(
                folderPath = folder,
                search = "",
                offset = offset,
                limit = 500,
                browser = browser,
            )
            recordIds += page.rows.map { it.ref.rowId }
            page.folders.mapTo(pending) { it.path }
            offset += page.rows.size
        } while (page.hasMore && page.rows.isNotEmpty())
    }
    return recordIds
}

private fun Set<BrowserId>.sourceLabels() = if (isEmpty()) "未知" else joinToString("、") {
    when (it) { BrowserId.VIA -> "Via"; BrowserId.EDGE -> "Edge" }
}

private fun BrowserId.browserLabel() = when (this) {
    BrowserId.VIA -> "Via"
    BrowserId.EDGE -> "Edge"
}

private fun ManagedDataRow.sectionLabel(kind: IntermediateItemKind): String = when (kind) {
    IntermediateItemKind.BOOKMARK -> "当前文件夹"
    IntermediateItemKind.HISTORY -> timestampEpochMillis?.let {
        SimpleDateFormat("yyyy年M月d日 EEEE", Locale.getDefault()).format(Date(it))
    } ?: "日期未知"
    IntermediateItemKind.OPEN_TAB -> "保存的标签页"
}

private fun String.pageTitle(kind: IntermediateItemKind): String = when {
    kind != IntermediateItemKind.BOOKMARK -> kind.title()
    isBlank() -> "收藏"
    else -> replace('\\', '/').trim('/').substringAfterLast('/')
}
