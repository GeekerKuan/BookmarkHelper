package pro.kisscat.www.bookmarkhelper.ui.component.miuix

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import pro.kisscat.www.bookmarkhelper.ui.UiPreferences

/** Uses Android's native cross-window Gaussian blur behind Miuix window dialogs. */
@Composable
fun MiuixDialogAdvancedMaterial() {
    val context = LocalContext.current
    val view = LocalView.current
    val enabled = UiPreferences.blurEnabled(context)
    DisposableEffect(view, enabled) {
        if (Build.VERSION.SDK_INT < 31 || !enabled) return@DisposableEffect onDispose { }
        val window = (view.parent as? DialogWindowProvider)?.window
            ?: return@DisposableEffect onDispose { }
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (!windowManager.isCrossWindowBlurEnabled) return@DisposableEffect onDispose { }

        val previousFlags = window.attributes.flags
        val previousBlurBehindRadius = window.attributes.blurBehindRadius
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.apply { blurBehindRadius = 48 }
        window.setBackgroundBlurRadius(36)

        onDispose {
            window.setBackgroundBlurRadius(0)
            window.attributes = window.attributes.apply {
                flags = previousFlags
                blurBehindRadius = previousBlurBehindRadius
            }
        }
    }
}
