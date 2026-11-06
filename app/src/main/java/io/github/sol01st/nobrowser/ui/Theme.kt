package io.github.sol01st.nobrowser.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

enum class ThemeMode { SYSTEM, LIGHT, DARK }

object ThemeState {
    var mode by mutableStateOf(ThemeMode.SYSTEM)
}

@Composable
fun NxbTheme(content: @Composable () -> Unit) {
    val dark = when (ThemeState.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MiuixTheme(
        colors = if (dark) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
