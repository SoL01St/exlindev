package io.github.sol01st.nobrowser.data

/**
 * 模块日志：读取宿主进程里 HookEngine 打出的判定日志。
 *
 * 日志格式（tag 固定 ExLinkNoBrowser）：
 *   D skip(no-entry) pkg=… caller=… target=…
 *   D skip(no-url) pkg=… caller=… target=… keys=ExAuto
 *   D skip(scan) pkg=… caller=… target=…
 *   I whitelisted pkg=… url=https://…
 *   I unwrap pkg=… url=… to=…
 *   I intercept pkg=… caller=… target=… url=https://…
 *
 * 注意 skip 系列是 DEBUG 级别，读的时候必须用 `-s TAG:D`；用 :I 会把它们全过滤掉。
 * pkg 是后加的字段，老日志没有也能解析（显示成未知应用）。
 */
data class ModuleLogEntry(
    /** 打出这条日志的宿主包名。 */
    val packageName: String?,
    /** logcat 行首时间，形如 15:42:28。 */
    val time: String?,
    /** 原始判定结果：intercept / whitelisted / no-entry / no-target / target-mismatch / no-url / scan。 */
    val kind: String,
    val caller: String?,
    val target: String?,
    val url: String?,
    /** skip(no-url) 里回显的取值字段，用于判断是不是字段取错了。 */
    val keys: String?,
) {
    val intercepted: Boolean get() = kind == "intercept"

    val shortCaller: String get() = caller?.substringAfterLast('.') ?: "-"

    val shortTarget: String get() = target?.substringAfterLast('.') ?: "-"

    val host: String? get() = url?.let { ModuleLog.hostOf(it) }

    val reason: String
        get() = when (kind) {
            "intercept" -> "拦截"
            "whitelisted" -> "放行 · 命中白名单"
            "no-entry" -> "放行 · 入口未登记"
            "no-target" -> "放行 · 目标为空"
            "target-mismatch" -> "放行 · 目标不匹配"
            "no-url" -> "放行 · 取不到链接"
            "scan" -> "放行 · 扫码识别"
            "no-route" -> "路由未命中 · 走询问/浏览器"
            "route-failed" -> "目标应用不可用 · 走询问/浏览器"
            "route-hit" -> "路由命中"
            "route-atm" -> "路由 · 系统直连启动成功"
            "route-atm-err" -> "路由 · 系统直连被拒"
            "route-atm-throw" -> "路由 · 系统直连异常"
            "route-ok" -> "路由 · 已发起启动（降级）"
            "route-err" -> "路由 · 启动失败"
            "route-home" -> "路由 · 目标不认该链接，已打开首页"
            "dialog-auto-allow" -> "已自动允许 QQ 离开确认"
            "dialog-no-button" -> "QQ 确认框未识别按钮"
            else -> "放行 · $kind"
        }

    /** 复制 / 导出时的一行长文本。 */
    fun toLine(): String = buildString {
        append(time ?: "--:--:--")
        append('\t').append(reason)
        packageName?.let { append('\t').append(it) }
        caller?.let { append('\t').append(it) }
        target?.let { append(" → ").append(it) }
        url?.let { append('\t').append(it) }
        keys?.let { append("\tkeys=").append(it) }
    }
}

object ModuleLog {

    private const val TAG = "ExLinkNoBrowser"

    private val TIME_RE = Regex("""^\d{2}-\d{2}\s+(\d{2}:\d{2}:\d{2})""")
    private val INTERCEPT_RE = Regex("""intercept(?: pkg=(\S+))? caller=(\S+) target=(\S+) url=(\S+)""")
    private val SKIP_RE = Regex("""skip\(([\w-]+)\)""")

    /**
     * 把 `skip(...)` 之后的 `key=value` 全抓出来。
     * 之前用固定顺序的正则，一旦日志里字段顺序变了或多出字段（比如 rc=），
     * 整行就解析不到，日志页表现成「打了日志但看不见」——这是排查时最容易误判的坑。
     */
    private val KV_RE = Regex("""(\w+)=(\S+)""")
    private val WHITE_NEW_RE = Regex("""whitelisted pkg=(\S+) url=(\S+)""")
    private val WHITE_OLD_RE = Regex("""whitelisted: (\S+)""")
    private val HOST_RE = Regex("""^https?://([^/?#]+)""", RegexOption.IGNORE_CASE)

    fun hostOf(url: String): String? = HOST_RE.find(url)?.groupValues?.get(1)?.lowercase()

