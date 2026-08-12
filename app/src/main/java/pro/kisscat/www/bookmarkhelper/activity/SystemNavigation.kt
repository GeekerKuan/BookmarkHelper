package pro.kisscat.www.bookmarkhelper.activity

import android.content.Intent
import android.os.Build
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
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
fun ComponentActivity.BindSystemBack(
    predictiveBackEnabled: Boolean,
    maximumProgress: Float = UiPreferences.predictiveBackMaxProgress(this),
) {
    BackHandler(enabled = Build.VERSION.SDK_INT >= 34 && !predictiveBackEnabled) {
        finishSystemPage()
    }
    PredictiveBackHandler(enabled = Build.VERSION.SDK_INT >= 34 && predictiveBackEnabled) { events ->
        val target = window.decorView
        val strength = maximumProgress.coerceIn(.25f, 1f)
        var committed = false
        try {
            events.collect { event ->
                val progress = event.progress.coerceIn(0f, 1f) * strength
                val direction = if (event.swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                target.pivotX = if (direction > 0f) 0f else target.width.toFloat()
                target.pivotY = target.height / 2f
                target.translationX = direction * target.width * .055f * progress
                target.scaleX = 1f - .055f * progress
                target.scaleY = 1f - .055f * progress
                target.alpha = 1f - .08f * progress
            }
            committed = true
            finishSystemPage()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            if (!committed) {
                target.animate().cancel()
                target.animate()
                    .translationX(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(160L)
                    .start()
            }
        }
    }
}

/** Predictive back for an in-Activity hierarchy, such as returning a main tab to Home. */
@Composable
fun ComponentActivity.BindInternalBack(
    predictiveBackEnabled: Boolean,
    maximumProgress: Float,
    onBack: () -> Unit,
) {
    BackHandler(enabled = Build.VERSION.SDK_INT < 34 || !predictiveBackEnabled, onBack = onBack)
    PredictiveBackHandler(enabled = Build.VERSION.SDK_INT >= 34 && predictiveBackEnabled) { events ->
        val target = window.decorView
        val strength = maximumProgress.coerceIn(.25f, 1f)
        try {
            events.collect { event ->
                val progress = event.progress.coerceIn(0f, 1f) * strength
                val direction = if (event.swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                target.pivotX = if (direction > 0f) 0f else target.width.toFloat()
                target.pivotY = target.height / 2f
                target.translationX = direction * target.width * .055f * progress
                target.scaleX = 1f - .055f * progress
                target.scaleY = 1f - .055f * progress
                target.alpha = 1f - .08f * progress
            }
            onBack()
        } finally {
            target.animate().cancel()
            target.animate()
                .translationX(0f)
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(160L)
                .start()
        }
    }
}
