package io.github.sol01st.nobrowser.data

import io.github.sol01st.nobrowser.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 检查更新：读 GitHub 仓库最新 release 的 tag_name，与当前 versionName 比较。
 * 仓库地址在这里集中维护，发布前改这一处即可。
 */
object UpdateChecker {

    private const val OWNER_REPO = "SoL01St/exlindev"
    private const val API = "https://api.github.com/repos/$OWNER_REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$OWNER_REPO/releases"

    sealed interface Result {
        data class Newer(val version: String, val url: String) : Result
        data object Latest : Result
        data class Failed(val reason: String) : Result
    }

    fun check(): Result {
        val conn = runCatching {
            (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "nxb/" + BuildConfig.VERSION_NAME)
            }
        }.getOrElse { return Result.Failed(it.message ?: "无法建立连接") }

        return try {
            val code = conn.responseCode
            if (code == 404) return Result.Failed("仓库还没有发布版本")
            if (code != 200) return Result.Failed("HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(body)
            val tag = obj.optString("tag_name").trim().removePrefix("v").removePrefix("V")
            if (tag.isEmpty()) return Result.Failed("响应缺少版本号")
            val url = obj.optString("html_url").ifBlank { RELEASES_PAGE }
            if (isNewer(tag, BuildConfig.VERSION_NAME)) Result.Newer(tag, url) else Result.Latest
        } catch (e: Exception) {
            Result.Failed(e.message ?: "网络错误")
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /** 逐段比较 x.y.z，remote 更大返回 true。 */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val l = local.split('.').map { it.toIntOrNull() ?: 0 }
        repeat(maxOf(r.size, l.size)) { i ->
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
