package io.github.sol01st.nobrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 全局轻提示。用应用内自绘的提示条代替系统 Toast，
 * 避免系统 Toast 强制带应用图标、风格不统一。
 */
object HintState {

    var text by mutableStateOf<String?>(null)
        private set

    /** 每次调用自增，用于相同文案也能重新计时。 */
    var token by mutableStateOf(0)
        private set

    fun show(message: String) {
        text = message
        token++
    }

    fun clear() {
        text = null
    }
}
