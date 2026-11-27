package io.github.sol01st.nobrowser.core

import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** LinkGo 规则兼容层：模板变量、短链解析、intent:// 解析。 */
object UrlTransform {

    private val VAR_REGEX = Regex(
        "\\{([a-zA-Z0-9]+)((?:_url_enc|_url_dec|_b64_enc|_b64_dec|_no_scheme|_enc|_dec)*)(?:\\|([^}]*))?\\}",
    )

    /** 去掉 URL 的协议头：https://pan.quark.cn/s/x → pan.quark.cn/s/x */
    private val SCHEME_PREFIX = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*://")
    private val LEFToverDigit = Regex("\\{\\d+\\}")

    /** 主线程最多为一次联网改写等这么久，超时立刻用兜底值，避免卡住宿主界面。 */
    private const val NET_WAIT_MS = 1200L

    /** 同一链接重复点击不必再抓一次落地页（页面内容对所有人一致）。 */
    private val deeplinkCache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 32
    }

    /**
     * 把可能联网的调用挪离主线程。
     *
     * 规则匹配发生在 hook 的 `Activity.startActivity` 里，也就是宿主的**主线程**；
     * 在那里直接开 HttpURLConnection 会抛 NetworkOnMainThreadException，被上层
     * runCatching 吞掉，表现成「规则静默失效」（改写结果退回原 URL）。
     * 这里起后台线程，主线程最多等 [NET_WAIT_MS]，超时就用 fallback。
     */
    private fun <T> offMain(fallback: T, block: () -> T): T {
        val onMain = runCatching { Looper.myLooper() == Looper.getMainLooper() }.getOrDefault(false)
        if (!onMain) return runCatching(block).getOrDefault(fallback)
        val ref = AtomicReference<Any?>(fallback)
        val latch = CountDownLatch(1)
        Thread {
            try {
                ref.set(block())
            } catch (_: Throwable) {
                // 联网失败保持 fallback
            } finally {
                latch.countDown()
            }
        }.apply {
            isDaemon = true
            name = "nxb-urltransform"
            start()
        }
        val finished = runCatching {
            latch.await(NET_WAIT_MS, TimeUnit.MILLISECONDS)
        }.getOrDefault(false)
        if (!finished) return fallback
        @Suppress("UNCHECKED_CAST")
        return ref.get() as T
    }

    /**
     * 把规则模板套到 URL 上。支持 {url} / {0} / {1} / {host} 等变量，
     * 以及 _url_enc / _url_dec / _b64_enc / _b64_dec / _no_scheme 后缀链（与 LinkGo 一致）。
     *
     * 典型用法：
     *   深链把整条链接塞进某个参数时，**必须**做一次 URL 编码，否则内层链接里的 ? & 会
     *   被外层 URI 解析吃掉（夸克、115 这类 scheme 都会因此停在首页而不是目标页）：
     *     qklink://www.uc.cn/<appkey>?action=open_url&url={url_url_enc}
     *   只需要主机+路径（有些 scheme 直接带原始地址）时去掉协议头：
     *     qklink://{url_no_scheme}
     *
     * extractPattern 未命中或最终仍残留数字占位符时返回原 URL，避免拼出非法地址。
     */
    fun applyTemplate(url: String, template: String, extractPattern: String): String {
        if (template.isBlank()) return url
        val vars = mutableMapOf<String, String>()
        vars["url"] = url
        vars["0"] = url
        runCatching {
            val uri = Uri.parse(url)
            vars["host"] = uri.host ?: ""
            vars["path"] = uri.path ?: ""
            vars["query"] = uri.query ?: ""
            vars["scheme"] = uri.scheme ?: ""
        }

        if (extractPattern.isNotBlank()) {
            val match = runCatching {
                Regex(extractPattern, RegexOption.IGNORE_CASE).find(url)
            }.getOrNull() ?: return url
            match.groupValues.forEachIndexed { i, value -> vars[i.toString()] = value }
        }

        // {deeplink}：有些 App 根本不认 http，只认自己的 scheme，而带服务端签名（sig）的真实深链
        // 是静态渲染在落地页 HTML 里的（腾讯元宝就是这样），本地没法构造，只能现场抓一次。
        // 只有模板真的用到这个变量时才发请求，且必须离开主线程（见 offMain）。
        // 支持 `{deeplink|<兜底深链>}`：抓不到时用兜底值，避免退回原 https 链接。
        if (template.contains("{deeplink")) {
            vars["deeplink"] = if (url.startsWith("http", ignoreCase = true)) {
                cachedDeeplink(url)
            } else {
                ""
            }
        }

        var result = VAR_REGEX.replace(template) { m ->
            val raw = vars[m.groupValues[1]].orEmpty()
            // 变量取值为空时用 `|` 后面的兜底文本（{deeplink|yuanbao://chat}）
            val fallback = m.groupValues.getOrNull(3).orEmpty()
            val value = if (raw.isBlank() && fallback.isNotEmpty()) fallback else raw
            applyTransformChain(value, m.groupValues[2])
        }
        result = result
            .replace("{url_encode}", enc(url))
            .replace("{url_decode}", dec(url))
        if (result.isBlank()) return url
        if (LEFToverDigit.containsMatchIn(result)) return url
        return result
    }

    private val DEEPLINK_REGEX = Regex("([a-zA-Z][a-zA-Z0-9+.\\-]*)://[^\\s\"'<>\\\\]{6,}")
    private val NON_APP_SCHEMES = setOf(
        "http", "https", "javascript", "data", "file", "about", "blob",
        "ftp", "ws", "wss", "mailto", "tel", "sms", "chrome-extension",
    )

    /** 带缓存的深链抓取：重复点击同一链接不再联网，也不必再等主线程。 */
    private fun cachedDeeplink(pageUrl: String): String {
        synchronized(deeplinkCache) {
            deeplinkCache[pageUrl]?.let { return it }
        }
        val link = offMain("") { extractDeeplink(pageUrl) }
        if (link.isNotBlank()) {
            synchronized(deeplinkCache) { deeplinkCache[pageUrl] = link }
        }
        return link
    }

    /**
     * 从落地页里抓 App 自有 scheme 的深链。
     *
     * 腾讯元宝的分享页把 `yuanbao://chat?extInfo=<含 sig 的 JSON>` 直接渲染进 HTML，
     * sig 由服务端按内容签发、每个 contentId 都不同，本地无法构造；
     * 但页面本身对所有人返回同一条，所以现场抓下来就能直达那条内容。
     */
    fun extractDeeplink(pageUrl: String): String {
        if (!pageUrl.startsWith("http", ignoreCase = true)) return ""
        val body = fetchBody(pageUrl) ?: return ""
        return DEEPLINK_REGEX.findAll(body)
            .map { it.value.replace("&amp;", "&") }
            .firstOrNull { link ->
                NON_APP_SCHEMES.none { s -> link.startsWith("$s://", ignoreCase = true) }
            }
            ?.trimEnd('.', ',', ')', ';', '\'')
            .orEmpty()
    }

    /** 只读页面前 128KB：落地页的深链都在首屏，没必要整页拉下来。 */
    private fun fetchBody(url: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36",
        )
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        try {
            if (conn.responseCode !in 200..299) return@runCatching null
            val reader = conn.inputStream?.bufferedReader() ?: return@runCatching null
            reader.use {
                val buf = CharArray(128 * 1024)
                val n = it.read(buf)
                if (n <= 0) null else String(buf, 0, n)
            }
        } finally {
            runCatching { conn.disconnect() }
        }
    }.getOrNull()

    private fun applyTransformChain(value: String, chain: String): String {
        if (chain.isBlank()) return value
        var result = value
        var rest = chain
        val known = listOf("_url_enc", "_url_dec", "_b64_enc", "_b64_dec", "_no_scheme", "_enc", "_dec")
        while (rest.isNotEmpty()) {
            val action = known.firstOrNull { rest.startsWith(it) }
            if (action == null) {
                val idx = rest.indexOf('_', 1)
                rest = if (idx >= 0) rest.substring(idx) else ""
                continue
            }
            result = when (action) {
                "_url_enc", "_enc" -> enc(result)
                "_url_dec", "_dec" -> dec(result)
                "_no_scheme" -> SCHEME_PREFIX.replace(result, "")
                "_b64_enc" -> runCatching {
                    Base64.encodeToString(result.toByteArray(), Base64.NO_WRAP)
                }.getOrDefault(result)
                else -> runCatching {
                    String(Base64.decode(result, Base64.NO_WRAP))
                }.getOrDefault(result)
            }
            rest = rest.substring(action.length)
        }
        return result
    }

    private fun enc(v: String): String = runCatching { URLEncoder.encode(v, "UTF-8") }.getOrDefault(v)
    private fun dec(v: String): String = runCatching { URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)

    /**
     * 跟随短链真实地址：3xx Location、200 页面里的 meta refresh / JS location 跳转。
     * 最多 10 跳，单跳 5 秒超时；任何异常都返回当前 URL，不让分发链路中断。
     */
    /** 解析短链（最多 10 跳）。同样必须离开主线程，否则请求会被系统直接拒绝。 */
    fun resolveShortLink(original: String): String {
        if (!original.startsWith("http", ignoreCase = true)) return original
        return offMain(original) { resolveShortLinkBlocking(original) }
    }

    private fun resolveShortLinkBlocking(original: String): String {
        var current = original
        for (step in 0 until 10) {
            val next = runCatching { resolveOnce(current) }.getOrNull() ?: return current
            if (next == current) return current
            current = next
        }
        return current
    }

    private fun resolveOnce(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36",
        )
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        try {
            val code = conn.responseCode
            val location = conn.getHeaderField("Location")
            if (code in 300..399 && !location.isNullOrBlank()) {
                return resolveRelative(url, location)
            }
            if (code == 200) {
                val body = runCatching {
                    conn.inputStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull().orEmpty()
                val meta = Regex(
                    "(?i)<meta[^>]*http-equiv=[\"']?refresh[\"']?[^>]*content=[\"']?[^;]*;?\\s*url=([^\"'> ]+)",
                ).find(body)?.groupValues?.getOrNull(1)
                if (!meta.isNullOrBlank()) return resolveRelative(url, meta)
                val js = Regex(
                    "(?i)(?:window\\.)?location(?:\\.href)?\\s*=\\s*[\"']([^\"']+)[\"']",
                ).find(body)?.groupValues?.getOrNull(1)
                if (!js.isNullOrBlank()) return resolveRelative(url, js)
                if (!location.isNullOrBlank()) return resolveRelative(url, location)
            }
            return url
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    private fun resolveRelative(base: String, ref: String): String = runCatching {
        java.net.URL(java.net.URL(base), ref).toString()
    }.getOrDefault(ref)

    /** intent:// 模板需要用 Intent.parseUri 解析出 extras，普通 Uri.parse 不行。 */
    fun parseIntentUri(uri: String, pkg: String, cls: String?): Intent? {
        if (!uri.startsWith("intent://", ignoreCase = true)) return null
        return runCatching {
            Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).apply {
                if (!cls.isNullOrBlank()) {
                    setClassName(pkg, cls)
                } else if (pkg.isNotBlank()) {
                    setPackage(pkg)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }.getOrNull()
    }
}
