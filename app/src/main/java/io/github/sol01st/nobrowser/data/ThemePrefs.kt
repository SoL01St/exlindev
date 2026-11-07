package io.github.sol01st.nobrowser.data

import android.content.Context
import io.github.sol01st.nobrowser.ui.ThemeMode

object ThemePrefs {

    private const val NAME = "nxb_theme"
    private const val KEY = "mode"

    fun load(context: Context): ThemeMode {
        val raw = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY, null)
        return runCatching { ThemeMode.valueOf(raw ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun save(context: Context, mode: ThemeMode) {
        // 用 commit() 同步落盘：切完主题如果进程被立刻回收，apply() 的异步写会丢
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY, mode.name).commit()
    }
}
