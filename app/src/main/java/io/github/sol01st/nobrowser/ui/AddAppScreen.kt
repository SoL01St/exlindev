package io.github.sol01st.nobrowser.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sol01st.nobrowser.data.InstalledApps
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 添加应用。
 *
 * 读取应用列表依赖系统的「读取应用列表」权限（manifest 里保留 QUERY_ALL_PACKAGES）。
 * 一进页面就主动读一次，HyperOS 等系统会在这一刻弹授权框；拿到的列表明显不完整时
 * 再补一个应用内弹窗，把用户直接送到权限页，回来后自动重新检测。
 */
@Composable
fun AddAppScreen(
    installed: List<InstalledApps.Item>,
    existing: Set<String>,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onPick: (String, String) -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    val tipPrefs = remember {
        context.getSharedPreferences("nxb_hint", android.content.Context.MODE_PRIVATE)
    }
    var showAppListTip by remember { mutableStateOf(false) }
    var requestedOnce by remember { mutableStateOf(false) }

    fun markTipShown() {
        tipPrefs.edit().putBoolean("applist_tip", true).apply()
    }

    fun openAppPermissionPage() {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + context.packageName),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { HintState.show("打不开系统设置，请手动到应用权限里开启「读取应用列表」") }
    }

    // 从系统设置返回后立刻重新读一次，用户不用再手动点
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { onReload() }

    // 进页面先主动读一次：这一步本身就会触发系统的「读取应用列表」授权弹窗
    LaunchedEffect(Unit) {
        if (!requestedOnce) {
            requestedOnce = true
            onReload()
        }
    }

    // 列表几乎为空 = 权限被系统挡了，弹一次应用内引导
    LaunchedEffect(installed.size) {
        if (installed.size < 3 && !tipPrefs.getBoolean("applist_tip", false)) {
            showAppListTip = true
        }
    }

    val list = remember(installed, query, existing) {
        installed.filter { it.pkg !in existing }
            .filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }
    }

    val listState = rememberLazyListState()

    // 和首页一致：大标题上滑折叠成中间小标题
    val scrollBehavior = MiuixScrollBehavior()
    NxbPageScaffold(
        title = "添加应用",
        largeTitle = "添加应用",
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            IconButton(onClick = onBack) {
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "搜索应用名或包名",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
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
                    Column(Modifier.padding(16.dp)) {
                        AppText(
                            "没有可添加的应用",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(6.dp))
                        AppText(
                            "列表一直是空的？点这里检查「读取应用列表」权限",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { showAppListTip = true }
                                .padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }

    if (showAppListTip) {
        AppDialog(onDismiss = {
            showAppListTip = false
            markTipShown()
        }) {
            AppText("授权读取应用", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            AppText(
                "添加应用要先读取本机已安装的应用，但系统这次没有把列表给到本应用。\n\n" +
                    "到「系统设置 → 应用管理 → 不用内置浏览器打开 → 权限」里打开" +
                    "「读取应用列表 / 获取应用列表」，然后回来点「重新检测」。",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DialogAction("重新检测", onClick = {
                    showAppListTip = false
                    onReload()
                    HintState.show("正在重新读取应用列表…")
                })
                Spacer(Modifier.width(4.dp))
                DialogAction("去授权", primary = true, onClick = {
                    showAppListTip = false
                    markTipShown()
                    settingsLauncher.launch(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + context.packageName),
                        ),
                    )
                })
            }
        }
    }
}
