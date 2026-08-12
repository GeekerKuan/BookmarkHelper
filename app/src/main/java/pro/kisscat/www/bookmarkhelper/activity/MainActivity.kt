package pro.kisscat.www.bookmarkhelper.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.alibaba.fastjson.parser.ParserConfig
import pro.kisscat.www.bookmarkhelper.converter.support.ConverterMaster
import pro.kisscat.www.bookmarkhelper.entry.rule.Rule
import pro.kisscat.www.bookmarkhelper.exception.CrashHandler
import pro.kisscat.www.bookmarkhelper.sync.task.SyncTaskCoordinator
import pro.kisscat.www.bookmarkhelper.sync.environment.ActivationState
import pro.kisscat.www.bookmarkhelper.sync.environment.EnvironmentStatusProbe
import pro.kisscat.www.bookmarkhelper.sync.model.IntermediateDataRepository
import pro.kisscat.www.bookmarkhelper.ui.BookmarkHelperActions
import pro.kisscat.www.bookmarkhelper.ui.AppHapticProvider
import pro.kisscat.www.bookmarkhelper.ui.BookmarkHelperMiuixScreen
import pro.kisscat.www.bookmarkhelper.ui.BookmarkHelperUiState
import pro.kisscat.www.bookmarkhelper.ui.MainPage
import pro.kisscat.www.bookmarkhelper.ui.MiuixBookmarkTheme
import pro.kisscat.www.bookmarkhelper.ui.UiMode
import pro.kisscat.www.bookmarkhelper.ui.UiPreferences
import pro.kisscat.www.bookmarkhelper.util.appList.AppListUtil
import pro.kisscat.www.bookmarkhelper.util.context.ContextUtil
import pro.kisscat.www.bookmarkhelper.util.log.LogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    private lateinit var rule: Rule
    private var uiState by mutableStateOf(BookmarkHelperUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!initializeApplication()) return
        loadUiPreferences()
        refreshBrowserState()
        refreshEnvironmentState()
        SyncTaskCoordinator.configure(applicationContext, rule)

        setContent {
            val tasks by SyncTaskCoordinator.tasks.collectAsState()
            val intermediateData by IntermediateDataRepository.state.collectAsState()
            val executing = tasks.firstOrNull { it.isExecuting }
            val unfinished = tasks.filterNot { it.isFinished }
            val pendingConfirmation = unfinished.count { it.phase == pro.kisscat.www.bookmarkhelper.sync.task.SyncTaskPhase.PREVIEW }
            val state = uiState.copy(
                taskRunning = executing != null,
                taskSummary = executing?.detail ?: unfinished.firstOrNull()?.detail,
                taskCount = tasks.size,
                unfinishedTaskCount = unfinished.size,
                pendingConfirmationCount = pendingConfirmation,
                intermediateData = intermediateData,
            )
            if (state.selectedPage != MainPage.HOME) {
                BackHandler {
                    performTick()
                    uiState = uiState.copy(selectedPage = MainPage.HOME)
                }
            } else {
                BindSystemBack(state.predictiveBackEnabled)
            }
            val systemDensity = LocalDensity.current
            val scaledDensity = Density(
                density = systemDensity.density * state.pageScale,
                fontScale = systemDensity.fontScale,
            )
            val content = @androidx.compose.runtime.Composable {
                val actions = actions()
                BookmarkHelperMiuixScreen(state, actions)
            }
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                AppHapticProvider(state.hapticsEnabled) {
                    MiuixBookmarkTheme(
                        themeMode = state.themeMode,
                        monetEnabled = state.monetEnabled,
                        content = content,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::rule.isInitialized) {
            refreshUiPreferencesPreservingPage()
            refreshBrowserState()
        }
    }

    private fun actions() = BookmarkHelperActions(
        openBookmarksTask = { openTask(NewTaskActivity.ImportKind.BOOKMARKS) },
        openHistoryTask = { openTask(NewTaskActivity.ImportKind.HISTORY) },
        openTabsTask = { openTabsTask() },
        openBookmarksExportTask = {
            openTask(
                NewTaskActivity.ImportKind.BOOKMARKS,
                NewTaskActivity.MigrationDirection.EXPORT,
            )
        },
        openHistoryExportTask = {
            openTask(
                NewTaskActivity.ImportKind.HISTORY,
                NewTaskActivity.MigrationDirection.EXPORT,
            )
        },
        openBackupRestore = {
            launch(Intent(this, DataBackupRestoreActivity::class.java))
        },
        refreshRootEnvironment = ::refreshRootState,
        refreshLsposedEnvironment = ::refreshLsposedState,
        openRunningTask = ::showTask,
        openDataManager = { launch(DataManagerActivity.intent(this, it)) },
        openPersonalization = {
            launch(Intent(this, PersonalizationActivity::class.java))
        },
        dismissMessage = { uiState = uiState.copy(dialogMessage = null) },
        openAbout = { launch(Intent(this, AppInfoActivity::class.java)) },
        setThemeMode = ::setThemeMode,
        setGlassEnabled = ::setGlassEnabled,
        setMonetEnabled = ::setMonetEnabled,
        setBlurEnabled = ::setBlurEnabled,
        setFloatingBarEnabled = ::setFloatingBarEnabled,
        setPageScale = ::setPageScale,
        setUiMode = ::setUiMode,
        setStartupPage = ::setStartupPage,
        selectPage = {
            if (it != uiState.selectedPage) performTick()
            uiState = uiState.copy(selectedPage = it)
        },
    )

    private fun loadUiPreferences() {
        val startup = UiPreferences.startupPage(this)
        uiState = uiState.copy(
            uiMode = UiPreferences.uiMode(this),
            themeMode = UiPreferences.themeMode(this),
            glassEnabled = UiPreferences.glassEnabled(this),
            monetEnabled = UiPreferences.monetEnabled(this),
            blurEnabled = UiPreferences.blurEnabled(this),
            hapticsEnabled = UiPreferences.hapticsEnabled(this),
            predictiveBackEnabled = UiPreferences.predictiveBackEnabled(this),
            transitionsEnabled = UiPreferences.transitionsEnabled(this),
            floatingBarEnabled = UiPreferences.floatingBarEnabled(this),
            pageScale = UiPreferences.pageScale(this),
            startupPage = startup,
            selectedPage = startup,
        )
    }

    private fun refreshUiPreferencesPreservingPage() {
        uiState = uiState.copy(
            uiMode = UiPreferences.uiMode(this),
            themeMode = UiPreferences.themeMode(this),
            glassEnabled = UiPreferences.glassEnabled(this),
            monetEnabled = UiPreferences.monetEnabled(this),
            blurEnabled = UiPreferences.blurEnabled(this),
            hapticsEnabled = UiPreferences.hapticsEnabled(this),
            predictiveBackEnabled = UiPreferences.predictiveBackEnabled(this),
            transitionsEnabled = UiPreferences.transitionsEnabled(this),
            floatingBarEnabled = UiPreferences.floatingBarEnabled(this),
            pageScale = UiPreferences.pageScale(this),
            startupPage = UiPreferences.startupPage(this),
        )
    }

    private fun setUiMode(mode: UiMode) {
        UiPreferences.setUiMode(this, mode)
        uiState = uiState.copy(uiMode = mode)
    }

    private fun setThemeMode(mode: Int) {
        UiPreferences.setThemeMode(this, mode)
        uiState = uiState.copy(themeMode = mode.coerceIn(0, 2))
    }

    private fun setGlassEnabled(enabled: Boolean) {
        UiPreferences.setGlassEnabled(this, enabled)
        uiState = uiState.copy(glassEnabled = enabled)
    }

    private fun setMonetEnabled(enabled: Boolean) {
        UiPreferences.setMonetEnabled(this, enabled)
        uiState = uiState.copy(monetEnabled = enabled)
    }

    private fun setBlurEnabled(enabled: Boolean) {
        UiPreferences.setBlurEnabled(this, enabled)
        uiState = uiState.copy(blurEnabled = enabled)
    }

    private fun setFloatingBarEnabled(enabled: Boolean) {
        UiPreferences.setFloatingBarEnabled(this, enabled)
        uiState = uiState.copy(floatingBarEnabled = enabled)
    }

    private fun setPageScale(scale: Float) {
        UiPreferences.setPageScale(this, scale)
        uiState = uiState.copy(pageScale = scale.coerceIn(.85f, 1.15f))
    }

    private fun setStartupPage(page: MainPage) {
        UiPreferences.setStartupPage(this, page)
        uiState = uiState.copy(startupPage = page)
    }

    private fun initializeApplication(): Boolean = try {
        ParserConfig.getGlobalInstance().isSafeMode = true
        ContextUtil.init(applicationContext)
        CrashHandler.getInstance().init()
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler.getInstance())
        LogHelper.init()
        IntermediateDataRepository.initialize(applicationContext)
        ConverterMaster.init(this)
        rule = ConverterMaster.getSupportRule().first()
        true
    } catch (error: RuntimeException) {
        LogHelper.e("MainActivityInit", error)
        finish()
        false
    }

    private fun refreshBrowserState() {
        runCatching { AppListUtil.reInit(this) }.onFailure { LogHelper.e("AppListRefresh", it) }
        val source = rule.source
        val target = rule.target
        val viaInstalled = source.isInstalled(this, source)
        val edgeInstalled = target.isInstalled(this, target)
        if (viaInstalled) source.fillVersion(this)
        if (edgeInstalled) target.fillVersion(this)
        rule.isCanUse = viaInstalled && edgeInstalled
        uiState = uiState.copy(
            viaInstalled = viaInstalled,
            edgeInstalled = edgeInstalled,
            viaVersion = source.versionName.orEmpty(),
            edgeVersion = target.versionName.orEmpty(),
        )
    }

    private fun openTask(
        kind: NewTaskActivity.ImportKind,
        direction: NewTaskActivity.MigrationDirection = NewTaskActivity.MigrationDirection.IMPORT,
    ) {
        ensureTaskNotificationPermission()
        refreshBrowserState()
        openSystemPage(NewTaskActivity.intent(
            this,
            kind,
            direction,
        ))
    }

    private fun openTabsTask() {
        ensureTaskNotificationPermission()
        refreshBrowserState()
        openSystemPage(NewTaskActivity.intent(this, NewTaskActivity.ImportKind.OPEN_TABS))
    }

    private fun refreshEnvironmentState() {
        uiState = uiState.copy(
            rootState = ActivationState.CHECKING,
            lsposedState = ActivationState.CHECKING,
        )
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { EnvironmentStatusProbe.read() }
            uiState = uiState.copy(
                rootState = result.root,
                lsposedState = result.lsposed,
                rootManager = result.rootManager,
            )
        }
    }

    private fun refreshRootState() {
        uiState = uiState.copy(rootState = ActivationState.CHECKING, rootManager = "正在检测")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { EnvironmentStatusProbe.readRoot() }
            uiState = uiState.copy(rootState = result.first, rootManager = result.second)
        }
    }

    private fun refreshLsposedState() {
        uiState = uiState.copy(lsposedState = ActivationState.CHECKING)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                EnvironmentStatusProbe.readLsposed(uiState.rootState)
            }
            uiState = uiState.copy(lsposedState = result)
        }
    }

    private fun showTask() = openSystemPage(Intent(this, TaskCenterActivity::class.java))

    private fun launch(intent: Intent) {
        openSystemPage(intent)
    }

    private fun performTick() {
        if (UiPreferences.hapticsEnabled(this)) {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    private fun ensureTaskNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            UiPreferences.notificationPermissionAsked(this)
        ) return
        UiPreferences.setNotificationPermissionAsked(this, true)
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

}
