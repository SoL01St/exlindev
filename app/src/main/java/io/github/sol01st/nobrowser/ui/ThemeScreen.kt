package io.github.sol01st.nobrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.sol01st.nobrowser.data.ThemePrefs
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 主题模式二级页：三个选项，点选即生效（写入 ThemePrefs），不需要额外保存。
 */
@Composable
fun ThemeScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            PageHeader(
                title = "主题模式",
                subtitle = "跟随系统，或固定为浅色 / 深色",
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
            item { SmallTitle("模式") }
            item {
                GroupCard {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        if (index > 0) GroupDivider()
                        BasicComponent(
                            title = themeModeLabel(mode),
                            endActions = { RadioDot(selected = ThemeState.mode == mode) },
                            onClick = {
                                ThemeState.mode = mode
                                ThemePrefs.save(context, mode)
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色模式"
    ThemeMode.DARK -> "深色模式"
}
