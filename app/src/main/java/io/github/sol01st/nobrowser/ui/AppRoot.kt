package io.github.sol01st.nobrowser.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.AnimatedContent
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.sol01st.nobrowser.core.TargetApp
import io.github.sol01st.nobrowser.data.InstalledApps
import io.github.sol01st.nobrowser.data.ModuleState
import io.github.sol01st.nobrowser.data.RuleStore
import io.github.sol01st.nobrowser.data.ScopeManager
import io.github.sol01st.nobrowser.data.XposedConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon

import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.Backdrop
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable

enum class Tab { Apps, Settings }

/** SaveableStateProvider 的 key：每个路由一个稳定字符串，返回时才能恢复页面状态。 */
private fun Route.saveKey(): String = when (this) {
    Route.Root -> "root"
    Route.Add -> "add"
    Route.About -> "about"
    Route.Logs -> "logs"
    Route.Backup -> "backup"
    is Route.Edit -> "edit:" + pkg
}

sealed interface Route {
    data object Root : Route
    data object Add : Route
    data object About : Route
    data class Edit(val pkg: String) : Route

    /** 模块日志页：只读展示 HookEngine 的判定日志。 */
    data object Logs : Route

    /** 备份与恢复页：导出 / 导入 / 立即备份 / 从备份恢复。 */
    data object Backup : Route
}

