package pro.kisscat.www.bookmarkhelper.activity

import android.content.Intent
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import pro.kisscat.www.bookmarkhelper.ui.UiPreferences

/** Uses the device/ROM Activity transition unchanged; only supports explicitly disabling it. */
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
 * Enabled: leave back dispatch to Android so the system can render predictive back.
 * Disabled: consume back with the classic immediate Activity finish path.
 */
@Composable
fun ComponentActivity.BindSystemBack(predictiveBackEnabled: Boolean) {
    BackHandler(enabled = Build.VERSION.SDK_INT >= 34 && !predictiveBackEnabled) {
        finishSystemPage()
    }
}
