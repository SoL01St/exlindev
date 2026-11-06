package io.github.sol01st.nobrowser.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sol01st.nobrowser.core.TargetApp
import io.github.sol01st.nobrowser.data.InstalledApps
import io.github.sol01st.nobrowser.data.RuleStore
import io.github.sol01st.nobrowser.data.ScopeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class Tab { Apps, Settings }

sealed interface Route {
    data object Root : Route
    data object Add : Route
    data object About : Route
    data class Edit(val pkg: String) : Route
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val store = remember { RuleStore(context) }
    val scope = rememberCoroutineScope()

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

    LaunchedEffect(Unit) {
        installed = withContext(Dispatchers.IO) { InstalledApps.load(context) }
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

    var refreshing by remember { mutableStateOf(false) }

    fun syncFromScope(silent: Boolean = false) {
        if (refreshing) return
        scope.launch {
            refreshing = true
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
            refreshing = false
        }
    }

    LaunchedEffect(installed) {
        if (installed.isEmpty()) return@LaunchedEffect
        syncFromScope(silent = true)
    }

    fun reloadApps() {
        scope.launch {
            installed = withContext(Dispatchers.IO) { InstalledApps.load(context) }
            syncFromScope(silent = true)
        }
    }

    fun syncScope(pkg: String, enabled: Boolean) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val r = ScopeManager.setScope(context, pkg, enabled)
                if (r && enabled) ScopeManager.restartPackage(pkg)
                r
            }
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

    fun addApp(pkg: String, label: String) {
        if (apps.any { it.packageName == pkg }) return
        val base = store.templateFor(pkg)
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

    Scaffold(
        bottomBar = {
            if (route is Route.Root) {
                BottomBar(tab) { tab = it }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    (fadeIn(tween(380, easing = LinearOutSlowInEasing)) togetherWith
                        fadeOut(tween(260, easing = FastOutSlowInEasing)))
                        .using(SizeTransform(clip = true))
                },
                label = "route",
            ) { current ->
                when (current) {
                    is Route.Root -> if (tab == Tab.Apps) {
                        AppsScreen(
                            apps = apps,
                            installed = installed,
                            defaultPkgs = remember { store.defaultApps().map { it.packageName }.toSet() },
                            refreshing = refreshing,
                            onRefresh = { reloadApps() },
                            onToggle = ::toggle,
                            onOpen = { route = Route.Edit(it) },
                            onAdd = { route = Route.Add },
                        )
                    } else {
                        SettingsScreen(
                            apps = apps,
                            store = store,
                            onOpenAbout = { route = Route.About },
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

                    is Route.About -> AboutScreen(onBack = { route = Route.Root })
                }
            }

            HintState.text?.let { message ->
                HintBar(message, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun HintBar(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .padding(start = 24.dp, end = 24.dp, bottom = 92.dp)
            .clip(shape)
            .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.92f))
            .border(1.dp, MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f), shape)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        AppText(text, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface, maxLines = 2)
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MiuixTheme.colorScheme.surface)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabItem("首页", Icons.Default.Home, current == Tab.Apps, Modifier.weight(1f)) { onSelect(Tab.Apps) }
        TabItem("设置", Icons.Default.Settings, current == Tab.Settings, Modifier.weight(1f)) { onSelect(Tab.Settings) }
    }
}

@Composable
private fun TabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = color)
        Spacer(Modifier.height(2.dp))
        AppText(label, fontSize = 11.sp, color = color, fontWeight = if (selected) FontWeight.Medium else null)
    }
}
