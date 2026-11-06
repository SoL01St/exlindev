package io.github.sol01st.nobrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sol01st.nobrowser.core.LinkRule
import io.github.sol01st.nobrowser.core.TargetApp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun EditScreen(app: TargetApp, onBack: () -> Unit, onSave: (TargetApp) -> Unit) {
    BackHandler(onBack = onBack)

    var enabled by remember(app.packageName) { mutableStateOf(app.enabled) }
    var askEach by remember(app.packageName) { mutableStateOf(app.askEachTime) }
    var rules by remember(app.packageName) { mutableStateOf(app.rules.toList()) }
    var white by remember(app.packageName) { mutableStateOf(app.whiteUrls.toList()) }
    var editingRule by remember { mutableStateOf<LinkRule?>(null) }
    var showRuleDialog by remember { mutableStateOf(false) }
    var showWhiteDialog by remember { mutableStateOf(false) }
    var editingHost by remember { mutableStateOf<String?>(null) }
    var showTip by remember { mutableStateOf(false) }

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
            TopAppBar(
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
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SmallTitle("总开关") }
            item {
                SectionCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AppText("接管此应用", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            AppText(
                                "首次接管的应用需在 LSPosed 作用域里重新勾选一次",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Switch(checked = enabled, onCheckedChange = { commit(enabledNew = it) })
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AppText("每次询问", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            AppText(
                                "开启：弹窗选择；关闭：直接用手机浏览器打开",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Switch(checked = askEach, onCheckedChange = { commit(askNew = it) })
                    }
                }
            }

            item { SmallTitle("拦截规则（${rules.size}）") }
            items(rules) { rule ->
                SectionCard(onClick = { editingRule = rule }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AppText(rule.activityName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(2.dp))
                            AppText(
                                "取值方式：" + describeKey(rule.extrasKey),
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        IconButton(onClick = { commit(rulesNew = rules - rule) }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    }
                }
            }
            item {
                SectionCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AppText("添加规则", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            AppText(
                                "Activity 支持 * 通配，例如 *Browser*",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        TextButton("添加", onClick = { showRuleDialog = true })
                    }
                }
            }

            item { SmallTitle("白名单（${white.size}）") }
            items(white) { host ->
                SectionCard(onClick = { editingHost = host }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AppText(host, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        IconButton(onClick = { commit(whiteNew = white - host) }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    }
                }
            }
            item {
                SectionCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AppText("添加白名单域名", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            AppText(
                                "命中白名单的链接保持应用内打开",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        TextButton("添加", onClick = { showWhiteDialog = true })
                    }
                }
            }

            item {
                SectionCard(onClick = { showTip = true }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AppText("规则说明", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            AppText(
                                "取值方式、通配与生效规则",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        AppText("查看", fontSize = 13.sp, color = MiuixTheme.colorScheme.primary)
                    }
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
                "支持 * 和 ? 通配，不区分大小写。\n\n" +
                "生效说明\n" +
                "修改「每次询问」后，需要结束目标应用再重新打开才会生效。",
            onDismiss = { showTip = false },
        )
    }
}

private fun describeKey(key: String): String = when (key) {
    "ExAuto" -> "自动嗅探链接"
    "ExDat" -> "Intent.data"
    else -> key
}
