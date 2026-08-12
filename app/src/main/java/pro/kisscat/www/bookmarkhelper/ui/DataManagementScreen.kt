package pro.kisscat.www.bookmarkhelper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import pro.kisscat.www.bookmarkhelper.sync.model.IntermediateDataState
import pro.kisscat.www.bookmarkhelper.sync.model.IntermediateItemKind
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private data class ManagementDestination(
    val kind: IntermediateItemKind,
    val title: String,
    val summary: String,
    val count: Int,
    val icon: ImageVector,
)

@Composable
fun DataManagementMiuix(
    data: IntermediateDataState,
    actions: BookmarkHelperActions,
    modifier: Modifier = Modifier,
    topContentPadding: Dp = 0.dp,
) {
    val destinations = data.destinations()
    LazyColumn(
        modifier.fillMaxSize().scrollEndHaptic().overScrollVertical().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = topContentPadding),
        overscrollEffect = null,
    ) {
        item {
            top.yukonga.miuix.kmp.basic.Text(
                "整理浏览器数据，重复记录会自动合并并保留来源。",
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        destinations.forEach { destination ->
            item {
                MiuixCard(
                    onClick = { actions.openDataManager(destination.kind) },
                    showIndication = true,
                    pressFeedbackType = PressFeedbackType.Sink,
                ) {
                    BasicComponent(
                        title = destination.title,
                        summary = "${destination.count} 条 · ${destination.summary}",
                        startAction = { MiuixIcon(destination.icon, null) },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(132.dp)) }
    }
}

@Composable
fun DataManagementMaterial(
    data: IntermediateDataState,
    actions: BookmarkHelperActions,
    modifier: Modifier = Modifier,
) {
    val destinations = data.destinations()
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("浏览器数据", style = MaterialTheme.typography.titleLarge)
                    Text("统一整理来自不同浏览器的数据；重复记录会自动合并并保留来源。")
                }
            }
        }
        destinations.forEach { destination ->
            item {
                Card(
                    onClick = { actions.openDataManager(destination.kind) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ListItem(
                        headlineContent = { Text(destination.title) },
                        supportingContent = { Text("${destination.count} 条 · ${destination.summary}") },
                        leadingContent = { Icon(destination.icon, null) },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun IntermediateDataState.destinations() = listOf(
    ManagementDestination(IntermediateItemKind.BOOKMARK, "收藏", "管理标题、网址、文件夹和来源", bookmarkCount, MiuixIcons.Favorites),
    ManagementDestination(IntermediateItemKind.HISTORY, "历史记录", "按访问时间整理与搜索", historyCount, MiuixIcons.Recent),
    ManagementDestination(IntermediateItemKind.OPEN_TAB, "标签页", "管理已保存的打开页面", openTabCount, MiuixIcons.Layers),
)
