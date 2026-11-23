package io.github.sol01st.nobrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.sol01st.nobrowser.BuildConfig
import io.github.sol01st.nobrowser.core.TargetApp
import io.github.sol01st.nobrowser.data.KeepAlive
import io.github.sol01st.nobrowser.data.LauncherIconManager
import io.github.sol01st.nobrowser.data.RuleStore
import io.github.sol01st.nobrowser.data.ThemePrefs
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 沿 ContextWrapper 逐层解包到 Activity，普通 `as? Activity` 在主题包装下会拿不到。 */
private fun Context.findActivity(): androidx.activity.ComponentActivity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is androidx.activity.ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun themeTitle(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色模式"
    ThemeMode.DARK -> "深色模式"
}

@Composable
fun SettingsScreen(
    store: RuleStore,
    listState: LazyListState,
    onOpenAbout: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    val context = LocalContext.current

    fun toast(msg: String) = HintState.show(msg)

    // 桌面图标开关的当前状态直接从 PackageManager 读，不额外存 pref，避免和系统不同步
    var launcherVisible by remember { mutableStateOf(LauncherIconManager.isVisible(context)) }
    val backupCount = remember { store.backups().size }

    // 保活两项都是「系统状态」而不是本地开关：点开关只是跳系统页，回到前台再读真实值。
    // 两个坑：
    //  1. HyperOS 写 doze 白名单是异步的，刚 resume 读到的往往还是旧值 → 600ms 后再读一次；
    //  2. 从系统页返回有时只走 ON_START（分屏 / 多窗口），所以两个事件都刷。
    var ignoringBattery by remember {
        mutableStateOf(KeepAlive.ignoringBatteryOptimizations(context))
    }
    var autoStart by remember { mutableStateOf(KeepAlive.autoStartEnabled(context)) }

    // LocalContext 可能被主题包了一层 ContextWrapper，逐层解包再注册生命周期监听
    val hostActivity = remember(context) { context.findActivity() }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    DisposableEffect(hostActivity) {
        val refresh = Runnable {
            ignoringBattery = KeepAlive.ignoringBatteryOptimizations(context)
            autoStart = KeepAlive.autoStartEnabled(context)
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME ||
                event == androidx.lifecycle.Lifecycle.Event.ON_START
            ) {
                refresh.run()
                mainHandler.removeCallbacksAndMessages(null)
                mainHandler.postDelayed(refresh, 600)
            }
        }
        hostActivity?.lifecycle?.addObserver(observer)
        onDispose {
            hostActivity?.lifecycle?.removeObserver(observer)
            mainHandler.removeCallbacksAndMessages(null)
        }
    }

    // 和首页一致：官方 TopAppBar + MiuixScrollBehavior，大标题上滑折叠成中间小标题
    val scrollBehavior = MiuixScrollBehavior()
    NxbPageScaffold(
        title = "设置",
        largeTitle = "设置",
        scrollBehavior = scrollBehavior,
    ) { barHeight ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 0.dp,
                end = 0.dp,
                top = barHeight + 10.dp,
                // 底栏是浮层，不参与 Scaffold padding：这里显式让开底栏高度
                bottom = listBottomPadding(100.dp),
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // ===== 外观 =====
            item { SmallTitle("外观") }
            item {
                GroupCard {
                    GlassDropdownPreference(
                        title = "主题模式",
                        items = ThemeMode.entries.map { themeTitle(it) },
                        selectedIndex = ThemeMode.entries.indexOf(ThemeState.mode),
                        backdrop = LocalGlassBackdrop.current,
                        onSelectedIndexChange = { index ->
                            val mode = ThemeMode.entries[index]
                            ThemeState.mode = mode
                            ThemePrefs.save(context, mode)
                        },
                    )
                }
            }

            // ===== 保活策略 =====
            item { SmallTitle("权限") }
            item {
                GroupCard {
                    BasicComponent(
                        title = "忽略电池优化",
                        summary = if (ignoringBattery) {
                            "系统在省电策略里对本应用没有限制"
                        } else {
                            "点开关去系统设置里选「无限制」"
                        },
                        endActions = {
                            Switch(
                                checked = ignoringBattery,
                                onCheckedChange = {
                                    context.startActivity(
                                        KeepAlive.batteryOptimizationIntent(context),
                                    )
                                },
                            )
                        },
                    )
                    GroupDivider()
                    BasicComponent(
                        title = "允许自启动",
                        summary = "如需每个应用弹出胶囊位置统一，请授予自启动权限",
                        endActions = {
                            Switch(
                                checked = autoStart,
                                onCheckedChange = {
                                    context.startActivity(KeepAlive.appDetailsIntent(context))
                                },
                            )
                        },
                    )
                }
            }

            // ===== 备份与恢复 =====
            item { SmallTitle("数据") }
            item {
                GroupCard {
                    BasicComponent(
                        title = "备份与恢复",
                        summary = if (backupCount == 0) {
                            "导出 / 导入规则文件、立即备份"
                        } else {
                            "导出 / 导入规则文件 · 已有 $backupCount 份备份"
                        },
                        onClick = onOpenBackup,
                    )
                }
            }

            // ===== 日志 =====
            item { SmallTitle("日志") }
            item {
                GroupCard {
                    BasicComponent(
                        title = "模块日志",
                        summary = "查看每次跳转的拦截 / 放行原因（读 logcat 需要 root）",
                        onClick = onOpenLogs,
                    )
                }
            }

            // ===== 启动入口 =====
            item { SmallTitle("启动入口") }
            item {
                GroupCard {
                    BasicComponent(
                        title = "显示桌面图标",
                        summary = if (launcherVisible) {
                            "关闭后桌面不再显示图标，仍可从 LSPosed「模块设置 → 打开」进入"
                        } else {
                            "已隐藏 · 请从 LSPosed 模块设置右下角「打开」进入本应用"
                        },
                        endActions = {
                            Switch(
                                checked = launcherVisible,
                                onCheckedChange = { next ->
                                    val ok = LauncherIconManager.setVisible(context, next)
                                    launcherVisible = LauncherIconManager.isVisible(context)
                                    // 成功不打扰，只在切换失败时提示
                                    if (!ok) toast("切换失败，请重试")
                                },
                            )
                        },
                    )
                }
            }

            // ===== 关于 =====
            item { SmallTitle("关于") }
            item {
                GroupCard {
                    BasicComponent(
                        title = "版本 ${BuildConfig.VERSION_NAME}",
                        summary = "检查更新、GitHub 仓库",
                        onClick = onOpenAbout,
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