/** 路由层级，用于决定转场方向（进栈从右滑入，出栈从左滑入）。 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val store = remember { RuleStore(context) }
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf(store.load().toList()) }
    var tab by remember { mutableStateOf(Tab.Apps) }
    var route by remember { mutableStateOf<Route>(Route.Root) }

    var installed by remember { mutableStateOf<List<InstalledApps.Item>>(emptyList()) }
    var moduleEnabled by remember { mutableStateOf<Boolean?>(null) }
    var installedLoaded by remember { mutableStateOf(false) }
    // 提到这一层：进出二级页后返回时保留滚动位置
    val appsListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()
    // 路由切换时把每个页面的可保存状态（顶栏折叠量、列表位置、滚动位置…）存起来，
    // 返回时原样恢复。AnimatedContent 默认会直接销毁旧页面，rememberSaveable 也就没了。
    val routeStates = rememberSaveableStateHolder()

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
        moduleEnabled = withContext(Dispatchers.IO) { ModuleState.isEnabled() }
        installedLoaded = true
        // 内置规则刚升级过：重发配置文件，注入侧（微信等）才会读到新的规则与白名单
        if (store.consumeMigration()) {
            val json = store.exportJson(store.load())
            withContext(Dispatchers.IO) { ScopeManager.publishConfig(context, json) }
        }
    }

    // 框架的 binder 可能比界面晚到：连上后把状态补一遍，并顺手把配置推一次
    LaunchedEffect(Unit) {
        val listener: (io.github.libxposed.service.XposedService?) -> Unit = { svc ->
            if (svc != null) {
                scope.launch {
                    moduleEnabled = withContext(Dispatchers.IO) { ModuleState.isEnabled() }
                    val json = store.exportJson(apps)
                    withContext(Dispatchers.IO) { ScopeManager.publishConfig(context, json) }
                }
            } else {
                moduleEnabled = null
            }
        }
        XposedConnection.addListener(listener)
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            XposedConnection.removeListener(listener)
        }
    }

    fun persist(list: List<TargetApp>) {
        apps = list
        store.save(list)
        scope.launch {
            val json = store.exportJson(list)
            val ok = withContext(Dispatchers.IO) { ScopeManager.publishConfig(context, json) }
            if (!ok) {
                HintState.show("配置下发失败：LSPosed 未连接，且没有 root 权限")
            }
        }
    }

    fun markFirstTime(pkg: String) {
        val prefs = context.getSharedPreferences("nxb_notified", android.content.Context.MODE_PRIVATE)
        val notified = prefs.getStringSet("pkg", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (pkg !in notified) {
            notified.add(pkg)
            prefs.edit().putStringSet("pkg", notified).apply()
            HintState.show("已加入 LSPosed 作用域，重启该应用后接管才会生效")
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
                val remote = withContext(Dispatchers.IO) { ScopeManager.readScope() }
                val installedPkgs = installed.map { it.pkg }.toHashSet()
                when {
                    remote == null -> if (!silent) {
                        HintState.show("读取 LSPosed 作用域失败：未连接到框架，请先在 LSPosed 里启用本模块")
                    }
                    remote.isEmpty() -> {
                        val pkgs = apps.filter { it.enabled && it.packageName in installedPkgs }
                            .map { it.packageName }
                        val ok = withContext(Dispatchers.IO) {
                            ScopeManager.syncAll(pkgs) &&
                                ScopeManager.publishConfig(context, store.exportJson(apps))
                        }
                        if (!silent) {
                            HintState.show(
                                if (ok) "已把当前接管的应用写入 LSPosed" else "写入 LSPosed 作用域失败",
                            )
                        }
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
                moduleEnabled = withContext(Dispatchers.IO) { ModuleState.isEnabled() }
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
            val ok = withContext(Dispatchers.IO) { ScopeManager.setScope(pkg, enabled) }
            if (silent) return@launch
            val msg = if (ok) {
                // 作用域只对之后启动的进程生效，没有 root 就没法替用户杀掉目标应用
                if (enabled) "已接管并加入 LSPosed 作用域，重启该应用后生效" else "已取消接管，作用域已移除"
            } else {
                "作用域同步失败：请先在 LSPosed 里启用本模块"
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

    // 底栏玻璃的采样源：录制**整页**（含每页自己的顶栏），底栏与它自己不在录制范围内，
    // 避免玻璃把自身录进背景。不支持运行时着色器时退化为 null（底栏自动用不透明材质）。
    val shaderOk = remember { isRuntimeShaderSupported() }
    val pageBg = MiuixTheme.colorScheme.background
    val barBackdrop = if (shaderOk) {
        rememberLayerBackdrop {
            drawRect(pageBg)
            drawContent()
        }
    } else {
        null
    }

    // 玻璃对话框 / 玻璃弹层从这里取采样源：录的是整页内容（不含浮层自身）
    CompositionLocalProvider(LocalGlassBackdrop provides barBackdrop) {
    Box(Modifier.fillMaxSize()) {
    // 底栏是浮层，不占布局高度：一二级页可用高度完全一致，
    // 进出二级页时列表锚点不会被视口高度变化顶动。
    // 注意：外层不能再让状态栏，否则会和 Miuix TopAppBar 内置的
    // windowInsetsPadding(systemBars.only(Top)) 叠加，顶栏整体下移一个状态栏高度。
    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars
            .union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // 页面内容进录制层；底栏、FAB、提示条都留在这层外面（否则会被录进玻璃背景）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (barBackdrop != null) Modifier.layerBackdrop(barBackdrop) else Modifier),
            ) {
            AnimatedContent(
                targetState = route,
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
                // 页面内容抽成一个 composable lambda：根页套 SaveableStateProvider 保留状态，
                // 二级页直接调用，每次进入都是全新一页（见下面的 if 分支）。
                val page: @Composable () -> Unit = {
                when (current) {
                    is Route.Root -> Crossfade(
                        targetState = tab,
                        animationSpec = tween(220, easing = LinearOutSlowInEasing),
                        label = "tab",
                    ) { currentTab ->
                        routeStates.SaveableStateProvider("tab:" + currentTab.name) {
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
                            )
                        } else {
                            SettingsScreen(
                                store = store,
                                listState = settingsListState,
                                onOpenAbout = { route = Route.About },
                                onOpenLogs = { route = Route.Logs },
                                onOpenBackup = { route = Route.Backup },
                            )
                        }
                        }
                    }

                    is Route.Add -> AddAppScreen(
                        installed = installed,
                        existing = apps.map { it.packageName }.toSet(),
                        onBack = { route = Route.Root },
                        onReload = { reloadApps() },
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

                    is Route.Backup -> BackupScreen(
                        apps = apps,
                        store = store,
                        onBack = { route = Route.Root },
                        onRestore = { restored ->
                            persist(restored)
                            scope.launch {
                                val installedPkgs = installed.map { it.pkg }.toHashSet()
                                withContext(Dispatchers.IO) {
                                    ScopeManager.syncAll(
                                        restored.filter { it.enabled && it.packageName in installedPkgs }
                                            .map { it.packageName },
                                    )
                                }
                            }
                        },
                    )

                }
                }

                // 只有根页（首页 / 设置）保存状态：从二级页返回时列表位置、折叠标题都不丢。
                // 二级页（添加 / 编辑 / 关于 / 日志 / 备份）不经过 SaveableStateProvider，
                // 退出再进入就是全新一页，不会停在上次的滚动位置。
                if (current is Route.Root) {
                    routeStates.SaveableStateProvider(current.saveKey()) { page() }
                } else {
                    page()
                }
            }
            }

            // 首页底栏：0.9.4 的 FloatingNavigationBar，浮层压在内容上，不影响任何页面的可用高度
            if (route is Route.Root) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    BottomBar(
                        current = tab,
                        backdrop = barBackdrop,
                        onSelect = { tab = it },
                    )
                }
                // 加号：只出现在首页（模块列表页）右下角，浮在底栏之上
                if (tab == Tab.Apps) {
                    // 定位交给外层 Box：FAB 自己只拿一个干净的 Modifier（内部会 .size()）
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp)
                            .padding(bottom = bottomBarReservedHeight() + 12.dp),
                    ) {
                        AddAppFab(
                            onClick = {
                                tap()
                                route = Route.Add
                            },
                        )
                    }
                }
            }

            // 提示条：队列 / 时长 / 滑动消失交给官方 SnackbarHost
            SnackbarHost(
                state = HintState.host,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(2f)
                    // 首页底栏是浮层（胶囊 + 浮动留白约 120dp），提示条要整体落在它上方
                    .padding(bottom = if (route is Route.Root) bottomBarReservedHeight() + 76.dp else 0.dp),
            )
        }
    }


    }
    }
}

@Composable
private fun BottomBar(
    current: Tab,
    backdrop: Backdrop?,
    onSelect: (Tab) -> Unit,
) {
    val tap = rememberTapHaptics()
    // 官方玻璃悬浮导航条：胶囊自适应宽度居中，选中态是玻璃指示胶囊
    GlassNavigationBar(
        items = listOf(
            GlassNavigationItem(
                icon = Icons.Default.Home,
                label = "首页",
                contentDescription = "首页",
            ),
            GlassNavigationItem(
                icon = Icons.Default.Settings,
                label = "设置",
                contentDescription = "设置",
            ),
        ),
        selectedIndex = if (current == Tab.Apps) 0 else 1,
        onSelect = { index ->
            tap()
            onSelect(if (index == 0) Tab.Apps else Tab.Settings)
        },
        backdrop = backdrop,
        // GlassNavigationBar 内部强制 fillMaxWidth，用固定宽度保持原来紧凑胶囊的观感
        modifier = Modifier.width(220.dp),
    )
}

/** 底栏（玻璃胶囊 + 浮动留白）从屏幕底边算起占掉的高度，与 GlassNavigationBar 内部公式一致。 */
@Composable
private fun bottomBarReservedHeight(): Dp {
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val floatingMargin = when {
        nav <= 16.dp -> 28.dp
        nav >= 28.dp -> nav
        else -> 12.dp + nav
    }
    return GlassNavigationBarDefaults.Height + floatingMargin
}

/** 首页右下角的加号：KSU 模块页那种独立浮动按钮，不占底栏。 */
@Composable
private fun AddAppFab(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // 自绘圆钮：官方 FloatingActionButton 是 Surface，外面加 border 会被它的背景盖住，
    // 这里直接用 Box 画「底色 + 0.5dp 描边」，深色下才不会和同色的卡片融成一片。
    Box(
        modifier = modifier
            .size(60.dp)
            .shadow(4.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .border(0.5.dp, cardOutlineColor(), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = "添加应用",
            modifier = Modifier.size(26.dp),
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}
