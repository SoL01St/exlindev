package io.github.sol01st.nobrowser.data

import io.github.sol01st.nobrowser.core.RouteRule
import io.github.sol01st.nobrowser.core.RoutesCodec
import io.github.sol01st.nobrowser.core.RulesCodec
import io.github.sol01st.nobrowser.core.TargetApp
import org.json.JSONObject

/**
 * 应用规则 + 目标应用路由的复合备份/导出格式。
 *
 * 新格式：{"format":"nxb_bundle","apps":{...RulesCodec...},"routes":{...RoutesCodec...}}
 * 旧格式：直接是 RulesCodec 的输出（顶层含 list），decoded 后 routes 为空，保持兼容。
 */
object BackupBundle {

    data class Payload(
        val apps: MutableList<TargetApp>,
        val routes: MutableList<RouteRule>,
    )

    fun encode(apps: List<TargetApp>, routes: List<RouteRule>, logging: Boolean = true): String =
        JSONObject()
            .put("format", "nxb_bundle")
            .put("version", 1)
            .put("apps", JSONObject(RulesCodec.encode(apps, logging)))
            .put("routes", JSONObject(RoutesCodec.encode(routes)))
            .toString()

    fun decode(text: String): Payload? {
        if (text.isBlank()) return null
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val isBundle = root.optString("format") == "nxb_bundle"
        val appsJson = if (isBundle) root.optJSONObject("apps")?.toString() else text
        val apps = RulesCodec.decode(appsJson)
        val routes = if (isBundle) {
            RoutesCodec.decode(root.optJSONObject("routes")?.toString())
        } else {
            mutableListOf()
        }
        return Payload(apps, routes)
    }
}
