package io.github.sol01st.nobrowser.data

import android.content.Context
import io.github.sol01st.nobrowser.core.Constants
import java.io.File

/**
 * 只读 LSPosed 里本模块的总开关状态（modules_state.enabled）。
 * 不做任何注入态 / 规则命中统计。
 */
object ModuleState {

    /** true = 已在 LSPosed 中启用；false = 未启用或表中无记录；null = 读不到（root/sqlite 不可用）。 */
    fun isEnabled(context: Context): Boolean? {
        val bin = ScopeManager.prepare(context) ?: return null
        val libDir = File(bin.parentFile, "lib").absolutePath
        val sql = "select enabled from modules_state where module_pkg_name='" +
            Constants.SELF_PACKAGE + "' and user_id=0;"
        val out = ScopeManager.capture(
            "LD_LIBRARY_PATH=$libDir ${bin.absolutePath} ${Constants.LSPD_DB} \"$sql\"",
        ) ?: return null
        val value = out.trim()
        if (value.isEmpty()) return false
        return value.firstOrNull() == '1'
    }
}
