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
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY, mode.name).apply()
    }
}
