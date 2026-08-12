/* Theme integration adapted from KernelSU Style UI Kit, GPL-3.0. */
package pro.kisscat.www.bookmarkhelper.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun MiuixBookmarkTheme(themeMode: Int, monetEnabled: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> systemDark
    }
    val controller = remember(dark, monetEnabled, themeMode) {
        ThemeController(
            colorSchemeMode = if (monetEnabled) {
                when (themeMode) {
                    1 -> ColorSchemeMode.MonetLight
                    2 -> ColorSchemeMode.MonetDark
                    else -> ColorSchemeMode.MonetSystem
                }
            } else if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light,
            isDark = dark,
        )
    }
    MiuixTheme(controller = controller) {
        LaunchedEffect(dark) {
            val window = (context as? Activity)?.window ?: return@LaunchedEffect
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
        CompositionLocalProvider(LocalContentColor provides MiuixTheme.colorScheme.onBackground) {
            content()
        }
    }
}
