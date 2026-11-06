package io.github.sol01st.nobrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sol01st.nobrowser.data.InstalledApps
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AddAppScreen(
    installed: List<InstalledApps.Item>,
    existing: Set<String>,
    onBack: () -> Unit,
    onPick: (String, String) -> Unit,
) {
    BackHandler(onBack = onBack)
    var query by remember { mutableStateOf("") }
    val list = remember(installed, query, existing) {
        installed.filter { it.pkg !in existing }
            .filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "添加应用",
                subtitle = "仅在需要时手动添加",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 0.dp,
                    end = 0.dp,
                    top = 4.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.pkg }) { item ->
                    Card(onClick = { onPick(item.label, item.pkg) }) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(item.pkg, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                AppText(item.label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(2.dp))
                                AppText(
                                    item.pkg,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                )
                            }
                        }
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
    }
}
