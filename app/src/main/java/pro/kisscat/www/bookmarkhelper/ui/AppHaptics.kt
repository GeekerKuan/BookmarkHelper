package pro.kisscat.www.bookmarkhelper.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

private object DisabledHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

/** Makes one preference control every Compose and Miuix haptic in the subtree. */
@Composable
fun AppHapticProvider(enabled: Boolean, content: @Composable () -> Unit) {
    if (enabled) {
        content()
    } else {
        CompositionLocalProvider(LocalHapticFeedback provides DisabledHapticFeedback, content = content)
    }
}
