package io.github.sol01st.nobrowser.data

import io.github.sol01st.nobrowser.core.RouteRule
import org.json.JSONArray
import org.json.JSONObject

/**
 * 把 LinkGo 导出的 linkgo_rules JSON 转成当前模块可执行的路由规则。
 *
 * 当前版本完整支持：REGEX / CONTAINS / EXACT 匹配、正则捕获组、模板后缀变换、
 * 短链 302 解析、RE_DISPATCH 多轮分发、intent:// 模板、显式组件。
 * 仅窗口模式 / 预热 / 跳转后复制等 LinkGo 专有项会忽略，不影响跳转本身。
 */
object LinkGoRuleImporter {

    data class Result(
        val rules: List<RouteRule>,
        val disabled: List<String>,
        val total: Int,
        val error: String? = null,
    )

    fun parse(json: String): Result {
        val arr = runCatching {
            val root = JSONObject(json)
            root.optJSONArray("linkgo_rules") ?: root.optJSONArray("rules")
        }.getOrNull() ?: runCatching { JSONArray(json) }.getOrNull()
            ?: return Result(emptyList(), emptyList(), 0, "不是有效的 LinkGo 规则文件")

        val rules = mutableListOf<RouteRule>()
        val disabled = mutableListOf<String>()

        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").ifBlank { "未命名规则 #${i + 1}" }
            val rule = RouteRule(
                id = o.optString("id").takeIf { it.isNotBlank() }
                    ?: java.util.UUID.randomUUID().toString(),
                name = name,
                enabled = o.optBoolean("isEnabled", true),
                matchType = normalizeMatchType(o.optString("matchType")),
                pattern = o.optString("pattern"),
                targetPackage = o.optString("targetPackage"),
                urlTemplate = o.optString("template"),
                extractPattern = o.optString("extractPattern"),
                resolveShortLink = o.optBoolean("resolveShortLink", false),
                resolveStrategy = normalizeStrategy(o.optString("resolveStrategy")),
                fromLinkGo = true,
            )

            val reason = validate(rule)
            if (reason != null) {
                rule.enabled = false
                rule.disabledReason = reason
                disabled += "$name（$reason）"
            }
            rules += rule
        }

        return Result(rules, disabled, rules.size)
    }

    private fun normalizeMatchType(raw: String): String = when (raw.uppercase()) {
        "REGEX" -> RouteRule.MATCH_REGEX
        "CONTAINS" -> RouteRule.MATCH_CONTAINS
        "EXACT" -> RouteRule.MATCH_EXACT
        else -> RouteRule.MATCH_HOST
    }

    private fun normalizeStrategy(raw: String): String = when (raw.uppercase()) {
        "RE_DISPATCH", "2" -> RouteRule.STRATEGY_RE_DISPATCH
        "DIRECT", "1" -> RouteRule.STRATEGY_DIRECT
        else -> RouteRule.STRATEGY_NONE
    }

    /** 返回 null 表示规则可执行；否则返回给用户看的自动禁用原因。 */
    private fun validate(rule: RouteRule): String? {
        if (rule.pattern.isBlank()) return "匹配内容为空"
        if (rule.matchType !in setOf(
                RouteRule.MATCH_HOST,
                RouteRule.MATCH_CONTAINS,
                RouteRule.MATCH_EXACT,
                RouteRule.MATCH_REGEX,
            )
        ) {
            return "不支持的匹配方式"
        }
        if (rule.extractPattern.isNotBlank()) {
            val ok = runCatching { Regex(rule.extractPattern) }.isSuccess
            if (!ok) return "提取正则无效"
        }
        if (Regex("\\{\\d+\\}").containsMatchIn(rule.urlTemplate) && rule.extractPattern.isBlank()) {
            return "模板引用了捕获组但没有提取正则"
        }
        if (rule.targetPackage.isNotBlank()) {
            val parts = rule.targetPackage.split("/")
            if (parts.isEmpty() || parts[0].isBlank() || !parts[0].contains(".")) {
                return "目标包名格式无效"
            }
            if (parts.size > 2 || (parts.size == 2 && parts[1].isBlank())) {
                return "目标组件格式无效"
            }
        } else if (rule.resolveStrategy != RouteRule.STRATEGY_RE_DISPATCH) {
            return "没有目标应用，且不是分发规则"
        }
        if (rule.resolveStrategy == RouteRule.STRATEGY_RE_DISPATCH &&
            rule.urlTemplate.isBlank() && !rule.resolveShortLink
        ) {
            return "分发规则既无改写模板也不解析短链"
        }
        return null
    }
}
