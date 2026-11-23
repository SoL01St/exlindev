package io.github.sol01st.nobrowser.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sol01st.nobrowser.BuildConfig
import io.github.sol01st.nobrowser.core.RulesCodec
import io.github.sol01st.nobrowser.core.TargetApp
import io.github.sol01st.nobrowser.data.LauncherIconManager
import io.github.sol01st.nobrowser.data.RuleStore
import io.github.sol01st.nobrowser.data.ThemePrefs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private fun themeTitle(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色模式"
    ThemeMode.DARK -> "深色模式"
}

@Composable
fun SettingsScreen(
    apps: List<TargetApp>,
    store: RuleStore,
    listState: LazyListState,
    onOpenAbout: () -> Unit,
    onOpenLogs: () -> Unit,
    onRestore: (List<TargetApp>) -> Unit,
) {
    val context = LocalContext.current

    fun toast(msg: String) = HintState.show(msg)

    // 桌面图标开关的当前状态直接从 PackageManager 读，不额外存 pref，避免和系统不同步
    var launcherVisible by remember { mutableStateOf(LauncherIconManager.isVisible(context)) }
    var pendingImport by remember { mutableStateOf<List<TargetApp>?>(null) }
    var pendingRestore by remember { mutableStateOf<List<TargetApp>?>(null) }
    // 备份文件列表 + 选择弹窗：恢复时可以自己挑时间点
    var backupFiles by remember { mutableStateOf(store.backups()) }
    var backupPicker by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<File?>(null) }

    // ---------- 导出 / 导入 / 备份 ----------
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(store.exportJson(apps).toByteArray())
                } ?: error("no stream")
            }.isSuccess
            toast(if (ok) "已导出 ${apps.size} 个应用的规则" else "导出失败")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        val parsed = text?.let { store.importJson(it) }
        if (parsed == null) {
            toast("导入失败：文件格式不正确")
            return@rememberLauncherForActivityResult
        }
        if (RulesCodec.schemaVersionOf(text) > RulesCodec.SCHEMA_VERSION) {
            toast("该文件来自更新的版本，部分字段可能被忽略")
        }
        pendingImport = parsed
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
                    OverlayDropdownPreference(
                        title = "主题模式",
                        items = ThemeMode.entries.map { themeTitle(it) },
                        selectedIndex = ThemeMode.entries.indexOf(ThemeState.mode),
                        onSelectedIndexChange = { index ->
                            val mode = ThemeMode.entries[index]
                            ThemeState.mode = mode
                            ThemePrefs.save(context, mode)
                        },
                    )
                }
            }

            // ===== 规则 =====
            item { SmallTitle("规则") }
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
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    )
                    GroupDivider()
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

    // ---------- 确认弹窗 ----------
    pendingImport?.let { parsed ->
        SimpleDialog(
            title = "导入规则",
            text = "将用文件中的 ${parsed.size} 个应用规则覆盖当前 ${apps.size} 个应用的配置，确定继续？",
            confirmText = "覆盖导入",
            onDismiss = { pendingImport = null },
            onConfirm = {
                pendingImport = null
                store.save(parsed)
                onRestore(parsed)
                toast("导入成功，共 ${parsed.size} 条")
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
                                val parsed = runCatching { store.importJson(file.readText()) }.getOrNull()
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
            text = "将用备份中的 ${parsed.size} 个应用规则覆盖当前配置，确定继续？",
            confirmText = "恢复",
            onDismiss = { pendingRestore = null },
            onConfirm = {
                pendingRestore = null
                store.save(parsed)
                onRestore(parsed)
                toast("已恢复 ${parsed.size} 条规则")
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
