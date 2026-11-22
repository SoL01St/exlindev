package io.github.sol01st.nobrowser.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 桌面图标开关。
 *
 * 图标由 manifest 里的 `.Launcher` activity-alias 提供，隐藏它不影响
 * `.ui.EntryActivity`（带 `de.robv.android.xposed.category.MODULE_SETTINGS`），
 * 所以 LSPosed 管理器里「模块设置 / 打开」的入口始终可用。
 */
object LauncherIconManager {

    /** activity-alias 的完整组件名，必须与 AndroidManifest 保持一致。 */
    private const val LAUNCHER_ALIAS = "io.github.sol01st.nobrowser.Launcher"

    fun isVisible(context: Context): Boolean {
        val pm = context.packageManager
        val state = runCatching {
            pm.getComponentEnabledSetting(ComponentName(context.packageName, LAUNCHER_ALIAS))
        }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        return when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
            -> false

            else -> true
        }
    }

    /** 显示 / 隐藏桌面图标。返回是否设置成功。 */
    fun setVisible(context: Context, visible: Boolean): Boolean = runCatching {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context.packageName, LAUNCHER_ALIAS),
            if (visible) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
        isVisible(context) == visible
    }.getOrDefault(false)
}
