package io.github.sol01st.nobrowser.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sol01st.nobrowser.core.RouteRule
import io.github.sol01st.nobrowser.data.InstalledApps
import io.github.sol01st.nobrowser.data.LinkGoRuleImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 目标应用路由设置：按链接域名/正则把链接直接交给指定应用。 */
@Composable
fun RoutesScreen(
    rules: List<RouteRule>,
    onPersist: (List<RouteRule>, String?) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val tap = rememberTapHaptics()

    var editing by remember { mutableStateOf<RouteRule?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RouteRule?>(null) }
    var pendingClearAll by remember { mutableStateOf(false) }
    var installed by remember { mutableStateOf<List<InstalledApps.Item>>(emptyList()) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        installed = withContext(Dispatchers.IO) { InstalledApps.load(context) }
    }

    fun persist(list: List<RouteRule>, successMsg: String? = "路由配置已生效") {
        onPersist(list, successMsg)
        if (successMsg != null) HintState.show(successMsg)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text == null) {
            HintState.show("导入失败：无法读取文件")
            return@rememberLauncherForActivityResult
        }
        val result = LinkGoRuleImporter.parse(text)
        if (result.error != null) {
            HintState.show("导入失败：${result.error}")
            return@rememberLauncherForActivityResult
        }
        val msg = if (result.disabled.isEmpty()) {
            "已导入 ${result.total} 条 LinkGo 规则"
        } else {
            val sample = result.disabled.take(2).joinToString("、")
            val more = if (result.disabled.size > 2) " 等" else ""
            "已导入 ${result.total} 条，自动禁用 ${result.disabled.size} 条：$sample$more"
        }
        persist(rules + result.rules, successMsg = msg)
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 68.dp + 4.dp,
                bottom = listBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                AppText(
                    "命中路由的链接会直接拉起目标应用，不再弹「应用内 / 浏览器」选择框。未命中时保持原有行为。",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                )
            }

            item { SmallTitle("路由规则（${rules.size}）") }
            item {
                GroupCard {
                    rules.forEachIndexed { index, rule ->
                        BasicComponent(
                            title = rule.name.ifBlank { "未命名路由" },
                            summary = buildString {
                                append("${matchLabel(rule.matchType)}: ${rule.pattern}  →  ")
                                append(rule.targetPackage.ifBlank { "二次分发" })
                                if (rule.disabledReason.isNotBlank()) append("\n已自动禁用：${rule.disabledReason}")
                            },
                            endActions = {
                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = {
                                        tap()
                                        persist(rules.mapIndexed { i, r ->
                                            if (i == index) r.copy(enabled = it) else r
                                        })
                                    },
                                )
                            },
                            onClick = {
                                tap()
                                editing = rule
                                editorOpen = true
                            },
                        )
                        if (index < rules.size - 1) GroupDivider()
                    }
                }
            }

            item {
                GroupCard {
                    BasicComponent(
                        title = "导入 LinkGo 规则",
                        summary = "从 linkgo_rules JSON 导入，不兼容规则自动禁用并提示原因",
                        onClick = {
                            tap()
                            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                    )
                    GroupDivider()
                    BasicComponent(
                        title = "添加路由规则",
                        summary = "域名 / 包含 / 正则 → 目标应用",
                        onClick = {
                            tap()
                            editing = null
                            editorOpen = true
                        },
                    )
                    GroupDivider()
                    BasicComponent(
                        title = "插入 115 示例规则",
                        summary = "115cdn.com → oof.disk scheme + 显式 Activity",
                        onClick = {
                            tap()
                            val sample = RouteRule(
                                name = "115 网盘",
                                pattern = "115cdn.com|115.com",
                                matchType = RouteRule.MATCH_HOST,
                                targetPackage = "com.ylmf.androidclient/com.main.disk.file.uidisk.SchemeMainActivity",
                                urlTemplate = "oof.disk://openurl/{url}",
                            )
                            persist(rules + sample)
                        },
                    )
                    GroupDivider()
                    BasicComponent(
                        title = "插入夸克示例规则",
                        summary = "pan.quark.cn → qklink 深链，网盘分享页直接落到夸克里",
                        onClick = {
                            tap()
                            val sample = RouteRule(
                                name = "夸克网盘",
                                pattern = "pan.quark.cn|quark.cn",
                                matchType = RouteRule.MATCH_HOST,
                                // 只限包名：qklink 深链交给系统按 intent-filter 分发，
                                // 硬写 com.ucpro.MainActivity 会让夸克停在首页。
                                targetPackage = "com.quark.browser",
                                urlTemplate = QUARK_OPEN_URL,
                            )
                            persist(rules + sample)
                        },
                    )
                    GroupDivider()
                    BasicComponent(
                        title = "一键清空路由",
                        summary = "删除全部 ${rules.size} 条路由规则（需二次确认）",
                        enabled = rules.isNotEmpty(),
                        onClick = {
                            tap()
                            pendingClearAll = true
                        },
                    )
                }
            }
        }
    }

    if (editorOpen) {
        RouteEditor(
            initial = editing,
            installed = installed,
            onDismiss = { editorOpen = false },
            onDelete = { target ->
                editorOpen = false
                pendingDelete = target
            },
            onConfirm = { rule ->
                editorOpen = false
                val next = if (editing == null) {
                    rules + rule
                } else {
                    rules.map { if (it.id == rule.id) rule else it }
                }
                persist(next)
            },
        )
    }

    if (pendingClearAll) {
        AppDialog(onDismiss = { pendingClearAll = false }) {
            AppText("清空全部路由？", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            AppText(
                "将删除全部 ${rules.size} 条路由规则，链接会恢复成「应用内 / 浏览器」询问或直接交给浏览器。此操作不可撤销（除非有备份）。",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", onClick = { pendingClearAll = false })
                Spacer(Modifier.width(4.dp))
                DialogAction("确认清空", primary = true, onClick = {
                    pendingClearAll = false
                    persist(emptyList(), successMsg = "已清空全部路由")
                })
            }
        }
    }

    pendingDelete?.let { target ->
        AppDialog(onDismiss = { pendingDelete = null }) {
            AppText("删除路由规则？", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            AppText(
                target.name.ifBlank { target.pattern },
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", onClick = { pendingDelete = null })
                Spacer(Modifier.width(4.dp))
                DialogAction("删除", primary = true, onClick = {
                    pendingDelete = null
                    persist(rules.filterNot { it.id == target.id })
                })
            }
        }
    }
}

private fun matchLabel(type: String): String = when (type.uppercase()) {
    RouteRule.MATCH_REGEX -> "正则"
    RouteRule.MATCH_CONTAINS -> "包含"
    RouteRule.MATCH_EXACT -> "精确"
    else -> "域名"
}

@Composable
private fun RouteEditor(
    initial: RouteRule?,
    installed: List<InstalledApps.Item>,
    onDismiss: () -> Unit,
    onDelete: (RouteRule) -> Unit,
    onConfirm: (RouteRule) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var matchType by remember { mutableStateOf(initial?.matchType ?: RouteRule.MATCH_HOST) }
    var pattern by remember { mutableStateOf(initial?.pattern ?: "") }
    var target by remember { mutableStateOf(initial?.targetPackage ?: "") }
    var template by remember { mutableStateOf(initial?.urlTemplate ?: "") }
    var extract by remember { mutableStateOf(initial?.extractPattern ?: "") }
    var resolveShort by remember { mutableStateOf(initial?.resolveShortLink ?: false) }
    var strategy by remember {
        mutableStateOf(initial?.resolveStrategy ?: RouteRule.STRATEGY_NONE)
    }
    var disabledReason by remember { mutableStateOf(initial?.disabledReason ?: "") }
    var sources by remember {
        mutableStateOf(initial?.sourcePackages?.joinToString(",") ?: "")
    }
    var showPicker by remember { mutableStateOf(false) }

    AppDialog(onDismiss = onDismiss) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
        AppText(if (initial == null) "添加路由规则" else "编辑路由规则", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))

        TextField(
            value = name,
            onValueChange = { name = it },
            label = "名称（可选）",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))

        AppText("匹配方式", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MatchChoice("域名", matchType == RouteRule.MATCH_HOST, Modifier.weight(1f)) {
                matchType = RouteRule.MATCH_HOST
            }
            MatchChoice("包含", matchType == RouteRule.MATCH_CONTAINS, Modifier.weight(1f)) {
                matchType = RouteRule.MATCH_CONTAINS
            }
            MatchChoice("正则", matchType == RouteRule.MATCH_REGEX, Modifier.weight(1f)) {
                matchType = RouteRule.MATCH_REGEX
            }
            MatchChoice("精确", matchType == RouteRule.MATCH_EXACT, Modifier.weight(1f)) {
                matchType = RouteRule.MATCH_EXACT
            }
        }
        Spacer(Modifier.height(6.dp))
        AppText(
            "域名支持多个，用 | 分隔；正则按 URL 全文匹配。",
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(10.dp))

        TextField(
            value = pattern,
            onValueChange = { pattern = it },
            label = "匹配内容（如 115cdn.com）",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))

        TextField(
            value = target,
            onValueChange = { target = it },
            label = "目标应用包名（可写 包名/.Activity）",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        AppText(
            "建议写「包名/Activity」显式组件（如 com.quark.browser/com.ucpro.MainActivity），只写包名可能因目标未注册链接而打不开。",
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(4.dp))
        TextButton(
            text = "从已安装应用选择",
            onClick = { showPicker = true },
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
        Spacer(Modifier.height(10.dp))

        TextField(
            value = template,
            onValueChange = { template = it },
            label = "URL 改写模板（可选）",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        AppText(
            "支持 {url}、{1} 捕获组与 _url_dec / _b64_dec 等后缀变换。" +
                "深链要把整条链接塞进参数时用 {url_url_enc}（不做编码的话内层 ? & 会被吃掉）。" +
                "{deeplink} 会去落地页里抓 App 自己的带签名深链，抓不到时用 | 后的兜底值。",
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TemplateChip("夸克·打开链接") { template = QUARK_OPEN_URL }
            TemplateChip("夸克·简版") { template = "qklink://{url_no_scheme}" }
            TemplateChip("115") { template = "oof.disk://openurl/{url}" }
            TemplateChip("落地页深链") { template = "{deeplink}" }
            TemplateChip("清空") { template = "" }
        }
        Spacer(Modifier.height(10.dp))

        TextField(
            value = extract,
            onValueChange = { extract = it },
            label = "提取正则（供 {1} 等捕获组）",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            AppText(
                "跳转前解析短链（302 / meta refresh）",
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = resolveShort, onCheckedChange = { resolveShort = it })
        }
        Spacer(Modifier.height(8.dp))

        AppText("分发策略", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MatchChoice("直接跳转", strategy == RouteRule.STRATEGY_NONE, Modifier.weight(1f)) {
                strategy = RouteRule.STRATEGY_NONE
            }
            MatchChoice("解析后执行", strategy == RouteRule.STRATEGY_DIRECT, Modifier.weight(1f)) {
                strategy = RouteRule.STRATEGY_DIRECT
            }
            MatchChoice("二次分发", strategy == RouteRule.STRATEGY_RE_DISPATCH, Modifier.weight(1f)) {
                strategy = RouteRule.STRATEGY_RE_DISPATCH
            }
        }

        if (disabledReason.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            AppText(
                "已自动禁用：$disabledReason",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            TextButton(
                text = "清除原因并启用",
                onClick = { disabledReason = "" },
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
        Spacer(Modifier.height(10.dp))

        TextField(
            value = sources,
            onValueChange = { sources = it },
            label = "来源应用包名（可选，逗号分隔）",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        AppText(
            "留空 = 对所有已接管应用生效。",
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (initial != null) {
                DialogAction("删除", onClick = { onDelete(initial) })
                Spacer(Modifier.weight(1f))
            }
            DialogAction("取消", onClick = onDismiss)
            Spacer(Modifier.width(4.dp))
            DialogAction(
                "保存",
                primary = true,
                onClick = {
                    val src = sources.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val rule = (initial ?: RouteRule()).copy(
                        name = name.trim(),
                        matchType = matchType,
                        pattern = pattern.trim(),
                        targetPackage = target.trim(),
                        urlTemplate = template.trim(),
                        extractPattern = extract.trim(),
                        resolveShortLink = resolveShort,
                        resolveStrategy = strategy,
                        disabledReason = disabledReason.trim(),
                        enabled = disabledReason.isBlank(),
                        sourcePackages = src.toMutableList(),
                    )
                    val canDispatch = strategy == RouteRule.STRATEGY_RE_DISPATCH
                    if (rule.pattern.isNotBlank() && (rule.targetPackage.isNotBlank() || canDispatch)) {
                        onConfirm(rule)
                    }
                },
            )
        }
        }
    }

    if (showPicker) {
        AppDialog(onDismiss = { showPicker = false }) {
            AppText("选择目标应用", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(installed) { item ->
                    BasicComponent(
                        title = item.label,
                        summary = item.pkg,
                        onClick = {
                            target = item.pkg
                            showPicker = false
                        },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DialogAction("关闭", onClick = { showPicker = false })
            }
        }
    }
}

/** 夸克自己「打开夸克」按钮用的深链格式：appkey 作 path，真实地址编码后放进 url 参数。 */
private const val QUARK_APPKEY = "b20b84fd735a8dd3f7541129bacc4e9a"
private const val QUARK_OPEN_URL =
    "qklink://www.uc.cn/$QUARK_APPKEY?action=open_url&url={url_url_enc}"

@Composable
private fun TemplateChip(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        AppText(text, fontSize = 12.sp)
    }
}

@Composable
private fun MatchChoice(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (selected) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    }
    val fg = if (selected) MiuixTheme.colorScheme.background else MiuixTheme.colorScheme.onSurface
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        AppText(text, fontSize = 13.sp, color = fg)
    }
}
