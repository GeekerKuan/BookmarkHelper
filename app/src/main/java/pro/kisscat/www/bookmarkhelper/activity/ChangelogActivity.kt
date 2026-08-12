@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pro.kisscat.www.bookmarkhelper.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import pro.kisscat.www.bookmarkhelper.ui.AppHapticProvider
import pro.kisscat.www.bookmarkhelper.ui.BookmarkHelperTheme
import pro.kisscat.www.bookmarkhelper.ui.MiuixBookmarkTheme
import pro.kisscat.www.bookmarkhelper.ui.ReleaseNote
import pro.kisscat.www.bookmarkhelper.ui.UiMode
import pro.kisscat.www.bookmarkhelper.ui.UiPreferences
import pro.kisscat.www.bookmarkhelper.ui.bookmarkHelperReleaseNotes
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.MiuixBlurredBar
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.rememberMiuixBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

class ChangelogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BindSystemBack(UiPreferences.predictiveBackEnabled(this))
            val uiMode = UiPreferences.uiMode(this)
            val systemDensity = LocalDensity.current
            val scaledDensity = Density(
                systemDensity.density * UiPreferences.pageScale(this),
                systemDensity.fontScale,
            )
            val content: @Composable () -> Unit = {
                if (uiMode == UiMode.MIUIX) {
                    MiuixChangelog(UiPreferences.blurEnabled(this), ::finishSystemPage)
                } else MaterialChangelog(::finishSystemPage)
            }
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                AppHapticProvider(UiPreferences.hapticsEnabled(this)) {
                    if (uiMode == UiMode.MIUIX) {
                        MiuixBookmarkTheme(
                            UiPreferences.themeMode(this),
                            UiPreferences.monetEnabled(this),
                            content,
                        )
                    } else BookmarkHelperTheme(
                        UiPreferences.themeMode(this),
                        UiPreferences.monetEnabled(this),
                        content,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiuixChangelog(blurEnabled: Boolean, back: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberMiuixBlurBackdrop(blurEnabled)
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    MiuixScaffold(topBar = {
        MiuixBlurredBar(backdrop) {
            MiuixTopAppBar(
                title = "版本更新日志",
                color = barColor,
                navigationIcon = {
                    MiuixIconButton(back) {
                        MiuixIcon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            LazyColumn(
                Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection).padding(horizontal = 12.dp),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                overscrollEffect = null,
            ) {
                items(bookmarkHelperReleaseNotes, key = { it.version }) { note ->
                    MiuixCard(Modifier.fillMaxWidth()) {
                        BasicComponent(
                            title = note.version,
                            summary = note.title + "\n" + note.changes.joinToString("\n") { "• $it" },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialChangelog(back: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("版本更新日志") },
                navigationIcon = {
                    IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(bookmarkHelperReleaseNotes, key = ReleaseNote::version) { note ->
                Card(Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(note.version) },
                        supportingContent = {
                            Text(note.title + "\n" + note.changes.joinToString("\n") { "• $it" })
                        },
                    )
                }
            }
        }
    }
}
