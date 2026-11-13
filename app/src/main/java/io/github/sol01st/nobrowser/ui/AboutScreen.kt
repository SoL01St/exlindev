package io.github.sol01st.nobrowser.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sol01st.nobrowser.BuildConfig
import io.github.sol01st.nobrowser.data.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var checking by remember { mutableStateOf(false) }
    var latest by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun checkUpdate() {
        if (checking) return
        checking = true
        note = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { UpdateChecker.check() }
            checking = false
            when (result) {
                is UpdateChecker.Result.Newer -> {
                    latest = result.version
                    note = "发现新版本 ${result.version}，点击前往下载"
                    openUrl(result.url)
                }

                UpdateChecker.Result.Latest -> {
                    latest = null
                    note = "已是最新版本"
                }

                is UpdateChecker.Result.Failed -> {
                    latest = null
                    note = "检查失败：${result.reason}"
                }
            }
        }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            PageHeader(
                title = "关于",
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
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { SmallTitle("应用信息") }
            item {
                GroupCard {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AppText("不用内置浏览器打开", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        AppText(
                            "点击应用内链接可选择应用内置浏览器或手机浏览器打开。",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(2.dp))
                        LabelValue("版本", BuildConfig.VERSION_NAME)
                        LabelValue("Xposed API", "102 (libxposed)")
                        LabelValue("包名", BuildConfig.APPLICATION_ID)
                    }
                }
            }

            item { SmallTitle("更新") }
            item {
                GroupCard {
                    BasicComponent(
                        title = if (checking) "正在检查…" else "检查更新",
                        summary = note
                            ?: if (latest != null) "发现新版本 $latest" else "从 GitHub 获取最新版本号",
                        enabled = !checking,
                        onClick = { checkUpdate() },
                    )
                    GroupDivider(startIndent = 16.dp)
                    BasicComponent(
                        title = "GitHub 仓库",
                        summary = "SoL01St/exlindev",
                        onClick = { openUrl("https://github.com/SoL01St/exlindev") },
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