    /**
     * 读取最近的模块日志。
     *
     * @return 第一个值表示是否成功取到（false 通常是没拿到 root）；第二个值是解析后的条目，新的在前。
     */
    fun fetch(limitLines: Int = 800): Pair<Boolean, List<ModuleLogEntry>> {
        // 必须是 :D，skip 系列是 DEBUG 级别，用 :I 会把放行原因全过滤掉。
        // 不能用 `-t N`：那个参数取的是「全部日志的最后 N 行」，系统日志动辄十几万行，
        // 模块日志早就被挤出窗口了（实测 -t 3000 只剩 10 行，按 tag 过滤有 54 行）。
        // 所以先按 tag 过滤，再用 tail 截取，保证截到的是模块日志本身。
        val output = RootShell.capture("logcat -d -s $TAG:D | tail -n $limitLines")
            ?: return false to emptyList()
        return true to parse(output).reversed()
    }

    fun parse(text: String): List<ModuleLogEntry> {
        if (text.isBlank()) return emptyList()
        val out = ArrayList<ModuleLogEntry>()
        for (line in text.lineSequence()) {
            if (!line.contains(TAG)) continue
            val time = TIME_RE.find(line)?.groupValues?.get(1)

            val hit = INTERCEPT_RE.find(line)
            if (hit != null) {
                out.add(
                    ModuleLogEntry(
                        packageName = hit.groupValues[1].takeIf { it.isNotBlank() },
                        time = time,
                        kind = "intercept",
                        caller = hit.groupValues[2],
                        target = hit.groupValues[3],
                        url = hit.groupValues[4],
                        keys = null,
                    ),
                )
                continue
            }

            val white = WHITE_NEW_RE.find(line)
            if (white != null) {
                out.add(
                    ModuleLogEntry(
                        packageName = white.groupValues[1],
                        time = time,
                        kind = "whitelisted",
                        caller = null, target = null, url = white.groupValues[2], keys = null,
                    ),
                )
                continue
            }
            val whiteOld = WHITE_OLD_RE.find(line)
            if (whiteOld != null) {
                out.add(
                    ModuleLogEntry(
                        packageName = null, time = time, kind = "whitelisted",
                        caller = null, target = null, url = whiteOld.groupValues[1], keys = null,
                    ),
                )
                continue
            }

            val skip = SKIP_RE.find(line)
            if (skip != null) {
                val rest = line.substring(skip.range.last + 1)
                val kv = KV_RE.findAll(rest).associate { it.groupValues[1] to it.groupValues[2] }
                // 深链本身可能带 &url=…（qklink://…?action=open_url&url=…），KV_RE 会把里面的
                // url 也抓出来并覆盖外层；行尾的 url= 才是本条日志真正的取值。
                val urlTail = Regex(""" url=(\S+)\s*$""").find(rest)?.groupValues?.get(1)
                val detail = buildString {
                    kv["rule"]?.let { append("rule=").append(it) }
                    kv["rc"]?.let { if (isNotEmpty()) append(' '); append("rc=").append(it) }
                    kv["err"]?.let { if (isNotEmpty()) append(' '); append("err=").append(it) }
                    kv["keys"]?.let { if (isNotEmpty()) append(' '); append("keys=").append(it) }
                    kv["texts"]?.let { if (isNotEmpty()) append(' '); append("texts=").append(it) }
                }.takeIf { it.isNotBlank() }
                out.add(
                    ModuleLogEntry(
                        // pkg=route / pkg=dialog 是占位符，不该当成包名显示
                        packageName = kv["pkg"]?.takeIf { it.isNotBlank() && it != "route" && it != "dialog" },
                        time = time,
                        kind = skip.groupValues[1],
                        caller = kv["caller"],
                        target = kv["target"]?.takeIf { it != "null" },
                        url = urlTail ?: kv["url"],
                        keys = detail,
                    ),
                )
            }
        }
        return out
    }

    /** 导出成纯文本。 */
    fun toText(list: List<ModuleLogEntry>): String = buildString {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        appendLine("# 不用内置浏览器打开 · 模块日志")
        appendLine("# 导出时间 $stamp")
        appendLine("# 共 ${list.size} 条（新的在前）")
        appendLine()
        list.forEach { appendLine(it.toLine()) }
    }

    /** 导出文件名。 */
    fun defaultFileName(): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        return "module-log-$stamp.txt"
    }
}
