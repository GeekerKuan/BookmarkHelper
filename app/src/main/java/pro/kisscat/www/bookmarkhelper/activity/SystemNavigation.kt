package pro.kisscat.www.bookmarkhelper.activity

import android.content.Intent
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import pro.kisscat.www.bookmarkhelper.ui.UiPreferences

fun ComponentActivity.openSystemPage(intent: Intent) {
    if (!UiPreferences.transitionsEnabled(this)) intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
    startActivity(intent)
}

fun ComponentActivity.finishSystemPage() {
    finish()
    if (!UiPreferences.transitionsEnabled(this)) {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

/**
 * Miuix pages leave enabled Activity predictive back entirely to Android. Disabling the
 * preference installs only the classic immediate finish handler.
 */
@Composable
fun ComponentActivity.BindSystemBack(predictiveBackEnabled: Boolean) {
    BackHandler(enabled = Build.VERSION.SDK_INT >= 34 && !predictiveBackEnabled) {
        finishSystemPage()
    }
}

/**
 * Same NavigationEvent handler used by the Miuix example for returning a main pager to page 0.
 * It never transforms decorView, so it cannot fight the platform's window animation.
 */
@Composable
fun BindMiuixPagerBack(
    enabled: Boolean,
    predictiveBackEnabled: Boolean,
    onBackCompleted: () -> Unit,
) {
    BackHandler(enabled = enabled && !predictiveBackEnabled) {
        onBackCompleted()
    }
    val state = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = state,
        isBackEnabled = enabled && predictiveBackEnabled,
        onBackCompleted = onBackCompleted,
    )
}
