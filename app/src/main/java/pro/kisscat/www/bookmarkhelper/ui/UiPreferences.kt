package pro.kisscat.www.bookmarkhelper.ui

import android.content.Context

object UiPreferences {
    private const val FILE = "bookmarkhelper_ui"
    private const val UI_MODE = "ui_mode"
    private const val THEME_MODE = "theme_mode"
    private const val GLASS = "glass_enabled"
    private const val STARTUP_PAGE = "startup_page"
    private const val MONET = "miuix_monet"
    private const val BLUR = "enable_blur"
    private const val FLOATING_BAR = "enable_floating_bottom_bar"
    private const val PAGE_SCALE = "page_scale"
    private const val HAPTICS = "system_haptics"
    private const val TRANSITIONS = "system_transition_animations"
    private const val PREDICTIVE_BACK = "predictive_back"
    private const val PREDICTIVE_BACK_MAX_PROGRESS = "predictive_back_max_progress"
    private const val NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked"
    private const val SHOW_DATA_CARD_URLS = "show_data_card_urls"

    private fun preferences(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Material 3 was removed from the selectable product surface; migrate every old value. */
    fun uiMode(context: Context) = UiMode.MIUIX
    fun themeMode(context: Context) = preferences(context).getInt(THEME_MODE, 0).coerceIn(0, 2)
    fun glassEnabled(context: Context) = preferences(context).getBoolean(GLASS, true)
    fun startupPage(context: Context) = MainPage.from(preferences(context).getString(STARTUP_PAGE, null))
    fun monetEnabled(context: Context) = preferences(context).getBoolean(MONET, false)
    fun blurEnabled(context: Context) = preferences(context).getBoolean(BLUR, true)
    fun floatingBarEnabled(context: Context) = preferences(context).getBoolean(FLOATING_BAR, true)
    fun pageScale(context: Context) = preferences(context).getFloat(PAGE_SCALE, 1f).coerceIn(.85f, 1.15f)
    fun hapticsEnabled(context: Context) = preferences(context).getBoolean(HAPTICS, true)
    fun transitionsEnabled(context: Context) = preferences(context).getBoolean(TRANSITIONS, true)
    fun predictiveBackEnabled(context: Context) = preferences(context).getBoolean(PREDICTIVE_BACK, true)
    fun predictiveBackMaxProgress(context: Context) =
        preferences(context).getFloat(PREDICTIVE_BACK_MAX_PROGRESS, 1f).coerceIn(.25f, 1f)
    fun showDataCardUrls(context: Context) = preferences(context).getBoolean(SHOW_DATA_CARD_URLS, false)
    fun notificationPermissionAsked(context: Context) =
        preferences(context).getBoolean(NOTIFICATION_PERMISSION_ASKED, false)

    fun setUiMode(context: Context, value: UiMode) =
        preferences(context).edit().putString(UI_MODE, UiMode.MIUIX.storedValue).apply()
    fun setThemeMode(context: Context, value: Int) =
        preferences(context).edit().putInt(THEME_MODE, value.coerceIn(0, 2)).apply()
    fun setGlassEnabled(context: Context, value: Boolean) =
        preferences(context).edit().putBoolean(GLASS, value).apply()
    fun setStartupPage(context: Context, value: MainPage) =
        preferences(context).edit().putString(STARTUP_PAGE, value.storedValue).apply()
    fun setMonetEnabled(context: Context, value: Boolean) =
        preferences(context).edit().putBoolean(MONET, value).apply()
    fun setBlurEnabled(context: Context, value: Boolean) =
        preferences(context).edit().putBoolean(BLUR, value).apply()
    fun setFloatingBarEnabled(context: Context, value: Boolean) =
        preferences(context).edit().putBoolean(FLOATING_BAR, value).apply()
    fun setPageScale(context: Context, value: Float) =
        preferences(context).edit().putFloat(PAGE_SCALE, value.coerceIn(.85f, 1.15f)).apply()
    fun setHapticsEnabled(context: Context, value: Boolean) =
        preferences(context).edit().putBoolean(HAPTICS, value).apply()
    fun setTransitionsEnabled(context: Context, value: Boolean) =
        preferences(context).edit().putBoolean(TRANSITIONS, value).apply()
    fun setPredictiveBackEnabled(context: Context, value: Boolean) =
        preferences(context).edit().putBoolean(PREDICTIVE_BACK, value).apply()
    fun setPredictiveBackMaxProgress(context: Context, value: Float) = preferences(context).edit()
        .putFloat(PREDICTIVE_BACK_MAX_PROGRESS, value.coerceIn(.25f, 1f)).apply()
    fun setShowDataCardUrls(context: Context, value: Boolean) =
        preferences(context).edit().putBoolean(SHOW_DATA_CARD_URLS, value).apply()
    fun setNotificationPermissionAsked(context: Context, value: Boolean) =
        preferences(context).edit().putBoolean(NOTIFICATION_PERMISSION_ASKED, value).apply()
}
