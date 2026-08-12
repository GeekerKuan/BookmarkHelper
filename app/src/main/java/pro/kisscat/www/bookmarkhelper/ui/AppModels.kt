package pro.kisscat.www.bookmarkhelper.ui

import pro.kisscat.www.bookmarkhelper.sync.model.IntermediateDataState
import pro.kisscat.www.bookmarkhelper.sync.model.IntermediateItemKind
import pro.kisscat.www.bookmarkhelper.sync.environment.ActivationState

enum class UiMode(val storedValue: String) {
    MIUIX("miuix");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.storedValue == value } ?: MIUIX
    }
}

enum class MainPage(val index: Int, val storedValue: String) {
    HOME(0, "home"),
    TRANSFER(1, "transfer"),
    DATA(2, "data"),
    SETTINGS(3, "settings");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.storedValue == value } ?: HOME
        fun fromIndex(index: Int) = entries.firstOrNull { it.index == index } ?: HOME
    }
}

data class BookmarkHelperUiState(
    val viaInstalled: Boolean = false,
    val edgeInstalled: Boolean = false,
    val viaVersion: String = "",
    val edgeVersion: String = "",
    val rootState: ActivationState = ActivationState.CHECKING,
    val lsposedState: ActivationState = ActivationState.CHECKING,
    val rootManager: String = "正在检测",
    val busy: Boolean = false,
    val status: String? = null,
    val dialogMessage: String? = null,
    val themeMode: Int = 0,
    val glassEnabled: Boolean = true,
    val monetEnabled: Boolean = false,
    val blurEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val predictiveBackEnabled: Boolean = true,
    val transitionsEnabled: Boolean = true,
    val floatingBarEnabled: Boolean = true,
    val pageScale: Float = 1f,
    val uiMode: UiMode = UiMode.MIUIX,
    val startupPage: MainPage = MainPage.HOME,
    val selectedPage: MainPage = MainPage.HOME,
    val taskSummary: String? = null,
    val taskRunning: Boolean = false,
    val taskCount: Int = 0,
    val unfinishedTaskCount: Int = 0,
    val pendingConfirmationCount: Int = 0,
    val intermediateData: IntermediateDataState = IntermediateDataState(),
)

data class BookmarkHelperActions(
    val openBookmarksTask: () -> Unit,
    val openHistoryTask: () -> Unit,
    val openTabsTask: () -> Unit,
    val openBookmarksExportTask: () -> Unit,
    val openHistoryExportTask: () -> Unit,
    val openBackupRestore: () -> Unit,
    val refreshRootEnvironment: () -> Unit,
    val refreshLsposedEnvironment: () -> Unit,
    val openRunningTask: () -> Unit,
    val openDataManager: (IntermediateItemKind) -> Unit,
    val openPersonalization: () -> Unit,
    val dismissMessage: () -> Unit,
    val openAbout: () -> Unit,
    val setThemeMode: (Int) -> Unit,
    val setGlassEnabled: (Boolean) -> Unit,
    val setMonetEnabled: (Boolean) -> Unit,
    val setBlurEnabled: (Boolean) -> Unit,
    val setFloatingBarEnabled: (Boolean) -> Unit,
    val setPageScale: (Float) -> Unit,
    val setUiMode: (UiMode) -> Unit,
    val setStartupPage: (MainPage) -> Unit,
    val selectPage: (MainPage) -> Unit,
)
