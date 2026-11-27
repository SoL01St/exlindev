package io.github.sol01st.nobrowser.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sol01st.nobrowser.core.RulesCodec
import io.github.sol01st.nobrowser.core.RouteRule
import io.github.sol01st.nobrowser.core.TargetApp
import io.github.sol01st.nobrowser.data.BackupBundle
import io.github.sol01st.nobrowser.data.RuleStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 备份与恢复：导出 / 导入 / 立即备份 / 从备份恢复。
 *
 * 这四项原来在设置页「规则」栏里，挪到二级页避免设置页太长；导出导入都是系统
 * SAF 文件选择器，不需要存储权限。
 */
@Composable
fun BackupScreen(
    apps: List<TargetApp>,
    routes: List<RouteRule>,
    store: RuleStore,
    onBack: () -> Unit,
    onRestore: (List<TargetApp>, List<RouteRule>) -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    fun toast(msg: String) = HintState.show(msg)

    var pendingImport by remember { mutableStateOf<BackupBundle.Payload?>(null) }
    var pendingRestore by remember { mutableStateOf<BackupBundle.Payload?>(null) }
    // 备份文件列表 + 选择弹窗：恢复时可以自己挑时间点
    var backupFiles by remember { mutableStateOf(store.backups()) }
    var backupPicker by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<File?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(BackupBundle.encode(apps, routes).toByteArray())
                } ?: error("no stream")
            }.isSuccess
            toast(if (ok) "已导出 ${apps.size} 个应用 + ${routes.size} 条路由" else "导出失败")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        val parsed = text?.let { BackupBundle.decode(it) }
        if (parsed == null) {
            toast("导入失败：文件格式不正确")
            return@rememberLauncherForActivityResult
        }
        if (RulesCodec.schemaVersionOf(text) > RulesCodec.SCHEMA_VERSION) {
            toast("该文件来自更新的版本，部分字段可能被忽略")
        }
        pendingImport = parsed
    }

    NxbPageScaffold(
        title = "备份与恢复",
        largeTitle = "备份与恢复",
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            GlassIconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
        },
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
                bottom = listBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { SmallTitle("规则文件") }
            item {
                GroupCard {
                    BasicComponent(
                        title = "导出规则",
                        summary = "保存为 JSON 文件（含格式版本号）",
                        onClick = { exportLauncher.launch("nxb-rules.json") },
                    )
                    GroupDivider()
                    BasicComponent(
                        title = "导入规则",
                        summary = "从 JSON 文件恢复，覆盖当前规则前会确认",
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                    )
                }
            }

            item { SmallTitle("备份") }
            item {
                GroupCard {
                    BasicComponent(
                        title = "立即备份",
                        summary = "备份数：${backupFiles.size} 份",
                        onClick = {
                            val file = runCatching { store.backup(apps) }.getOrNull()
                            backupFiles = store.backups()
                            toast(if (file != null) "已备份：${file.name}" else "备份失败")
                        },
                    )
                    GroupDivider()
                    BasicComponent(
                        title = "从备份恢复",
                        summary = if (backupFiles.isEmpty()) {
                            "暂无备份"
                        } else {
                            "共 ${backupFiles.size} 份（最多保留 ${RuleStore.MAX_BACKUPS} 份）"
                        },
                        enabled = backupFiles.isNotEmpty(),
                        onClick = {
                            backupFiles = store.backups()
                            if (backupFiles.isEmpty()) toast("暂无备份") else backupPicker = true
                        },
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    // ---------- 弹窗 ----------
    pendingImport?.let { parsed ->
        SimpleDialog(
            title = "导入规则",
            text = "将用文件中的 ${parsed.apps.size} 个应用 + ${parsed.routes.size} 条路由覆盖当前配置" +
                "（${apps.size} 应用 + ${routes.size} 路由），确定继续？",
            confirmText = "覆盖导入",
            onDismiss = { pendingImport = null },
            onConfirm = {
                pendingImport = null
                store.save(parsed.apps)
                onRestore(parsed.apps, parsed.routes)
                toast("导入成功：${parsed.apps.size} 应用 + ${parsed.routes.size} 路由")
            },
        )
    }

    if (backupPicker) {
        AppDialog(onDismiss = { backupPicker = false }) {
            AppText("选择要恢复的备份", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            AppText(
                "共 ${backupFiles.size} 份 · 按时间从新到旧 · 长按可删除",
                fontSize = 10.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 1.dp),
            )
            Spacer(Modifier.height(2.dp))
            LazyColumn(Modifier.heightIn(max = 300.dp)) {
                itemsIndexed(backupFiles, key = { _, file -> file.absolutePath }) { index, file ->
                    Column {
                        if (index > 0) GroupDivider(startIndent = 0.dp)
                        BackupRow(
                            title = backupTimeLabel(file),
                            size = backupSizeLabel(file),
                            newest = index == 0,
                            onClick = {
                                backupPicker = false
                                val parsed = runCatching { BackupBundle.decode(file.readText()) }.getOrNull()
                                if (parsed == null) toast("备份读取失败") else pendingRestore = parsed
                            },
                            onLongClick = { pendingDelete = file },
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", onClick = { backupPicker = false })
            }
        }
    }

    pendingDelete?.let { file ->
        SimpleDialog(
            title = "删除备份",
            text = "确定删除 ${backupTimeLabel(file)} 的备份？删除后无法恢复。",
            confirmText = "删除",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                val ok = store.deleteBackup(file)
                backupFiles = store.backups()
                toast(if (ok) "已删除该备份" else "删除失败")
            },
        )
    }

    pendingRestore?.let { parsed ->
        SimpleDialog(
            title = "恢复备份",
            text = "将用备份中的 ${parsed.apps.size} 个应用 + ${parsed.routes.size} 条路由覆盖当前配置，确定继续？",
            confirmText = "恢复",
            onDismiss = { pendingRestore = null },
            onConfirm = {
                pendingRestore = null
                store.save(parsed.apps)
                onRestore(parsed.apps, parsed.routes)
                toast("已恢复 ${parsed.apps.size} 应用 + ${parsed.routes.size} 路由")
            },
        )
    }
}

/** 备份文件名里的时间戳 → 可读时间，解析失败回退到文件修改时间。 */
private fun backupTimeLabel(file: File): String {
    val stamp = file.name.removePrefix("rules_").removeSuffix(".json")
    val parsed = runCatching { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(stamp) }.getOrNull()
    val date = parsed ?: Date(file.lastModified())
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(date)
}

private fun backupSizeLabel(file: File): String {
    val bytes = file.length()
    return if (bytes >= 1024) "${bytes / 1024} KB" else "$bytes B"
}

/** 备份选择行：小字号、紧凑间距，长按删除。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackupRow(
    title: String,
    size: String,
    newest: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(title, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
        if (newest) {
            AppText(
                "最新",
                fontSize = 10.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        AppText(
            size,
            fontSize = 10.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
