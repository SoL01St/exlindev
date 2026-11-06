package io.github.sol01st.nobrowser.core

import org.json.JSONArray
import org.json.JSONObject

/** 单条拦截规则：activityName 支持 * / ? 通配，extrasKey 支持 ExDat（intent.data）与 ExAuto（自动嗅探）。 */
data class LinkRule(
    var activityName: String,
    var extrasKey: String,
) {
    fun matches(activity: String): Boolean = globMatch(activityName, activity)

    private fun globMatch(pattern: String, text: String): Boolean {
        if (pattern.isEmpty()) return false
        val sb = StringBuilder()
        for (c in pattern) {
            when (c) {
                '*' -> sb.append(".*")
                '?' -> sb.append('.')
                else -> if (c.isLetterOrDigit() || c == '.' || c == '_' || c == '$') sb.append(c) else sb.append('\\').append(c)
            }
        }
        return runCatching {
            Regex("^" + sb + "\$", RegexOption.IGNORE_CASE).matches(text)
        }.getOrDefault(pattern.equals(text, ignoreCase = true))
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("activityName", activityName)
        put("extrasKey", extrasKey)
    }
}

data class TargetApp(
    var appName: String,
    var packageName: String,
    val rules: MutableList<LinkRule> = mutableListOf(),
    val whiteUrls: MutableList<String> = mutableListOf(),
    var enabled: Boolean = true,
    var builtIn: Boolean = true,
    /** 用户通过 + 号手动添加的应用。 */
    var addedByUser: Boolean = false,
    /** 命中时先弹选择框，而不是直接交给外部浏览器。 */
    var askEachTime: Boolean = true,
    /** 常用应用，列表中优先显示。 */
    var frequent: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("appName", appName)
        put("packageName", packageName)
        put("isUse", enabled)
        put("isUserBuild", !builtIn)
        put("addedByUser", addedByUser)
        put("askEachTime", askEachTime)
        put("frequent", frequent)
        put("rules", JSONArray().apply { rules.forEach { put(it.toJson()) } })
        put("whiteUrl", JSONArray().apply { whiteUrls.forEach { put(it) } })
    }

    companion object {
        fun fromJson(o: JSONObject): TargetApp {
            val rules = mutableListOf<LinkRule>()
            o.optJSONArray("rules")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val r = arr.optJSONObject(i) ?: continue
                    rules.add(LinkRule(r.optString("activityName"), r.optString("extrasKey")))
                }
            }
            val white = mutableListOf<String>()
            o.optJSONArray("whiteUrl")?.let { arr ->
                for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { white.add(it) }
            }
            val pkg = o.optString("packageName")
            return TargetApp(
                appName = o.optString("appName", pkg),
                packageName = pkg,
                rules = rules,
                whiteUrls = white,
                enabled = o.optBoolean("isUse", true),
                builtIn = !o.optBoolean("isUserBuild", false),
                addedByUser = o.optBoolean("addedByUser", false),
                askEachTime = o.optBoolean("askEachTime", true),
                frequent = o.optBoolean("frequent", false),
            )
        }
    }
}

object RulesCodec {
    fun encode(apps: List<TargetApp>): String =
        JSONObject().put("list", JSONArray().apply { apps.forEach { put(it.toJson()) } }).toString()

    fun decode(json: String?): MutableList<TargetApp> {
        val out = mutableListOf<TargetApp>()
        if (json.isNullOrBlank()) return out
        runCatching {
            val root = JSONObject(json)
            val arr = root.optJSONArray("list") ?: JSONArray(json)
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { out.add(TargetApp.fromJson(it)) }
            }
        }
        return out
    }
}

object Constants {
    const val EXTRA_GUARD = "nxb_self_forward"
    const val KEY_EX_DAT = "ExDat"
    const val KEY_EX_AUTO = "ExAuto"
    const val PREFS_NAME = "nxb_config"
    const val PREFS_KEY_RULES = "rules_json"
    const val SELF_PACKAGE = "io.github.sol01st.nobrowser"
    const val LSPD_DB = "/data/adb/lspd/config/modules_config.db"
}
