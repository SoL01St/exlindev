package io.github.sol01st.nobrowser.core

import android.net.Uri

object UrlUtil {

    private val URL_RE = Regex("https?://[\\w\\-._~:/?#\\[\\]@!\$&'()*+,;=%]+")

    /** 链接尾部常见的中英文标点，提取时经常被一起带出来。 */
    private const val TRAILING = ",，.。;；、:：)）]】\"'”’ \t\r\n"

    fun trimTrailing(raw: String): String {
        var s = raw.trim()
        while (s.isNotEmpty() && TRAILING.indexOf(s.last()) >= 0) {
            s = s.dropLast(1)
        }
        return s
    }

    fun findUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return URL_RE.find(text)?.value?.let { trimTrailing(it) }
    }

    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val s = trimTrailing(raw)
        if (s.startsWith("http://") || s.startsWith("https://")) return s
        if (s.startsWith("//")) return "https:" + s
        if (s.startsWith("www.")) return "https://" + s
        return findUrl(s)
    }

    fun hostOf(url: String): String? = runCatching { Uri.parse(url).host }.getOrNull()

    /** 白名单匹配：完全相等或为其子域名。 */
    fun matchWhite(url: String, whiteList: Collection<String>): Boolean {
        val host = hostOf(url)?.lowercase() ?: return false
        for (w in whiteList) {
            val rule = w.trim().lowercase()
            if (rule.isEmpty()) continue
            if (host == rule) return true
            if (host.endsWith("." + rule)) return true
        }
        return false
    }
}
