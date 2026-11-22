package io.github.sol01st.nobrowser.data

import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.sol01st.nobrowser.core.Constants
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * LSPosed 作用域与配置读写，全部走框架 service（libxposed API 102），不需要 root。
 *
 * 之前的实现是 root + 自带 sqlite3 直接改 /data/adb/lspd/config/modules_config.db，
 * 一旦没授权 root 就整条链路失效；现在改用框架提供的接口：
 *  - [readScope] → `XposedService.getScope()`
 *  - [setScope] / [syncAll] → `requestScope()` / `removeScope()`
 *  - [publishConfig] → `getRemotePreferences()`，宿主侧用同名 group 读取
 *
 * 框架 service 不可用时这些方法返回 null / false，调用方负责提示用户。
 */
object ScopeManager {

    private const val TAG = "ExLinkNoBrowser"

    /** 等待框架批准作用域申请的最长时间。 */
    private const val SCOPE_TIMEOUT_SEC = 6L

    /** 框架是否已连接；false 时下面所有操作都会失败。 */
    fun available(): Boolean = XposedConnection.connected()

    /** 读取当前勾选的作用域；框架不可用返回 null。 */
    fun readScope(): Set<String>? {
        val svc = XposedConnection.service() ?: return null
        return runCatching { svc.scope.toSet() }.getOrElse { e ->
            Log.w(TAG, "读取作用域失败", e)
            null
        }
    }

    /** 勾选 / 取消一个应用的作用域。 */
    fun setScope(packageName: String, enabled: Boolean): Boolean {
        val svc = XposedConnection.service() ?: return false
        val current = runCatching { svc.scope.toSet() }.getOrNull() ?: return false
        return runCatching {
            if (enabled) {
                if (packageName in current) return true
                requestScope(svc, listOf(packageName))
            } else {
                if (packageName !in current) return true
                svc.removeScope(listOf(packageName))
                true
            }
        }.getOrElse { e ->
            Log.w(TAG, "设置作用域失败：$packageName", e)
            false
        }
    }

    /** 把作用域整体同步成 [packages]（多退少补）。 */
    fun syncAll(packages: Collection<String>): Boolean {
        val svc = XposedConnection.service() ?: return false
        val current = runCatching { svc.scope.toSet() }.getOrNull() ?: return false
        val target = packages.toSet()
        val remove = current - target
        val add = target - current
        return runCatching {
            if (remove.isNotEmpty()) svc.removeScope(remove.toList())
            if (add.isEmpty()) return true
            requestScope(svc, add.toList())
        }.getOrElse { e ->
            Log.w(TAG, "同步作用域失败", e)
            false
        }
    }

    /**
     * 下发规则配置。
     *
     * 首选框架的远程配置：写进 `nxb_config` 这个 group，宿主进程用
     * `getRemotePreferences("nxb_config")` 读到的是同一份数据，全程不需要 root。
     * 框架不可用时才回落到老的文件通道（需要 root）。
     */
    fun publishConfig(context: android.content.Context, json: String): Boolean {
        val svc = XposedConnection.service()
        if (svc != null) {
            val ok = runCatching {
                svc.getRemotePreferences(Constants.PREFS_NAME)
                    .edit()
                    .putString(Constants.PREFS_KEY_RULES, json)
                    .commit()
            }.getOrElse { e ->
                Log.w(TAG, "写入框架远程配置失败", e)
                false
            }
            if (ok) return true
        }
        Log.w(TAG, "框架远程配置不可用，回落到 root 文件通道")
        return RootShell.publishConfig(context, json)
    }

    /** 申请把包加入作用域，等框架回调（LSPosed 一般直接批准）。 */
    private fun requestScope(svc: XposedService, packages: List<String>): Boolean {
        val latch = CountDownLatch(1)
        var approved = false
        val listener = object : XposedService.OnScopeEventListener {
            override fun onScopeRequestApproved(approvedPackages: List<String>) {
                approved = true
                latch.countDown()
            }

            override fun onScopeRequestFailed(message: String) {
                Log.w(TAG, "作用域申请被拒绝：$message")
                approved = false
                latch.countDown()
            }
        }
        svc.requestScope(packages, listener)
        val finished = runCatching { latch.await(SCOPE_TIMEOUT_SEC, TimeUnit.SECONDS) }
            .getOrDefault(false)
        if (!finished) {
            Log.w(TAG, "作用域申请超时：$packages")
            return false
        }
        return approved
    }
}
