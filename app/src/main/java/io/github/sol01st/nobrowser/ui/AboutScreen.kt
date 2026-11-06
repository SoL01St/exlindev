package io.github.sol01st.nobrowser.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            TopAppBar(
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
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionCard {
                    AppText("不用内置浏览器打开", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    AppText(
                        "点击应用内链接可选择应用内置浏览器或手机浏览器打开。",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(12.dp))
                    LabelValue("版本", "4.2.0")
                    LabelValue("Xposed API", "102 (libxposed)")
                    LabelValue("包名", "io.github.sol01st.nobrowser")
                    Spacer(Modifier.height(8.dp))
                    GitHubLink()
                }
            }

            item {
                SectionCard {
                    AppText("使用提示", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TipItem("1", "作用域同步与配置写入需要 root 权限，可在「设置 - Root 权限」查看状态。")
                        TipItem("2", "首次接管的应用需在 LSPosed 作用域里重新勾选一次。")
                        TipItem("3", "首页默认接管 QQ、微信、QQ邮箱、百度贴吧、钉钉、TIM；其他应用点右下角 + 号添加，添加的应用自行添加编辑规则。")
                        TipItem("4", "「每次询问」开启时，点链接先弹窗，选择用应用内浏览器还是手机浏览器打开；关闭时直接交给手机浏览器，不弹窗。")
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun TipItem(index: String, text: String) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
        AppText(index + ". ", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        AppText(
            text,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun GitHubLink() {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                runCatching {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/SoL01St/exlindev"),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText("GitHub 仓库", fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        AppText(
            "SoL01St/exlindev",
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.primary,
        )
    }
}
