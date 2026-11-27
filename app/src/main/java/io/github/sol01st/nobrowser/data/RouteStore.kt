package io.github.sol01st.nobrowser.data

import android.content.Context
import io.github.sol01st.nobrowser.core.RouteRule
import io.github.sol01st.nobrowser.core.RoutesCodec

/** 目标应用路由规则的本地仓库。发布到 /data/local/tmp/nxb_routes.json 后由 Hook 侧读取。 */
class RouteStore(context: Context) {

    private val app = context.applicationContext
    private val sp = app.getSharedPreferences("nxb_routes", Context.MODE_PRIVATE)

    fun load(): MutableList<RouteRule> = RoutesCodec.decode(sp.getString(KEY, null))

    fun save(list: List<RouteRule>) {
        // 与 RuleStore 一致用同步写，避免进程被回收时丢配置
        sp.edit().putString(KEY, RoutesCodec.encode(list)).commit()
    }

    fun exportJson(list: List<RouteRule>): String = RoutesCodec.encode(list)

    fun importJson(text: String): MutableList<RouteRule>? {
        val list = RoutesCodec.decode(text)
        return if (list.isEmpty()) null else list
    }

    private companion object {
        const val KEY = "routes_json"
    }
}
