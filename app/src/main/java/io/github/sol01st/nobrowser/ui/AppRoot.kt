package io.github.sol01st.nobrowser.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.sol01st.nobrowser.core.TargetApp
import io.github.sol01st.nobrowser.data.InstalledApps
import io.github.sol01st.nobrowser.data.ModuleState
import io.github.sol01st.nobrowser.data.RuleStore
import io.github.sol01st.nobrowser.data.ScopeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class Tab { Apps, Settings }

sealed interface Route {
    data object Root : Route
    data object Add : Route
    data object About : Route
    data class Edit(val pkg: String) : Route

    /** 模块日志页：只读展示 HookEngine 的判定日志。 */
    data object Logs : Route
}

/** 路由层级，用于决定转场方向（进栈从右滑入，出栈从左滑入）。 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val store = remember { RuleStore(context) }
    val scope = rememberCoroutineScope()
    // 提示条毛玻璃的采样源：整个内容层是背景，提示条自身不参与采样
    val hazeState = rememberHazeState()

    var apps by remember { mutableStateOf(store.load().toList()) }
    var tab by remember { mutableStateOf(Tab.Apps) }
    var route by remember { mutableStateOf<Route>(Route.Root) }

    LaunchedEffect(HintState.token) {
        if (HintState.text != null) {
            kotlinx.coroutines.delay(2400)
            HintState.clear()
        }
    }

    var installed by remember { mutableStateOf<List<InstalledApps.Item>>(emptyList()) }
    var moduleEnabled by remember { mutableStateOf<Boolean?>(null) }
    var installedLoaded by remember { mutableStateOf(false) }
    // 提到这一层：进出二级页后返回时保留滚动位置
    val appsListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()

    // 切换 Tab 时回到顶部（保留"进出二级页返回原位"的行为，但不让 Tab 之间共享滚动位置）
    LaunchedEffect(tab) {
        if (tab == Tab.Apps) {
            appsListState.scrollToItem(0)
        } else {
            settingsListState.scrollToItem(0)
        }
    }
    val tap = rememberTapHaptics()

    LaunchedEffect(Unit) {
        installed = withContext(Dispatchers.IO) { InstalledApps.load(context) }
        moduleEnabled = withContext(Dispatchers.IO) { ModuleState.isEnabled(context) }
        installedLoaded = true
        // 内置规则刚升级过：重发配置文件，注入侧（微信等）才会读到新的规则与白名单
        if (store.consumeMigration()) {
            val json = store.exportJson(store.load())
            withContext(Dispatchers.IO) { ScopeManager.publishConfig(context, json) }
        }
    }

    fun persist(list: List<TargetApp>) {
        apps = list
        store.save(list)
        scope.launch {
            val json = store.exportJson(list)
            val ok = withContext(Dispatchers.IO) { ScopeManager.publishConfig(context, json) }
            if (!ok) {
                HintState.show("配置写入失败：请在 KernelSU 里允许本应用使用 root")
            }
        }
    }

    fun markFirstTime(pkg: String) {
        val prefs = context.getSharedPreferences("nxb_notified", android.content.Context.MODE_PRIVATE)
        val notified = prefs.getStringSet("pkg", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (pkg !in notified) {
            notified.add(pkg)
            prefs.edit().putStringSet("pkg", notified).apply()
            HintState.show("首次接管的应用需在 LSPosed 作用域里重新勾选一次")
        }
    }

    // refreshing 只驱动下拉刷新动画；root 同步的状态单独用一个不驱动 UI 的标志，
    // 这样未授权 root 时（su 会卡在授权上）也不会把首页顶在刷新态。
    var refreshing by remember { mutableStateOf(false) }
    var syncingScope by remember { mutableStateOf(false) }

    fun syncFromScope(silent: Boolean = false) {
        if (syncingScope) return
        scope.launch {
            syncingScope = true
            try {
                val remote = withContext(Dispatchers.IO) { ScopeManager.readScope(context) }
                val installedPkgs = installed.map { it.pkg }.toHashSet()
                when {
                    remote == null -> if (!silent) HintState.show("读取 LSPosed 作用域失败，请确认已授予 root")
                    remote.isEmpty() -> {
                        val pkgs = apps.filter { it.enabled && it.packageName in installedPkgs }
                            .map { it.packageName }
                        withContext(Dispatchers.IO) {
                            ScopeManager.syncAll(context, pkgs)
                            ScopeManager.publishConfig(context, store.exportJson(apps))
                        }
                        if (!silent) HintState.show("已把当前接管的应用写入 LSPosed")
                    }
                    else -> {
                        val updated = apps.map { app ->
                            if (app.packageName in installedPkgs) {
                                app.copy(enabled = app.packageName in remote)
                            } else {
                                app
                            }
                        }
                        persist(updated)
                        if (!silent) HintState.show("已同步 LSPosed 作用域（${remote.size} 个应用）")
                    }
                }
                moduleEnabled = withContext(Dispatchers.IO) { ModuleState.isEnabled(context) }
            } catch (e: Exception) {
                android.util.Log.w("ExLinkNoBrowser", "syncFromScope failed", e)
            } finally {
                // 无论成功、失败还是异常都要复位，不然刷新功能会永久失效
                syncingScope = false
            }
        }
    }

    LaunchedEffect(installed) {
        if (installed.isEmpty()) return@LaunchedEffect
        syncFromScope(silent = true)
    }

    /**
     * 下拉刷新只做「重扫已安装应用」这一件快事，root 同步（可能很慢甚至卡在授权上）
     * 丢到后台跑，不参与 PullToRefresh 的状态，避免无 root 时首页被卡住。
     */
    fun reloadApps() {
        scope.launch {
            refreshing = true
            try {
                val started = System.currentTimeMillis()
                installed = withContext(Dispatchers.IO) { InstalledApps.load(context) }
                installedLoaded = true
                // 让刷新动画至少转一下，否则一闪而过像是没反应
                val elapsed = System.currentTimeMillis() - started
                if (elapsed < 400) kotlinx.coroutines.delay(400 - elapsed)
            } catch (e: Exception) {
                android.util.Log.w("ExLinkNoBrowser", "reloadApps failed", e)
            } finally {
                refreshing = false
            }
            syncFromScope(silent = true)
        }
    }

    fun syncScope(pkg: String, enabled: Boolean, silent: Boolean = false) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val r = ScopeManager.setScope(context, pkg, enabled)
                if (r && enabled) ScopeManager.restartPackage(pkg)
                r
            }
            if (silent) return@launch
            val msg = if (ok) {
                if (enabled) "已接管，并写入 LSPosed 作用域" else "已取消接管，作用域已移除"
            } else {
                "作用域同步失败：请确认已授予 root 权限"
            }
            HintState.show(msg)
        }
    }

    fun toggle(pkg: String, enabled: Boolean) {
        persist(apps.map { if (it.packageName == pkg) it.copy(enabled = enabled) else it })
        syncScope(pkg, enabled)
    }

    /** 从首页移除：保留规则到用户模板，取消接管，应用回到「添加应用」列表。 */
    fun removeApp(pkg: String) {
        val target = apps.firstOrNull { it.packageName == pkg } ?: return
        val wasEnabled = target.enabled
        store.saveUserTemplate(target)                       // 规则保留
        persist(apps.filterNot { it.packageName == pkg })
        if (wasEnabled) {
            // 已接管：一并取消作用域，并给一条明确的提示
            syncScope(pkg, false, silent = true)
            HintState.show("已移除并取消接管，规则已保留")
        }
        // 未接管：作用域在关闭开关时已清掉，这里不再同步、也不提示
    }

    fun addApp(pkg: String, label: String) {
        if (apps.any { it.packageName == pkg }) return
        val base = store.userTemplate(pkg)
            ?: store.templateFor(pkg)
            ?: TargetApp(appName = label, packageName = pkg, builtIn = false, enabled = false)
        persist(
            apps + base.copy(
                appName = if (base.appName.isBlank()) label else base.appName,
                enabled = true,
                frequent = false,
                addedByUser = true,
            ),
        )
        syncScope(pkg, true)
    }

    Box(Modifier.fillMaxSize()) {
    // 底栏是浮层，不占布局高度：一二级页可用高度完全一致，
    // 进出二级页时列表锚点不会被视口高度变化顶动。
    Scaffold(containerColor = MiuixTheme.colorScheme.background) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = route,
                modifier = Modifier.hazeSource(hazeState),
                transitionSpec = {
                    // 横滑推进：进二级页时新页从右侧推入，返回时反向推出
                    val forward = initialState is Route.Root
                    if (forward) {
                        (
                            slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { it } +
                                fadeIn(tween(180))
                            ) togetherWith (
                            slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 6 } +
                                fadeOut(tween(160))
                            )
                    } else {
                        (
                            slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 6 } +
                                fadeIn(tween(180))
                            ) togetherWith (
                            slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it } +
                                fadeOut(tween(160))
                            )
                    }
                },
                label = "route",
            ) { current ->
                when (current) {
                    is Route.Root -> Crossfade(
                        targetState = tab,
                        animationSpec = tween(220, easing = LinearOutSlowInEasing),
                        label = "tab",
                    ) { currentTab ->
                        if (currentTab == Tab.Apps) {
                            AppsScreen(
                                apps = apps,
                                installed = installed,
                                defaultPkgs = remember { store.defaultApps().map { it.packageName }.toSet() },
                                moduleEnabled = moduleEnabled,
                                installedLoaded = installedLoaded,
                                listState = appsListState,
                                refreshing = refreshing,
                                onRefresh = { reloadApps() },
                                onToggle = { pkg, enabled ->
                                    tap()
                                    toggle(pkg, enabled)
                                },
                                onOpen = { route = Route.Edit(it) },
                                onRemoveApp = { pkg -> removeApp(pkg) },
                                onBuiltInLongPress = {
                                    HintState.show("内置应用不能删除，用开关关闭接管即可")
                                },
                                onAdd = {
                                    tap()
                                    route = Route.Add
                                },
                                hazeState = hazeState,
                            )
                        } else {
                            SettingsScreen(
                                apps = apps,
                                store = store,
                                moduleEnabled = moduleEnabled,
                                listState = settingsListState,
                                onOpenAbout = { route = Route.About },
                                onOpenLogs = { route = Route.Logs },
                                onRestore = { restored ->
                                    persist(restored)
                                    scope.launch {
                                        val installedPkgs = installed.map { it.pkg }.toHashSet()
                                        withContext(Dispatchers.IO) {
                                            ScopeManager.syncAll(
                                                context,
                                                restored.filter { it.enabled && it.packageName in installedPkgs }
                                                    .map { it.packageName },
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }

                    is Route.Add -> AddAppScreen(
                        installed = installed,
                        existing = apps.map { it.packageName }.toSet(),
                        onBack = { route = Route.Root },
                        onPick = { label, pkg ->
                            addApp(pkg, label)
                            route = Route.Root
                        },
                    )

                    is Route.Edit -> {
                        val app = apps.firstOrNull { it.packageName == current.pkg }
                        if (app == null) {
                            route = Route.Root
                        } else {
                            EditScreen(
                                app = app,
                                onBack = { route = Route.Root },
                                onSave = { changed ->
                                    val previous = apps.firstOrNull { it.packageName == changed.packageName }
                                    persist(apps.map { if (it.packageName == changed.packageName) changed else it })
                                    if (previous != null && previous.enabled != changed.enabled) {
                                        syncScope(changed.packageName, changed.enabled)
                                    } else if (previous != null && previous.askEachTime != changed.askEachTime) {
                                        HintState.show("已保存，开关实时生效")
                                    }
                                },
                            )
                        }
                    }

                    is Route.Logs -> ModuleLogScreen(onBack = { route = Route.Root })

                    is Route.About -> AboutScreen(onBack = { route = Route.Root })

                }
            }


            HintState.text?.let { message ->
                HintBar(
                    text = message,
                    hazeState = hazeState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // 底栏叠在内容之上，提示条要让开它的高度
                        .padding(bottom = if (route is Route.Root) AppBottomBarHeight else 0.dp),
                )
            }
        }
    }

        // 首页底栏：浮层形式压在内容上，不影响任何页面的可用高度
        if (route is Route.Root) {
            BottomBar(
                current = tab,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(1f),
                onSelect = { tab = it },
                onAdd = { route = Route.Add },
                hazeState = hazeState,
            )
        }
    }
}

@Composable
private fun HintBar(
    text: String,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val surface = MiuixTheme.colorScheme.surface
    val tint = HazeTint(surface.copy(alpha = 0.45f))
    Box(
        modifier
            .padding(start = 28.dp, end = 28.dp, bottom = 24.dp)
            .clip(shape)
            // 真毛玻璃：采样下面那层内容再模糊，底下的字被糊掉，就不会和提示文字叠在一起
            .hazeEffect(state = hazeState) {
                // 0.4 + 0.4 ≈ 0.8 的白，配上 30dp 模糊：底下的字被糊掉又不会觉得提示条是块死白
                blurRadius = 30.dp
                backgroundColor = surface.copy(alpha = 0.40f)
                tints = listOf(HazeTint(surface.copy(alpha = 0.40f)))
                noiseFactor = 0.02f
            }
            .border(0.5.dp, MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f), shape)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        AppText(text, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface, maxLines = 2)
    }
}

@Composable
private fun BottomBar(
    current: Tab,
    modifier: Modifier = Modifier,
    onSelect: (Tab) -> Unit,
    onAdd: () -> Unit,
    hazeState: HazeState,
) {
    val surface = MiuixTheme.colorScheme.surface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(64.dp)
                .shadow(2.dp, RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .hazeEffect(state = hazeState) {
                    blurRadius = 24.dp
                    backgroundColor = surface.copy(alpha = 0.45f)
                    tints = listOf(HazeTint(surface.copy(alpha = 0.45f)))
                    noiseFactor = 0.02f
                }
                .border(0.5.dp, MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(32.dp)),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem("首页", Icons.Default.Home, current == Tab.Apps) { onSelect(Tab.Apps) }
            TabItem("设置", Icons.Default.Settings, current == Tab.Settings) { onSelect(Tab.Settings) }
        }
        Box(
            Modifier
                .size(64.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .hazeEffect(state = hazeState) {
                    blurRadius = 24.dp
                    backgroundColor = surface.copy(alpha = 0.45f)
                    tints = listOf(HazeTint(surface.copy(alpha = 0.45f)))
                    noiseFactor = 0.02f
                }
                .border(0.5.dp, MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape)
                .clickable { onAdd() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "添加应用",
                tint = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = if (selected) {
        MiuixTheme.colorScheme.onSurface
    } else {
        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        AppText(label, fontSize = 11.sp, color = color)
    }
}
