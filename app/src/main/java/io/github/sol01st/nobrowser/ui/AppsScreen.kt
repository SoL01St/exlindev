package io.github.sol01st.nobrowser.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sol01st.nobrowser.core.TargetApp
import io.github.sol01st.nobrowser.data.InstalledApps
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppsScreen(
    apps: List<TargetApp>,
    installed: List<InstalledApps.Item>,
    defaultPkgs: Set<String>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val installedMap = installed.associateBy { it.pkg }
    val enabledCount = apps.count { it.enabled && it.packageName in installedMap }
    // 内置应用始终显示；用户自己添加的只有开启时才出现；未安装的排在已安装之后
    val visible = apps
        .filter { it.packageName in defaultPkgs || it.enabled }
        .sortedBy { if (it.packageName in installedMap) 0 else 1 }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = "不用内置浏览器打开",
                subtitle = "已接管 $enabledCount 个应用",
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "添加应用")
            }
        },
    ) { padding ->
        PullToRefresh(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 0.dp,
                    end = 0.dp,
                    top = 4.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.packageName }) { app ->
                    val inst = installedMap[app.packageName]
                    AppRow(
                        app = app,
                        label = inst?.label ?: app.appName,
                        installed = inst != null,
                        onToggle = onToggle,
                        onOpen = onOpen,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: TargetApp,
    label: String,
    installed: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onOpen: (String) -> Unit,
) {
    SectionCard(onClick = { if (installed) onOpen(app.packageName) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppText(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    if (!installed) {
                        Spacer(Modifier.width(6.dp))
                        AppText("未安装", fontSize = 10.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
                Spacer(Modifier.height(2.dp))
                AppText(
                    app.packageName,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = installed && app.enabled,
                enabled = installed,
                onCheckedChange = { if (installed) onToggle(app.packageName, it) },
            )
        }
    }
}
