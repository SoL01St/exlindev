package io.github.sol01st.nobrowser.core

import android.net.Uri

object UrlUtil {

    private val URL_RE = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")

    /** 链接尾部常见的中英文标点，提取时经常被一起带出来。 */
    private const val TRAILING = ",，.。;；、:：)）]】\"'”’ \t\r\n"

    /** 白名单里的「精确匹配」前缀：=mail.qq.com 表示只匹配这个 host，不含子域。 */
    const val EXACT_PREFIX = "="

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

    fun hostOf(url: String): String? = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase()

    /**
     * 白名单匹配。
     *
     * 默认是「相等或子域名」：配 `qq.com` 会连带放行 `wx.qq.com`、`mail.qq.com` 等所有子域。
     * 加前缀 `=` 则只认这个 host 本身：`=mail.qq.com` 不再匹配 `wx.mail.qq.com`。
     *
     * 需要精确匹配的场景很常见：钉钉、贴吧、QQ 邮箱这些应用会把**外部链接**包在自家域名下中转
     * （如 `wx.mail.qq.com/xmspamcheck/xmsafejump?url=…`），用后缀匹配的白名单等于给所有外链放行。
     */
    fun matchWhite(url: String, whiteList: Collection<String>): Boolean {
        val host = hostOf(url) ?: return false
        for (w in whiteList) {
            val rule = w.trim().lowercase()
            if (rule.isEmpty()) continue
            if (rule.startsWith(EXACT_PREFIX)) {
                val exact = rule.substring(1).trim()
                if (exact.isNotEmpty() && host == exact) return true
                continue
            }
            if (host == rule) return true
            if (host.endsWith("." + rule)) return true
        }
        return false
    }

    // ---------- 外链中转解包 ----------

    /** 严格判断是不是完整 URL：`https%3A%2F%2F…` 这种编码串前 4 个字符也是 http，不能只看前缀。 */
    private fun isHttpUrl(v: String): Boolean =
        v.startsWith("http://", ignoreCase = true) || v.startsWith("https://", ignoreCase = true)

    /** 中转页里可能是多重编码，最多解三层，直到出现 http(s)。 */
    private fun decodeHttp(raw: String?): String? {
        var v: String = raw?.takeIf { it.isNotBlank() } ?: return null
        repeat(3) {
            if (isHttpUrl(v)) return v
            v = runCatching { java.net.URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)
        }
        return if (isHttpUrl(v)) v else null
    }

    private fun queryParam(uri: Uri, key: String): String? =
        runCatching { uri.getQueryParameter(key) }.getOrNull()

    /**
     * 把「应用自家域名下的外链中转页」解包成真实链接。
     * 覆盖：闲鱼（reminderUrl）、QQ 邮箱（xmsafejump?url=）、钉钉（通用兜底）。
     * 解不出来就原样返回，绝不让 URL 变成空。
     */
    fun unwrapRedirect(url: String): String {
        return unwrapIdlefishRedirect(url).takeIf { it != url }
            ?: unwrapQqMailRedirect(url)?.takeIf { it != url }
            ?: unwrapDingTalkRedirect(url)?.takeIf { it != url }
            ?: url
    }

    /**
     * QQ 邮箱的中转：https://wx.mail.qq.com/xmspamcheck/xmsafejump?url=<真实链接>
     * 这个接口只在邮箱 App 内有效，直接拿给浏览器会返回 {"ret":-5002,"stack":"Invalid url"}。
     */
    private fun unwrapQqMailRedirect(url: String): String? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host != "mail.qq.com" && !host.endsWith(".mail.qq.com")) return null
        val path = (uri.path ?: "").lowercase()
        if (!path.contains("xmsafejump") && !path.contains("xmspamcheck")) return null
        return decodeHttp(queryParam(uri, "url"))
    }

    /**
     * 钉钉的外链安全跳转：各家业务线格式不统一，按「钉钉自家域名 + 明确的跳转参数名」兜底。
     */
    private val DINGTALK_HOSTS = listOf("dingtalk.com", "dingtalkapps.com")
    private val REDIRECT_KEYS =
        listOf("url", "target", "link", "jumpUrl", "jump_url", "redirectUrl", "redirect", "to")

    private fun unwrapDingTalkRedirect(url: String): String? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (DINGTALK_HOSTS.none { host == it || host.endsWith(".$it") }) return null
        for (key in REDIRECT_KEYS) {
            val v = decodeHttp(queryParam(uri, key)) ?: continue
            if (v != url) return v
        }
        return null
    }

    /** 已知的「空壳中转页」：拿到手只有页面地址，真实链接不在 URL 参数里。 */
    private val BARE_RELAY_HOSTS = listOf(
        "wx.mail.qq.com", "qr.dingtalk.com", "link.dingtalk.com", "h5.m.goofish.com",
    )

    /** 这个 URL 是不是已知的中转域名（用来排除“中转页自身”被当成真实链接）。 */
    fun isRelayHost(url: String): Boolean {
        val host = hostOf(url) ?: return false
        return BARE_RELAY_HOSTS.any { host == it || host.endsWith(".$it") } ||
            host.endsWith(".mail.qq.com") || host.endsWith(".dingtalk.com") ||
            host.endsWith(".goofish.com")
    }

    /**
     * 这次拿到的 URL 是不是「只有中转页、没有目标参数」。
     * 这类地址交给浏览器一般只能看到错误页或下载页，需要反过来去 Intent 的 extras 里找真实链接。
     */
    fun isBareRelay(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (BARE_RELAY_HOSTS.none { host == it || host.endsWith(".$it") }) return false
        // 能解出真实链接就不算空壳
        return unwrapRedirect(url) == url
    }

    /**
     * 闲鱼的外链中转页：
     *   https://h5.m.goofish.com/wow/moyu/.../url-firewall/pages/reminder?reminderUrl=<真实链接>
     * 直接把外层地址交给浏览器，用户只会看到闲鱼的风险提示页，还得自己长按复制。
     * 这里把 reminderUrl 解出来，交给浏览器时换成真实地址。
     */
    fun unwrapIdlefishRedirect(url: String): String {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        val host = uri.host?.lowercase() ?: return url
        if (host != "goofish.com" && !host.endsWith(".goofish.com")) return url
        val path = (uri.path ?: "").lowercase()
        if (!path.contains("url-firewall") && !path.contains("reminder")) return url
        val target = decodeHttp(queryParam(uri, "reminderUrl")) ?: return url
        return target
    }
}
