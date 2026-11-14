package io.github.sol01st.nobrowser.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

enum class ThemeMode { SYSTEM, LIGHT, DARK }

object ThemeState {
    var mode by mutableStateOf(ThemeMode.SYSTEM)
}

/** 当前是否深色，仅用于描边/透明度这类细节。 */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun currentIsDark(): Boolean = when (ThemeState.mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * 状态栏 / 导航栏图标外观跟随主题。
 * 深色主题下系统图标用白色（isAppearanceLight* = false），否则深色下图标与背景同色会看不见。
 */
@Composable
private fun SystemBarsAppearance(dark: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

private fun Context.findActivity(): ComponentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun NxbTheme(content: @Composable () -> Unit) {
    val dark = currentIsDark()
    SystemBarsAppearance(dark)
    // 顶栏大标题用 Miuix 的 title1（默认 32sp），这里压到 26sp，和原来的 PageHeader 一致
    val baseTextStyles = MiuixTheme.textStyles
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MiuixTheme(
            textStyles = baseTextStyles.copy(
                title1 = baseTextStyles.title1.copy(fontSize = 26.sp),
            ),
            colors = if (dark) {
            darkColorScheme()
        } else {
            // 参考图配色：页面浅灰底，卡片纯白，分组边界可见
            // 背景比 iOS 的 F2F2F7 再深一档：卡片水平留白只有 12dp、行间距 6dp，
            // 光靠原来那点色差在低对比屏上几乎看不出卡片边界。
            lightColorScheme().copy(
                background = Color(0xFFF7F7F7),
                surface = Color(0xFFFFFFFF),
            )
        },
            content = content,
        )
    }
}
