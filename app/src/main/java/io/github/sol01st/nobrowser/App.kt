package io.github.sol01st.nobrowser

import android.app.Application
import android.util.Log
import io.github.sol01st.nobrowser.data.RouteStore
import io.github.sol01st.nobrowser.data.RuleStore
import io.github.sol01st.nobrowser.data.ScopeManager
import io.github.sol01st.nobrowser.data.XposedConnection
import kotlin.concurrent.thread

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 尽早注册：框架的 binder 通过 XposedProvider 送进来，注册晚了会错过绑定，
        // 之后作用域读写与配置下发都会退回需要 root 的老通道。
        XposedConnection.init()

        // 框架一连上就把规则 / 路由推一次。
        // 用户装完不改设置也能用，宿主进程读到的永远是最新配置。
        XposedConnection.addListener { service ->
            if (service == null) return@addListener
            thread(name = "nxb-publish") {
                runCatching {
                    val ruleStore = RuleStore(this)
                    ScopeManager.publishConfig(this, ruleStore.exportJson(ruleStore.load()))
                    val routeStore = RouteStore(this)
                    ScopeManager.publishRoutes(this, routeStore.exportJson(routeStore.load()))
                }.onFailure { Log.w("ExLinkNoBrowser", "启动时下发配置失败", it) }
            }
        }
    }
}
