package io.github.sol01st.nobrowser.ui

import android.content.ClipData
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.derivedStateOf
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sol01st.nobrowser.data.ModuleLog
import io.github.sol01st.nobrowser.data.ModuleLogEntry
import io.github.sol01st.nobrowser.data.RuleStore
import io.github.sol01st.nobrowser.data.ScopeManager
import io.github.sol01st.nobrowser.data.UiPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.menu.WindowIconCascadingDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 模块日志页：把 HookEngine 的判定日志（拦截 / 各种放行原因）列出来，只读，不改规则。
 *
 * 顶部是「日志记录」总开关：关掉后宿主进程不再写日志，同时停止本页的自动刷新。
 * 右上角刷新图标手动拉一次，⋮ 里是次要操作。数据来自 logcat 环形缓冲，需要 root。
 */
@Composable
fun ModuleLogScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pm = remember { context.packageManager }
    val store = remember { RuleStore(context) }

    var loading by remember { mutableStateOf(false) }
    var rootOk by remember { mutableStateOf(true) }
    var entries by remember { mutableStateOf<List<ModuleLogEntry>>(emptyList()) }
    var interceptedOnly by remember { mutableStateOf(false) }
    var autoRefresh by remember { mutableStateOf(UiPrefs.autoRefresh(context)) }
    var logging by remember { mutableStateOf(UiPrefs.logging(context)) }
    var loaded by remember { mutableStateOf(false) }

    // 包名 → 应用名缓存，避免每条记录都去问 PackageManager
    val appLabels = remember { mutableStateMapOf<String, String>() }
    fun labelOf(pkg: String?): String {
        if (pkg.isNullOrBlank()) return "未知应用"
        appLabels[pkg]?.let { return it }
        val label = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
        appLabels[pkg] = label
        return label
    }

    fun copyText(text: String) {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("module-log", text))
        }.onSuccess { HintState.show("已复制到剪贴板") }
            .onFailure { HintState.show("复制失败") }
    }

    val shown = remember(entries, interceptedOnly) {
        if (interceptedOnly) entries.filter { it.intercepted } else entries
    }

    fun copyAll() {
        if (shown.isEmpty()) {
            HintState.show("没有可复制的内容")
            return
        }
        copyText(ModuleLog.toText(shown))
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(ModuleLog.toText(shown).toByteArray())
            } ?: error("no stream")
        }.isSuccess
        HintState.show(if (ok) "已导出 ${shown.size} 条日志" else "导出失败")
    }

    /** 日志总开关变化后，把新配置推给宿主进程（它们下次拦截时会自动重载）。 */
    fun applyLogging(next: Boolean) {
        logging = next
        UiPrefs.setLogging(context, next)
        autoRefresh = next
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                ScopeManager.publishConfig(context, store.exportJson(store.load()))
            }
            if (!ok) {
                HintState.show("配置写入失败，请确认已授予 root")
            } else {
                HintState.show(if (next) "已开启日志记录" else "已关闭日志记录")
            }
        }
    }

    fun refresh() {
        if (loading) return
        loading = true
        scope.launch {
            val (ok, list) = withContext(Dispatchers.IO) { ModuleLog.fetch() }
            rootOk = ok
            entries = list
            loaded = true
            loading = false
            if (!ok) HintState.show("读取日志需要 root 权限")
        }
    }

    // 自动刷新：开着的时候每 3 秒拉一次，点完链接切回这个页面就能直接看到新记录
    LaunchedEffect(autoRefresh) {
        if (!autoRefresh) return@LaunchedEffect
        while (true) {
            refresh()
            kotlinx.coroutines.delay(3000)
        }
    }
    LaunchedEffect(Unit) {
        if (!autoRefresh) refresh()
    }

    val listState = rememberLazyListState()

        val topHaze = rememberHazeState()
    val density = LocalDensity.current
    val collapse by remember(listState, density) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / with(density) { 50.dp.toPx() }).coerceIn(0f, 1f)
        }
    }

Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(topHaze),
            contentPadding = PaddingValues(
                start = 0.dp,
                end = 0.dp,
                top = 68.dp + 4.dp,
                bottom = listBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                GroupCard {
                    BasicComponent(
                        title = "日志记录",
                        summary = if (logging) {
                            "正在记录，日志会自动刷新"
                        } else {
                            "已关闭，不再写日志，也不再自动刷新"
                        },
                        endActions = {
                            Switch(checked = logging, onCheckedChange = { applyLogging(it) })
                        },
                    )
                }
            }

            if (loaded && shown.isEmpty()) {
                item {
                    AppText(
                        if (!rootOk) {
                            "没拿到 root，读不到 logcat。请确认已在 KernelSU / Magisk 里允许本应用使用 root。"
                        } else if (entries.isEmpty()) {
                            "还没有判定记录。\n\n到任一已接管的应用里点一次链接，再回到这个页面点右上角刷新即可。"
                        } else {
                            "已隐藏全部放行记录，右上角菜单里关掉「只看拦截」即可查看。"
                        },
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
                    )
                }
            }

            if (shown.isNotEmpty()) {
                item { SmallTitle("记录（${shown.size}）· 长按单条可复制") }
                items(shown) { entry ->
                    NxbCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        cornerRadius = 18.dp,
                        onLongClick = { copyText(entry.toLine()) },
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            // 第一行：应用名 + 判定结果 + 时间
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppText(
                                    labelOf(entry.packageName),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                )
                                AppText(
                                    "  ${entry.reason}",
                                    fontSize = 12.sp,
                                    color = if (entry.intercepted) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    },
                                )
                                if (entry.time != null) {
                                    AppText(
                                        "  ${entry.time}",
                                        fontSize = 11.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                            entry.packageName?.let {
                                AppText(
                                    it,
                                    fontSize = 10.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            if (entry.caller != null) {
                                AppText(
                                    "${entry.shortCaller} → ${entry.shortTarget}",
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                )
                                AppText(
                                    buildString {
                                        append(entry.caller)
                                        entry.target?.let { append("  →  ").append(it) }
                                    },
                                    fontSize = 10.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 3,
                                )
                            }
                            entry.host?.let {
                                AppText(it, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                            entry.keys?.let {
                                AppText(
                                    "取值字段：$it",
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }

            item { SmallTitle("说明") }
            item {
                GroupCard {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AppText(
                            "· 拦截 = 弹窗或直接交给手机浏览器\n" +
                                "· 入口未登记 = 该发起方不在规则里（扫码、钱包、服务页等属正常）\n" +
                                "· 目标不匹配 = 入口对了，但打开的不是网页容器\n" +
                                "· 取不到链接 = 入口和目标都对，但字段里没有链接\n" +
                                "· 扫码识别 = 长按识别二维码，故意放行\n" +
                                "· 命中白名单 = 域名在白名单里，故意放行",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        AppText(
                            "读的是 logcat 环形缓冲，旧的记录会被系统冲掉；要留档请用右上角「导出为 txt」。长按任意一条可单独复制。",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
            NxbTopBar(
                collapse = collapse,
                hazeState = topHaze,
                title = "模块日志",
                subtitle = if (loaded) "共 ${entries.size} 条 · 拦截 ${entries.count { it.intercepted }} 条" else null,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    WindowIconCascadingDropdownMenu(
                        entry = DropdownEntry(
                            items = listOf(
                                DropdownItem(
                                    text = "只看拦截",
                                    summary = if (interceptedOnly) "已开启" else "已关闭",
                                    selected = interceptedOnly,
                                    onClick = { interceptedOnly = !interceptedOnly },
                                ),
                                DropdownItem(
                                    text = "复制到剪贴板",
                                    summary = "当前 ${shown.size} 条",
                                    onClick = { copyAll() },
                                ),
                                DropdownItem(
                                    text = "导出为 txt",
                                    summary = "当前 ${shown.size} 条",
                                    onClick = {
                                        if (shown.isEmpty()) {
                                            HintState.show("没有可导出的内容")
                                        } else {
                                            exportLauncher.launch(ModuleLog.defaultFileName())
                                        }
                                    },
                                ),
                            ),
                        ),
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                },
            )
        }
    }
}
