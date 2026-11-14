package io.github.sol01st.nobrowser.ui

import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import io.github.sol01st.nobrowser.data.ThemePrefs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val mode = ThemePrefs.load(this)
        ThemeState.mode = mode
        // 冷启动第一帧的窗口底色跟上当前主题，避免深色模式闪白
        val dark = when (mode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM ->
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        window.setBackgroundDrawable(ColorDrawable(if (dark) 0xFF242424.toInt() else 0xFFF7F7F7.toInt()))
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NxbTheme {
                AppRoot()
            }
        }
    }
}
