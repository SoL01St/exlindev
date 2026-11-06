package io.github.sol01st.nobrowser.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import io.github.sol01st.nobrowser.core.TargetApp
import io.github.sol01st.nobrowser.data.RuleStore
import io.github.sol01st.nobrowser.data.ScopeManager
import io.github.sol01st.nobrowser.data.ThemePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

private fun themeTitle(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色模式"
    ThemeMode.DARK -> "深色模式"
}

private fun themeDesc(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "自动在浅色与深色外观间切换"
    ThemeMode.LIGHT -> "始终使用浅色外观"
    ThemeMode.DARK -> "始终使用深色外观"
}

private data class SettingItem(
    val title: String,
    val subtitle: String,
    val action: () -> Unit,
)

@Composable
fun SettingsScreen(
    apps: List<TargetApp>,
    store: RuleStore,
    onOpenAbout: () -> Unit,
    onRestore: (List<TargetApp>) -> Unit,
) {
    val context = LocalContext.current

    fun toast(msg: String) {
        HintState.show(msg)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(store.exportJson(apps).toByteArray())
                } ?: error("no stream")
            }.isSuccess
            toast(if (ok) "规则已导出" else "导出失败")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            val parsed = text?.let { store.importJson(it) }
            if (parsed != null) {
                store.save(parsed)
                onRestore(parsed)
                toast("导入成功，共 ${parsed.size} 条")
            } else {
                toast("导入失败：文件格式不正确")
            }
        }
    }
    var themeMode by remember { mutableStateOf(ThemeState.mode) }
    var rootOk by remember { mutableStateOf<Boolean?>(null) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        rootOk = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ScopeManager.hasRoot()
        }
    }

    val items = listOf(
        SettingItem("导出规则", "保存为 JSON 文件") {
            exportLauncher.launch("browser-rules.json")
        },
        SettingItem("导入规则", "从 JSON 文件恢复规则") {
            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        },
        SettingItem("关于", "版本与使用说明") { onOpenAbout() },
    )

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { TopAppBar(title = "设置") },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 0.dp,
                end = 0.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SmallTitle("主题模式") }
            items(listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)) { mode ->
                SectionCard(onClick = {
                    themeMode = mode
                    ThemeState.mode = mode
                    ThemePrefs.save(context, mode)
                }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AppText(themeTitle(mode), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(2.dp))
                            AppText(
                                themeDesc(mode),
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        RadioDot(selected = themeMode == mode)
                    }
                }
            }

            item { SmallTitle("规则") }
            items(items) { item ->
                SectionCard(onClick = item.action) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AppText(item.title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(2.dp))
                            AppText(
                                item.subtitle,
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }

            item { SmallTitle("权限") }
            item {
                SectionCard(onClick = {
                    coroutineScope.launch {
                        val ok = withContext(Dispatchers.IO) { ScopeManager.hasRoot() }
                        rootOk = ok
                        if (!ok) toast("请在 KernelSU 里允许本应用使用 root")
                    }
                }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AppText("Root 权限", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(2.dp))
                            AppText(
                                when (rootOk) {
                                    true -> "已授权，作用域与配置可自动同步"
                                    false -> "未授权，点一下申请（KernelSU 会弹窗）"
                                    else -> "正在检查…"
                                },
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }
    }
}
