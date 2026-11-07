package io.github.sol01st.nobrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.sol01st.nobrowser.core.TargetApp
import io.github.sol01st.nobrowser.data.InstalledApps
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** PullToRefresh 默认文案是英文，这里给出简体中文四态。 */
private val RefreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新…", "刷新完成")

private val WarnColor = Color(0xFFE5484D)

private const val ScreenTitle = "不用内置浏览器打开"

@Composable
fun AppsScreen(
    apps: List<TargetApp>,
    installed: List<InstalledApps.Item>,
    defaultPkgs: Set<String>,
    moduleEnabled: Boolean?,
    installedLoaded: Boolean,
    listState: LazyListState,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onRemoveApp: (String) -> Unit,
    onBuiltInLongPress: () -> Unit,
    onAdd: () -> Unit,
) {
    val installedMap = installed.associateBy { it.pkg }
    val enabledCount = apps.count { it.enabled && it.packageName in installedMap }
    // 内置应用始终显示；用户添加的应用即使关闭接管也保留；未安装的排在已安装之后
    val visible = apps
        .filter { it.packageName in defaultPkgs || it.enabled || it.addedByUser }
        .sortedBy { if (it.packageName in installedMap) 0 else 1 }

    // 长按菜单：锚定到被长按的那一行
    var menuPkg by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MiuixTheme.colorScheme.background,
            topBar = {
                PageHeader(
                    title = ScreenTitle,
                    subtitle = if (installedLoaded) "已接管 $enabledCount 个应用" else "正在读取应用列表…",
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
            PullToRefresh(
                isRefreshing = refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
                refreshTexts = RefreshTexts,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 0.dp,
                        end = 0.dp,
                        top = padding.calculateTopPadding() + 4.dp,
                        bottom = listBottomPadding(AppBottomBarHeight + 96.dp),
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item { ModuleBanner(moduleEnabled) }
                    item { SmallTitle("已接管的应用") }
                    if (!installedLoaded) {
                        item {
                            AppText(
                                "正在读取应用列表…",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(start = 20.dp, top = 12.dp),
                            )
                        }
                    }
                    items(visible, key = { it.packageName }) { app ->
                        val inst = installedMap[app.packageName]
                        val isInstalled = inst != null
                        // 只有「用户手动添加」且「不在内置默认清单里」的应用才允许删除
                        val removable = app.addedByUser && app.packageName !in defaultPkgs
                        Box(Modifier.animateItem()) {
                            NxbCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                cornerRadius = 18.dp,
                                onClick = { if (isInstalled) onOpen(app.packageName) },
                                onLongClick = {
                                    if (removable) {
                                        menuPkg = app.packageName
                                    } else {
                                        onBuiltInLongPress()
                                    }
                                },
                            ) {
                                BasicComponent(
                                    title = inst?.label ?: app.appName,
                                    summary = if (isInstalled) app.packageName else "未安装",
                                    startAction = { AppIcon(app.packageName, size = 36.dp) },
                                    endActions = {
                                        Switch(
                                            checked = isInstalled && app.enabled,
                                            enabled = isInstalled,
                                            onCheckedChange = { if (isInstalled) onToggle(app.packageName, it) },
                                        )
                                    },
                                )
                            }

                            // 菜单锚定在这一行的左下角，天然贴合，不用算窗口坐标
                            if (menuPkg == app.packageName) {
                                Popup(
                                    alignment = Alignment.BottomStart,
                                    offset = IntOffset(24, 2),
                                    onDismissRequest = { menuPkg = null },
                                    properties = PopupProperties(focusable = true),
                                ) {
                                    // 外层留出 padding，否则 Popup 窗口会把阴影裁掉
                                    Box(Modifier.padding(16.dp)) {
                                        val menuShape = RoundedCornerShape(18.dp)
                                        Box(
                                            Modifier
                                                .width(186.dp)
                                                .shadow(
                                                    elevation = 20.dp,
                                                    shape = menuShape,
                                                    clip = false,
                                                    ambientColor = Color.Black,
                                                    spotColor = Color.Black,
                                                )
                                                .clip(menuShape)
                                                .background(MiuixTheme.colorScheme.surface)
                                                .border(
                                                    1.dp,
                                                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                                                    menuShape,
                                                ),
                                        ) {
                                            BasicComponent(
                                                title = "从首页移除",
                                                onClick = {
                                                    menuPkg = null
                                                    onRemoveApp(app.packageName)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }

                // 底栏是浮层，FAB 自己让开底栏高度 + 手势条
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = AppBottomBarHeight + 16.dp)
                        .navigationBarsPadding(),
                ) {
                    FloatingActionButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, contentDescription = "添加应用")
                    }
                }
            }
        }

    }
}

/** 顶部横幅：只显示 LSPosed 里本模块的启用状态。 */
@Composable
private fun ModuleBanner(enabled: Boolean?) {
    val ok = enabled == true
    val accent = if (ok) MiuixTheme.colorScheme.primary else WarnColor
    val title = when (enabled) {
        null -> "模块状态未知"
        true -> "模块已激活"
        false -> "模块未激活"
    }
    val desc = when (enabled) {
        null -> "读不到 LSPosed 配置，请在设置页确认 Root 权限后下拉刷新"
        true -> "LSPosed 已启用本模块，作用域内应用重启后生效"
        false -> "请在 LSPosed 中启用本模块"
    }
    GroupCard(modifier = Modifier.padding(bottom = 2.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Spacer(Modifier.width(8.dp))
                AppText(title, fontSize = 15.sp, color = accent)
            }
            AppText(
                desc,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
