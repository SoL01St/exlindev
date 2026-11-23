package io.github.sol01st.nobrowser.data

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

/**
 * 保活相关的系统状态查询与跳转。
 *
 * 提示框位置的唯一副本存在模块进程里，模块被 force-stop 之后宿主连 provider 的
 * authority 都解析不到，各应用就只能各记各的位置。这里只做两件不需要 root 的事：
 * 引导把本应用加进电池优化白名单、允许自启动。
 */
object KeepAlive {

    /** 电池优化白名单：标准 API，结果可信。 */
    fun ignoringBatteryOptimizations(context: Context): Boolean = runCatching {
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)

    /** 电池优化授权页；机型不认「直接对本应用开白名单」时退回列表页。 */
    fun batteryOptimizationIntent(context: Context): Intent {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:" + context.packageName),
        )
        if (context.packageManager.resolveActivity(direct, 0) != null) return direct
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    /**
     * HyperOS / MIUI 的自启动是私有 appops，编号 10008，在 `appops get` 里显示为
     * `MIUIOP(10008)`。按名字查（"AUTO_START"）会直接报 Unknown operation string，
     * 只能按编号查；查的又是自己（同 uid），不需要额外权限。
     */
    private const val MIUI_OP_AUTO_START = 10008

    /**
     * 自启动是否已允许。
     *
     * 读不到（非 MIUI/HyperOS、或系统砍掉这个 op）时返回 false，开关显示为关闭，
     * 点击仍然跳应用详情页，不会误导操作。
     */
    fun autoStartEnabled(context: Context): Boolean = runCatching {
        val aom = context.getSystemService(AppOpsManager::class.java)
        // 公开 SDK 只有按名字查的版本，按编号查的那个是 @hide，只能反射
        val method = AppOpsManager::class.java.getMethod(
            "checkOpNoThrow",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java,
        )
        val mode = method.invoke(aom, MIUI_OP_AUTO_START, Process.myUid(), context.packageName) as Int
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /**
     * 应用详情页：HyperOS 的「自启动」开关就在这一页。
     *
     * 这里刻意带上 NEW_TASK：设置页属于它自己的任务栈，不带这个 flag 时系统会按
     * 「跨应用启动」重定向，表现为打开后闪一下又被顶回来；带上之后就是一个规矩的
     * 新任务，不会再闪。返回落点由系统决定（可能回到桌面），回到本页时靠
     * ON_START / ON_RESUME 重读状态。 
     */
    fun appDetailsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:" + context.packageName),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
