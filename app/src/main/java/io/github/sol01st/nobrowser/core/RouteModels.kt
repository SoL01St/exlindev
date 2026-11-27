package io.github.sol01st.nobrowser.core

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 目标应用路由规则。
 *
 * 与 LinkRule（拦不拦）不同，这里决定「拦下来之后交给谁」：
 * 命中后把 URL 按模板改写，再用 ACTION_VIEW + 指定包名/组件拉起目标应用。
 *
 * 匹配方式：
 * - HOST：域名精确或子域，多个域名用 | 分隔；
 * - CONTAINS：host 精确/子域或 URL 包含字符串（与 LinkGo 语义一致）；
 * - EXACT：URL 全文精确匹配；
 * - REGEX：正则匹配 URL。
 *
 * resolveStrategy=RE_DISPATCH 的规则改写后不启动应用，而是把结果交给下一轮规则，
 * 用于「京东短链 → 京东」「t.cn → 目标」这类需要先解析或提纯的链路。
 */
data class RouteRule(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var enabled: Boolean = true,
    var matchType: String = MATCH_HOST,
    var pattern: String = "",
    /** 目标包名，支持 "包名/.Activity" 指定显式组件；分发规则可留空。 */
    var targetPackage: String = "",
    /** URL 改写模板，支持 {url}、{1} 等变量与后缀变换；留空表示使用原 URL。 */
    var urlTemplate: String = "",
    /** 从 URL 中提取捕获组的正则，供 {1} / {2} 等变量使用。 */
    var extractPattern: String = "",
    /** 跳转前是否跟随 302 / meta refresh / JS 跳转解析真实地址。 */
    var resolveShortLink: Boolean = false,
    var resolveStrategy: String = STRATEGY_NONE,
    /** 来自 LinkGo 配置导入。 */
    var fromLinkGo: Boolean = false,
    /** 非空表示导入时判定为当前版本无法安全执行，已自动禁用；内容为用户可读原因。 */
    var disabledReason: String = "",
    val sourcePackages: MutableList<String> = mutableListOf(),
) {
    val autoDisabled: Boolean get() = disabledReason.isNotBlank()

    fun matches(url: String, callerPkg: String): Boolean {
        if (!enabled || autoDisabled || pattern.isBlank()) return false
        if (sourcePackages.isNotEmpty() && callerPkg !in sourcePackages) return false
        return when (matchType.uppercase()) {
            MATCH_REGEX -> runCatching {
                Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(url)
            }.getOrDefault(false)

            MATCH_EXACT -> url.equals(pattern, ignoreCase = true)

            MATCH_CONTAINS -> {
                val host = runCatching { Uri.parse(url).host }.getOrNull()
                pattern.split('|').map { it.trim() }.filter { it.isNotBlank() }.any { p ->
                    (host != null && (host.equals(p, ignoreCase = true) ||
                        host.endsWith(".$p", ignoreCase = true))) ||
                        url.contains(p, ignoreCase = true)
                }
            }

            else -> {
                val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
                pattern.split('|').map { it.trim().lowercase() }.filter { it.isNotBlank() }
                    .any { h -> host == h || host.endsWith(".$h") }
            }
        }
    }

    /** 按模板生成最终 URL（不含短链解析，那一步在分发循环里做）。 */
    fun rewrite(url: String): String = UrlTransform.applyTemplate(url, urlTemplate, extractPattern)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("enabled", enabled)
        put("matchType", matchType)
        put("pattern", pattern)
        put("targetPackage", targetPackage)
        put("urlTemplate", urlTemplate)
        put("extractPattern", extractPattern)
        put("resolveShortLink", resolveShortLink)
        put("resolveStrategy", resolveStrategy)
        put("fromLinkGo", fromLinkGo)
        put("disabledReason", disabledReason)
        put("sourcePackages", JSONArray().apply { sourcePackages.forEach { put(it) } })
    }

    companion object {
        const val MATCH_HOST = "HOST"
        const val MATCH_CONTAINS = "CONTAINS"
        const val MATCH_EXACT = "EXACT"
        const val MATCH_REGEX = "REGEX"

        const val STRATEGY_NONE = "NONE"
        const val STRATEGY_DIRECT = "DIRECT"
        const val STRATEGY_RE_DISPATCH = "RE_DISPATCH"

        fun fromJson(o: JSONObject): RouteRule? {
            val pattern = o.optString("pattern")
            if (pattern.isBlank()) return null
            val sources = mutableListOf<String>()
            o.optJSONArray("sourcePackages")?.let { arr ->
                for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { sources.add(it) }
            }
            return RouteRule(
                id = o.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                name = o.optString("name"),
                enabled = o.optBoolean("enabled", true),
                matchType = o.optString("matchType").takeIf { it.isNotBlank() } ?: MATCH_HOST,
                pattern = pattern,
                targetPackage = o.optString("targetPackage"),
                urlTemplate = o.optString("urlTemplate"),
                extractPattern = o.optString("extractPattern"),
                resolveShortLink = o.optBoolean("resolveShortLink", false),
                resolveStrategy = o.optString("resolveStrategy").takeIf { it.isNotBlank() } ?: STRATEGY_NONE,
                fromLinkGo = o.optBoolean("fromLinkGo", false),
                disabledReason = o.optString("disabledReason"),
                sourcePackages = sources,
            )
        }
    }
}

object RoutesCodec {
    const val SCHEMA_VERSION = 2

    fun encode(rules: List<RouteRule>): String = JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("list", JSONArray().apply { rules.forEach { put(it.toJson()) } })
        .toString()

    fun decode(json: String?): MutableList<RouteRule> {
        val out = mutableListOf<RouteRule>()
        if (json.isNullOrBlank()) return out
        runCatching {
            val root = runCatching { JSONObject(json) }.getOrNull()
            val arr = root?.optJSONArray("list") ?: JSONArray(json)
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { RouteRule.fromJson(it)?.let(out::add) }
            }
        }
        return out
    }
}
