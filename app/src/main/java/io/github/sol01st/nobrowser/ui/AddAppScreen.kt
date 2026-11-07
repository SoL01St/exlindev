package io.github.sol01st.nobrowser.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sol01st.nobrowser.data.InstalledApps
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AddAppScreen(
    installed: List<InstalledApps.Item>,
    existing: Set<String>,
    onBack: () -> Unit,
    onPick: (String, String) -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    // 首次进入且读不到足够的应用列表时，提示去系统里开启「获取应用列表」权限（MIUI/HyperOS 由系统 AppOps 控制，无法代码申请）
    val tipPrefs = remember { context.getSharedPreferences("nxb_hint", android.content.Context.MODE_PRIVATE) }
    var showAppListTip by remember {
        mutableStateOf(installed.size < 10 && !tipPrefs.getBoolean("applist_tip", false))
    }
    val list = remember(installed, query, existing) {
        installed.filter { it.pkg !in existing }
            .filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            PageHeader(
                title = "添加应用",
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
                bottom = listBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.secondaryVariant)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        textStyle = TextStyle(
                            color = MiuixTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                AppText(
                                    "搜索应用名或包名",
                                    fontSize = 15.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            inner()
                        },
                    )
                }
            }
            items(list, key = { it.pkg }) { item ->
                NxbCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .animateItem(),
                    cornerRadius = 18.dp,
                ) {
                    BasicComponent(
                        title = item.label,
                        summary = item.pkg,
                        startAction = { AppIcon(item.pkg, size = 36.dp) },
                        onClick = { onPick(item.label, item.pkg) },
                    )
                }
            }
            if (list.isEmpty()) {
                item {
                    AppText(
                        "没有可添加的应用",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    if (showAppListTip) {
                SimpleDialog(
                    title = "需要读取应用列表",
                    text = "添加应用需要读取本机已安装应用。\n\n" +
                        "如果列表为空或不完整，请到「系统设置 → 应用管理 → 不用内置浏览器打开 → 权限」" +
                        "开启「获取应用列表」，然后回到这里重新进入本页。",
                    confirmText = "去设置",
                    onDismiss = {
                        showAppListTip = false
                        tipPrefs.edit().putBoolean("applist_tip", true).apply()
                    },
                    onConfirm = {
                        showAppListTip = false
                        tipPrefs.edit().putBoolean("applist_tip", true).apply()
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + context.packageName),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                )
            }
}
