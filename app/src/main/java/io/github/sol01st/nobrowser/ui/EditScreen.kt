package io.github.sol01st.nobrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sol01st.nobrowser.core.LinkRule
import io.github.sol01st.nobrowser.core.TargetApp
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

    fun commit(
        rulesNew: List<LinkRule> = rules,
        whiteNew: List<String> = white,
        enabledNew: Boolean = enabled,
        askNew: Boolean = askEach,
    ) {
        rules = rulesNew
        white = whiteNew
        enabled = enabledNew
        askEach = askNew
        onSave(
            app.copy(
                enabled = enabledNew,
                askEachTime = askNew,
                rules = rulesNew.toMutableList(),
                whiteUrls = whiteNew.toMutableList(),
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
                        .animateItem(),
                    cornerRadius = 18.dp,
                ) {
                    BasicComponent(
                        title = rule.activityName,
                        summary = "取值方式：" + describeKey(rule.extrasKey),
                        endActions = {
                            IconButton(onClick = { pendingDeleteRule = rule }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除")
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
                        "还没有白名单。白名单里的域名会保持在应用内打开。",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                    )
                }
            }
            itemsIndexed(white, key = { index, host -> "w" + index + "|" + host }) { _, host ->
                NxbCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .animateItem(),
                    cornerRadius = 18.dp,
                ) {
                    BasicComponent(
                        title = host,
                        endActions = {
                            IconButton(onClick = { pendingDeleteHost = host }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除")
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
            onDismiss = { editingRule = null },
            onConfirm = { activity, key ->
                commit(rulesNew = rules.map { if (it === rule) LinkRule(activity, key) else it })
                editingRule = null
            },
        )
    }

    if (showRuleDialog) {
        RuleEditDialog(
            initialActivity = "",
            initialKey = "ExAuto",
            onDismiss = { showRuleDialog = false },
            onConfirm = { activity, key ->
                commit(rulesNew = rules + LinkRule(activity, key))
                showRuleDialog = false
            },
        )
    }
    if (showWhiteDialog) {
        InputDialog(
            title = "添加白名单",
            placeholder = "例如 qq.com",
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
            placeholder = "例如 qq.com",
            initial = host,
            onDismiss = { editingHost = null },
            onConfirm = { value ->
                commit(whiteNew = white.map { if (it == host) value else it })
                editingHost = null
            },
        )
    }
    if (showTip) {
        SimpleDialog(
            title = "规则说明",
            text = "取值方式\n" +
                "· ExAuto：自动在 Intent 的 data、extras、Bundle 中寻找第一个 http 链接\n" +
                "· ExDat：只读取 Intent.data\n" +
                "· 其它：按对应 key 读取 extras 或 Bundle 的字符串\n\n" +
                "匹配规则\n" +
                "Activity 同时匹配「发起跳转的 Activity」和「Intent 指向的目标 Activity」，" +
                "支持 * 和 ? 通配，不区分大小写。",
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
            pendingDeleteHost?.let { commit(whiteNew = white - it) }
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
