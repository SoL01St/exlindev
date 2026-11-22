package io.github.sol01st.nobrowser.data

import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 与 LSPosed 框架的控制通道（libxposed API 102 的 service 侧）。
 *
 * 框架会把 binder 通过本应用 manifest 里的 `XposedProvider` 送进来，
 * [XposedServiceHelper] 收到后回调这里，之后就能：
 *  - `getScope / requestScope / removeScope` 读写作用域（不用再碰 modules_config.db，不需要 root）
 *  - `getRemotePreferences` 写模块配置（宿主进程用 `getRemotePreferences` 读同一份，不需要 root）
 *
 * service 不可用时（框架过旧 / 未启用模块）[service] 为 null，
 * 调用方应回落到 root 文件通道或直接提示用户。
 */
object XposedConnection {

    private const val TAG = "ExLinkNoBrowser"

    @Volatile
    private var service: XposedService? = null

    private val listeners = CopyOnWriteArrayList<(XposedService?) -> Unit>()

    @Volatile
    private var inited = false

    /** Application.onCreate 里尽早调用：binder 可能在任何时刻送达，晚注册会漏掉。 */
    fun init() {
        if (inited) return
        inited = true
        runCatching {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(svc: XposedService) {
                    service = svc
                    Log.i(TAG, "框架已连接：${name(svc)} ${version(svc)} api=${api(svc)}")
                    listeners.forEach { runCatching { it(svc) } }
                }

                override fun onServiceDied(svc: XposedService) {
                    if (service === svc) service = null
                    Log.w(TAG, "框架连接已断开")
                    listeners.forEach { runCatching { it(null) } }
                }
            })
        }.onFailure { Log.w(TAG, "注册框架服务监听失败", it) }
    }

    fun service(): XposedService? = service

    fun connected(): Boolean = service != null

    fun addListener(listener: (XposedService?) -> Unit) {
        listeners.add(listener)
        listener(service)
    }

    fun removeListener(listener: (XposedService?) -> Unit) {
        listeners.remove(listener)
    }

    fun name(svc: XposedService): String = runCatching { svc.frameworkName }.getOrNull().orEmpty()

    fun version(svc: XposedService): String = runCatching { svc.frameworkVersion }.getOrNull().orEmpty()

    fun api(svc: XposedService): Int = runCatching { svc.apiVersion }.getOrDefault(0)
}
