package io.github.sol01st.nobrowser.data

import android.content.Context

/**
 * 界面类的小偏好（只影响模块 App 自己，不参与 Xposed 侧的规则判定）。
 * 规则相关的东西都在 RuleStore 里，别混进来。
 */
object UiPrefs {

    private const val FILE = "nxb_ui"
    private const val KEY_LOG_AUTO_REFRESH = "log_auto_refresh"
    private const val KEY_LOGGING = "logging_enabled"

    /** 模块日志页是否自动刷新，默认开启。 */
    fun autoRefresh(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOG_AUTO_REFRESH, true)

    /** 同步落盘：页面可能马上被销毁，apply() 有丢写风险。 */
    fun setAutoRefresh(context: Context, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LOG_AUTO_REFRESH, value)
            .commit()
    }

    /** 日志总开关：关掉后宿主进程不再写判定日志，App 侧也停止自动刷新。默认开启。 */
    fun logging(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOGGING, true)

    fun setLogging(context: Context, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LOGGING, value)
            .putBoolean(KEY_LOG_AUTO_REFRESH, value)
            .commit()
    }
}
