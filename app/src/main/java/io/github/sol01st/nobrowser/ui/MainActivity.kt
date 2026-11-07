package io.github.sol01st.nobrowser.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.sol01st.nobrowser.data.ThemePrefs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeState.mode = ThemePrefs.load(this)
        super.onCreate(savedInstanceState)
        setContent {
            NxbTheme {
                AppRoot()
            }
        }
    }
}
