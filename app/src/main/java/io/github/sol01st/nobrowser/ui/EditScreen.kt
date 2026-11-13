package io.github.sol01st.nobrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sol01st.nobrowser.core.LinkRule
import io.github.sol01st.nobrowser.core.TargetApp
import io.github.sol01st.nobrowser.data.RuleStore
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun EditScreen(app: TargetApp, onBack: () -> Unit, onSave: (TargetApp) -> Unit) {
    BackHandler(onBack = onBack)
    val tap = rememberTapHaptics()

    var enabled by remember(app.packageName) { mutableStateOf(app.enabled) }
    var askEach by remember(app.packageName) { mutableStateOf(app.askEachTime) }
    var rules by remember(app.packageName) { mutableStateOf(app.rules.toList()) }
    var white by remember(app.packageName) { mutableStateOf(app.whiteUrls.toList()) }
    var editingRule by remember { mutableStateOf<LinkRule?>(null) }
    var showRuleDialog by remember { mutableStateOf(false) }
    var showWhiteDialog by remember { mutableStateOf(false) }
    var editingHost by remember { mutableStateOf<String?>(null) }
    var showTip by remember { mutableStateOf(false) }
    var pendingDeleteRule by remember { mutableStateOf<LinkRule?>(null) }
    var pendingDeleteHost by remember { mutableStateOf<String?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }
    // 被关掉的白名单条目（字符串列表保持不变，单独存一份禁用集合）
    var whiteDisabled by remember(app.packageName) { mutableStateOf(app.disabledWhiteUrls.toSet()) }
    val context = LocalContext.current
    val store = remember { RuleStore(context) }

    fun commit(
        rulesNew: List<LinkRule> = rules,
        whiteNew: List<String> = white,
        enabledNew: Boolean = enabled,
        askNew: Boolean = askEach,
        whiteDisabledNew: Set<String> = whiteDisabled,
    ) {
        rules = rulesNew
        white = whiteNew
        enabled = enabledNew
        askEach = askNew
        whiteDisabled = whiteDisabledNew
        onSave(
            app.copy(
                enabled = enabledNew,
                askEachTime = askNew,
                rules = rulesNew.toMutableList(),
                whiteUrls = whiteNew.toMutableList(),
                disabledWhiteUrls = whiteDisabledNew.toMutableSet(),
            ),
        )
    }


    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            PageHeader(
                title = app.appName,
                subtitle = app.packageName,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 0.dp,
                end = 0.dp,
                top = padding.calculateTopPadding() + 4.dp,
                // 二级页没有底栏，底部只需要导航栏高度 + 一点呼吸空间
                bottom = listBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { SmallTitle("总开关") }
            item {
                GroupCard {
                    BasicComponent(
                        title = "接管此应用",
                        summary = "首次接管的应用需在 LSPosed 作用域里重新勾选一次",
                        endActions = {
                            Switch(
                                checked = enabled,
                                onCheckedChange = {
                                    tap()
                                    commit(enabledNew = it)
                                },
                            )
                        },
                    )
                    GroupDivider()
                    BasicComponent(
                        title = "每次询问",
                        summary = "开启：弹窗选择；关闭：直接用手机浏览器打开",
                        endActions = {
                            Switch(
                                checked = askEach,
                                onCheckedChange = {
                                    tap()
                                    commit(askNew = it)
                                },
                            )
                        },
                    )
                }
            }

            item { SmallTitle("拦截规则（${rules.size}）") }
            if (rules.isEmpty()) {
                item {
                    AppText(
                        "还没有规则。点下面的「添加规则」，填入该应用的网页 Activity 即可。",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                    )
                }
            }
            itemsIndexed(rules, key = { index, rule -> "r" + index + "|" + rule.activityName + "|" + rule.extrasKey }) { _, rule ->
                NxbCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        // 关掉的规则整行变淡，一眼能看出哪条没生效
                        .alpha(if (rule.enabled) 1f else 0.45f)
                        .animateItem(),
                    cornerRadius = 18.dp,
                ) {
                    BasicComponent(
                        title = rule.activityName,
                        summary = (if (rule.isCaller()) "入口 · " else "目标 · ") + "取值方式：" + describeKey(rule.extrasKey),
                        endActions = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = { on ->
                                        commit(
                                            rulesNew = rules.map {
                                                if (it === rule) it.copy(enabled = on) else it
                                            },
                                        )
                                    },
                                )
                                IconButton(onClick = { pendingDeleteRule = rule }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除")
                                }
                            }
                        },
                        onClick = { editingRule = rule },
                    )
                }
            }
            item {
                GroupCard {
                    BasicComponent(
                        title = "添加规则",
                        summary = "Activity 支持 * 通配，例如 *Browser*",
                        onClick = { showRuleDialog = true },
                    )
                }
            }

            item { SmallTitle("白名单（${white.size}）") }
            if (white.isEmpty()) {
                item {
                    AppText(
                        "还没有白名单。白名单里的域名会保持在应用内打开；写成 =mail.qq.com 表示只精确匹配这个域名（不含子域）。",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                    )
                }
            }
            itemsIndexed(white, key = { index, host -> "w" + index + "|" + host }) { _, host ->
                val whiteOn = host !in whiteDisabled
                NxbCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .alpha(if (whiteOn) 1f else 0.45f)
                        .animateItem(),
                    cornerRadius = 18.dp,
                ) {
                    BasicComponent(
                        title = host,
                        endActions = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = whiteOn,
                                    onCheckedChange = { on ->
                                        commit(
                                            whiteDisabledNew = if (on) whiteDisabled - host else whiteDisabled + host,
                                        )
                                    },
                                )
                                IconButton(onClick = { pendingDeleteHost = host }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除")
                                }
                            }
                        },
                        onClick = { editingHost = host },
                    )
                }
            }
            item {
                GroupCard {
                    BasicComponent(
                        title = "添加白名单域名",
                        summary = "命中白名单的链接保持应用内打开",
                        onClick = { showWhiteDialog = true },
                    )
                }
            }

            item {
                GroupCard {
                    BasicComponent(
                        title = "按模板重置规则",
                        summary = "恢复此应用的默认规则与白名单，保留开关设置",
                        onClick = { showResetConfirm = true },
                    )
                }
            }

            item {
                GroupCard {
                    BasicComponent(
                        title = "规则说明",
                        summary = "取值方式、通配与生效规则",
                        onClick = { showTip = true },
                    )
                }
            }
        }
    }

    editingRule?.let { rule ->
        RuleEditDialog(
            initialActivity = rule.activityName,
            initialKey = rule.extrasKey,
            initialScope = rule.scope,
            onDismiss = { editingRule = null },
            onConfirm = { activity, key, scope ->
                commit(
                    rulesNew = rules.map {
                        if (it === rule) it.copy(activityName = activity, extrasKey = key, scope = scope) else it
                    },
                )
                editingRule = null
            },
        )
    }

    if (showRuleDialog) {
        RuleEditDialog(
            initialActivity = "",
            initialKey = "ExAuto",
            initialScope = LinkRule.SCOPE_CALLER,
            onDismiss = { showRuleDialog = false },
            onConfirm = { activity, key, scope ->
                commit(rulesNew = rules + LinkRule(activity, key, scope = scope))
                showRuleDialog = false
            },
        )
    }
    if (showWhiteDialog) {
        InputDialog(
            title = "添加白名单",
            placeholder = "qq.com 或 =mail.qq.com",
            onDismiss = { showWhiteDialog = false },
            onConfirm = { value ->
                showWhiteDialog = false
                commit(whiteNew = white + value)
            },
        )
    }
    editingHost?.let { host ->
        InputDialog(
            title = "编辑白名单域名",
            placeholder = "qq.com 或 =mail.qq.com",
            initial = host,
            onDismiss = { editingHost = null },
            onConfirm = { value ->
                commit(
                    whiteNew = white.map { if (it == host) value else it },
                    whiteDisabledNew = if (host in whiteDisabled) (whiteDisabled - host) + value else whiteDisabled,
                )
                editingHost = null
            },
        )
    }
    if (showResetConfirm) {
        SimpleDialog(
            title = "重置规则",
            text = "将把「${app.appName}」的规则与白名单恢复为内置模板，当前自定义内容会被覆盖。",
            confirmText = "重置",
            onDismiss = { showResetConfirm = false },
            onConfirm = {
                showResetConfirm = false
                val tpl = store.templateFor(app.packageName)
                if (tpl == null) {
                    HintState.show("没有这个应用的内置模板")
                } else {
                    commit(
                        rulesNew = tpl.rules.toList(),
                        whiteNew = tpl.whiteUrls.toList(),
                        whiteDisabledNew = emptySet(),
                    )
                    HintState.show("已按模板重置")
                }
            },
        )
    }
    if (showTip) {
        SimpleDialog(
            title = "规则说明",
            text = "匹配对象\n" +
                "· 入口（发起方）：只有这些页面里点出去的链接才进入判定，推荐。没登记的入口" +
                "（扫一扫、钱包账单、服务栏、小程序）一律放行。\n" +
                "· 目标（网页容器）：按 Intent 指向的 Activity 匹配。应用配了入口规则时，" +
                "目标规则只作为附加限制，必须同时命中。\n" +
                "支持 * 和 ? 通配，不区分大小写。\n\n" +
                "取值方式\n" +
                "· ExDat：只读取 Intent.data\n" +
                "· ExAuto：优先 Intent.data；取不到时在网页容器里嗅探\n" +
                "· 其它：按对应 key 读取 extras 或 Bundle，取不到再退回 Intent.data\n\n" +
                "白名单\n" +
                "· 写 qq.com：相等或子域都放行（wx.qq.com 也算命中）\n" +
                "· 写 =mail.qq.com：只放行这个域名本身，不含子域\n" +
                "· 钉钉、贴吧、QQ 邮箱会把外链包在自家域名下中转，这类域名不要用后缀匹配\n\n" +
                "单条开关\n" +
                "每条规则和白名单右侧都有独立开关，关掉的条目立即不参与匹配。",
            onDismiss = { showTip = false },
        )
    }

    DeleteConfirmDialogs(
        pendingRule = pendingDeleteRule,
        pendingHost = pendingDeleteHost,
        onDismissRule = { pendingDeleteRule = null },
        onConfirmRule = {
            pendingDeleteRule?.let { commit(rulesNew = rules - it) }
            pendingDeleteRule = null
        },
        onDismissHost = { pendingDeleteHost = null },
        onConfirmHost = {
            pendingDeleteHost?.let { host ->
                commit(whiteNew = white - host, whiteDisabledNew = whiteDisabled - host)
            }
            pendingDeleteHost = null
        },
    )
}

@Composable
private fun DeleteConfirmDialogs(
    pendingRule: LinkRule?,
    pendingHost: String?,
    onDismissRule: () -> Unit,
    onConfirmRule: () -> Unit,
    onDismissHost: () -> Unit,
    onConfirmHost: () -> Unit,
) {
    pendingRule?.let {
        SimpleDialog(
            title = "删除规则",
            text = "确定删除这条规则？\n\n${it.activityName}\n取值方式：${describeKey(it.extrasKey)}",
            confirmText = "删除",
            onDismiss = onDismissRule,
            onConfirm = onConfirmRule,
        )
    }
    pendingHost?.let {
        SimpleDialog(
            title = "删除白名单",
            text = "确定删除白名单域名「$it」？",
            confirmText = "删除",
            onDismiss = onDismissHost,
            onConfirm = onConfirmHost,
        )
    }
}

private fun describeKey(key: String): String = when (key) {
    "ExAuto" -> "自动嗅探链接"
    "ExDat" -> "Intent.data"
    else -> key
}
