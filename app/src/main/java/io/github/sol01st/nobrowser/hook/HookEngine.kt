package io.github.sol01st.nobrowser.hook

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import io.github.sol01st.nobrowser.core.Constants
import io.github.sol01st.nobrowser.core.DefaultRules
import io.github.sol01st.nobrowser.core.LinkRule
import io.github.sol01st.nobrowser.core.RulesCodec
import io.github.sol01st.nobrowser.core.TargetApp
import io.github.sol01st.nobrowser.core.UrlUtil
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

/**
 * 宿主进程内的拦截引擎。这里不能触碰 Compose / 模块 UI 的任何类。
 *
 * 判定模型（入口白名单）：
 * 1. 应用配了 scope=caller 的规则时，只有登记过的发起方页面（聊天、朋友圈、评论区…）才进入判定，
 *    其它入口一律放行——扫一扫、钱包账单、服务栏、生活服务、小程序这些必须在应用内完成的，
 *    永远不需要写排除名单。
 * 2. 应用没有 caller 规则时，退回按目标 Activity 精确匹配（兼容旧配置与暂未录到发起方的应用）。
 * 3. 取 URL 只在指定字段里取，ExAuto 也只在目标是网页容器时才嗅探。
 * 4. 扫码/长按识别二维码按「发起方类名 + 调用栈」识别，直接放行。
 * 5. 交给浏览器前先把应用自家的外链中转页解包成真实地址。
 */
internal object HookEngine {

    private const val TAG = "ExLinkNoBrowser"
    private const val CONFIG_PATH = "/data/local/tmp/nxb_config.json"
    private val PROCEED = Any()

    /**
     * 扫码 / 长按识别二维码的调用链特征。
     * 微信里「聊天或朋友圈点链接」和「长按识别二维码」用的是同一个发起方（LauncherUI / SnsBrowseUI），
     * 光看 caller 区分不开；但识别一定走扫码插件，栈里会出现这些类。
     * QQ 则是发起方自己就叫 DispatchScanResultActivity。
     */
    private val SCAN_STACK_HINTS = listOf(
        "plugin.scanner", "scanner.ui", "scanner", ".qr.", "qrcode", "qrscan",
        "scanui", "barcode", "qbar", "scanresult", "scanactivity", "dispatchscan",
    )

    /** 嗅探时优先看的链接字段：这些 key 明确表达「要打开的网页」，命中就不必再猜。 */
    private val URL_KEYS = listOf(
        "url", "targetUrl", "target_url", "link", "jumpUrl", "jump_url",
        "extraUrl", "webUrl", "redirectUrl", "loadUrl", "rawUrl",
    )

    /** 日志总开关（由配置顶层的 logging 字段下发），关掉后宿主进程不再写判定日志。 */
    @Volatile
    private var loggingEnabled = true

    private fun d(msg: String) {
        if (loggingEnabled) Log.d(TAG, msg)
    }

    private fun i(msg: String) {
        if (loggingEnabled) Log.i(TAG, msg)
    }

    @Volatile
    private var currentApp: TargetApp? = null
    @Volatile
    private var configMtime = -1L
    private var installed = false

    fun onPackageLoaded(module: XposedModule, packageName: String, loader: ClassLoader) {
        if (packageName == Constants.SELF_PACKAGE) return
        val apps = loadRules(module)
        val app = apps.firstOrNull { it.packageName == packageName && it.enabled } ?: return
        currentApp = app
        configMtime = configFileMtime()
        i("attached to $packageName, rules=${app.rules.size}, ask=${app.askEachTime}")
        install(module, loader)
    }

    private fun configFileMtime(): Long = runCatching {
        val file = java.io.File(CONFIG_PATH)
        if (file.exists()) file.lastModified() else 0L
    }.getOrDefault(0L)

    /**
     * 配置只在进程启动时读一次的话，用户改完设置而应用一直挂在后台就永远不生效。
     * 这里每次拦截前看一眼配置文件时间戳，变了就重新加载。
     */
    @Synchronized
    private fun refreshConfig(module: XposedModule, packageName: String) {
        val mtime = configFileMtime()
        if (mtime == configMtime) return
        configMtime = mtime
        val apps = loadRules(module)
        currentApp = apps.firstOrNull { it.packageName == packageName && it.enabled }
        i("config reloaded for $packageName, ask=${currentApp?.askEachTime}")
    }

    private fun loadRules(module: XposedModule): List<TargetApp> {
        // 优先读模块发布的全局配置文件，改完设置杀掉目标应用即可生效
        val fromFile = runCatching {
            val file = java.io.File(CONFIG_PATH)
            if (file.exists() && file.canRead()) file.readText() else null
        }.getOrNull()
        if (!fromFile.isNullOrBlank()) {
            loggingEnabled = RulesCodec.loggingOf(fromFile)
            val parsed = RulesCodec.decode(fromFile)
            if (parsed.isNotEmpty()) return parsed
        }
        val remote = runCatching {
            module.getRemotePreferences(Constants.PREFS_NAME)
                .getString(Constants.PREFS_KEY_RULES, null)
        }.getOrNull()
        loggingEnabled = RulesCodec.loggingOf(remote)
        val list = RulesCodec.decode(remote ?: DefaultRules.JSON)
        return if (list.isEmpty()) RulesCodec.decode(DefaultRules.JSON) else list
    }

    private fun install(module: XposedModule, loader: ClassLoader) {
        if (installed) return
        installed = true
        val activityClass = runCatching { loader.loadClass("android.app.Activity") }.getOrNull() ?: return
        val hooker = buildHooker(module)
        val intent = Intent::class.java
        val intType = Int::class.javaPrimitiveType!!
        val bundle = Bundle::class.java

        hookMethod(module, activityClass, "startActivity", arrayOf(intent), hooker)
        hookMethod(module, activityClass, "startActivity", arrayOf(intent, bundle), hooker)
        hookMethod(module, activityClass, "startActivityForResult", arrayOf(intent, intType), hooker)
        hookMethod(module, activityClass, "startActivityForResult", arrayOf(intent, intType, bundle), hooker)
    }

    private fun hookMethod(
        module: XposedModule,
        clazz: Class<*>,
        name: String,
        params: Array<Class<*>>,
        hooker: XposedInterface.Hooker,
    ) {
        runCatching {
            val method = clazz.getDeclaredMethod(name, *params)
            module.hook(method).intercept(hooker)
            i("hooked $name/${params.size}")
        }.onFailure { Log.w(TAG, "hook $name failed: $it") }
    }

    private fun buildHooker(module: XposedModule) = object : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val activity = chain.getThisObject() as? Activity
            if (activity != null) {
                runCatching { refreshConfig(module, activity.packageName) }
            }
            val app = currentApp ?: return chain.proceed()
            return try {
                val result = handle(app, chain)
                if (result === PROCEED) chain.proceed() else result
            } catch (t: Throwable) {
                Log.w(TAG, "intercept error", t)
                chain.proceed()
            }
        }
    }

    private fun handle(app: TargetApp, chain: XposedInterface.Chain): Any? {
        val activity = chain.getThisObject() as? Activity ?: return PROCEED
        val args = chain.getArgs()
        if (args.isEmpty()) return PROCEED
        val intent = args[0] as? Intent ?: return PROCEED
        if (intent.getBooleanExtra(Constants.EXTRA_GUARD, false)) return PROCEED

        val bundle = args.getOrNull(args.size - 1) as? Bundle
        val pkg = activity.packageName
        val caller = activity.javaClass.name
        val target = intent.component?.className

        val hasCallerPolicy = app.rules.any { it.enabled && it.isCaller() }
        val callerRules = app.callerRules().filter { it.matches(caller) }
        val targetRules = app.targetRules()

        val matched: List<LinkRule>
        if (hasCallerPolicy) {
            // 入口白名单：没登记的发起方一律放行（扫码、钱包账单、服务栏、小程序都走这里）
            if (callerRules.isEmpty()) {
                d("skip(no-entry) pkg=$pkg caller=$caller target=$target")
                return PROCEED
            }
            // 配了目标容器限制时，目标也必须像网页容器，避免入口页面发起的原生跳转被误拦
            if (targetRules.isNotEmpty() && (target == null || targetRules.none { it.matches(target) })) {
                d("skip(target-mismatch) pkg=$pkg caller=$caller target=$target")
                return PROCEED
            }
            matched = callerRules
        } else {
            if (target == null) {
                d("skip(no-target) pkg=$pkg caller=$caller")
                return PROCEED
            }
            matched = targetRules.filter { it.matches(target) }
            if (matched.isEmpty()) {
                d("skip(no-entry) pkg=$pkg caller=$caller target=$target")
                return PROCEED
            }
        }

        // 长按识别二维码打开网页：保持应用内打开，不弹窗
        if (looksLikeScan(caller)) {
            d("skip(scan) pkg=$pkg caller=$caller target=$target")
            return PROCEED
        }

        val rawUrl = matched.asSequence()
            .mapNotNull { extractUrl(it, intent, bundle, target) }
            .mapNotNull { UrlUtil.normalize(it) }
            .firstOrNull()
        if (rawUrl == null) {
            d("skip(no-url) pkg=$pkg caller=$caller target=$target keys=${matched.joinToString { it.extrasKey }}")
            return PROCEED
        }
        if (!rawUrl.startsWith("http")) return PROCEED

        val activeWhite = app.whiteUrls.filter { it !in app.disabledWhiteUrls }
        if (UrlUtil.matchWhite(rawUrl, activeWhite)) {
            i("whitelisted pkg=$pkg url=$rawUrl")
            return PROCEED
        }

        // 闲鱼 / QQ 邮箱 / 钉钉会把外链包在自家域名下跳转，交给浏览器前解包成真实地址，
        // 否则用户在浏览器里只会看到提示页或接口报错（如 QQ 邮箱的 ret -5002 Invalid url）
        var openUrl = UrlUtil.unwrapRedirect(rawUrl)
        if (openUrl != rawUrl) {
            i("unwrap pkg=$pkg url=$rawUrl to=$openUrl")
        } else if (UrlUtil.isBareRelay(rawUrl)) {
            // QQ 邮箱的 xmsafejump、钉钉的 common_webview.html 都是「空壳中转页」：
            // data 里只有页面地址，真实链接在 Intent 的其它字段里，反过去挖一次
            val sniffed = pickRealUrl(intent, bundle, rawUrl)
            i("relay-bare pkg=$pkg url=$rawUrl sniffed=${sniffed ?: "-"} httpKeys=${httpKeysOf(intent, bundle)}")
            if (sniffed != null) openUrl = sniffed
        }
        i("intercept pkg=$pkg caller=$caller target=$target url=$openUrl")

        if (app.askEachTime) {
            activity.runOnUiThread { showChooser(activity, intent, openUrl) }
        } else {
            openExternal(activity, openUrl)
        }
        return null
    }

    /** 这次 startActivity 是不是由「扫码 / 识别二维码」触发的。 */
    private fun looksLikeScan(caller: String): Boolean {
        val c = caller.lowercase()
        if (SCAN_STACK_HINTS.any { c.contains(it) }) return true
        return runCatching {
            Throwable().stackTrace.any { frame ->
                val n = frame.className.lowercase()
                SCAN_STACK_HINTS.any { n.contains(it) }
            }
        }.getOrDefault(false)
    }

    /**
     * 取值策略：
     * - ExDat：只用 intent.data；
     * - ExAuto：data 优先，取不到时在网页容器里嗅探；
     * - 其它：先按指定 key 取，再退化到 intent.data（部分版本的入口只把链接放在 data 上）。
     */
    private fun extractUrl(rule: LinkRule, intent: Intent, bundle: Bundle?, target: String?): String? {
        return when (rule.extrasKey) {
            Constants.KEY_EX_DAT -> intent.dataString
            Constants.KEY_EX_AUTO -> intent.dataString ?: strictSniff(intent, bundle, target)
            else -> intent.getStringExtra(rule.extrasKey)?.takeIf { it.isNotBlank() }
                ?: bundle?.getString(rule.extrasKey)?.takeIf { it.isNotBlank() }
                ?: intent.dataString
        }
    }

    /**
     * 受限嗅探：只对网页容器生效，取值顺序是
     *   1. 常见链接字段（URL_KEYS）——明确表达「要打开哪个网页」，命中即用；
     *   2. 退回 extras / bundle 里第一个 http 串（QQ 邮箱、贴吧这类会把 URL 塞进别的 key）。
     * 不要求「整份 extras 里只有一个链接」：那个约束会把带附加参数的正常跳转判成不可信，
     * 误拦风险改由「入口 caller + 目标 target」双重条件来兜。
     */
    private fun strictSniff(intent: Intent, bundle: Bundle?, target: String?): String? {
        if (!targetLooksWeb(target)) return null
        for (key in URL_KEYS) {
            UrlUtil.findUrl(intent.getStringExtra(key))?.let { return it }
            UrlUtil.findUrl(bundle?.getString(key))?.let { return it }
        }
        intent.extras?.let { extras ->
            for (key in extras.keySet()) {
                UrlUtil.findUrl(runCatching { extras.get(key)?.toString() }.getOrNull())?.let { return it }
            }
        }
        bundle?.let { b ->
            for (key in b.keySet()) {
                UrlUtil.findUrl(runCatching { b.get(key)?.toString() }.getOrNull())?.let { return it }
            }
        }
        return null
    }

    private fun targetLooksWeb(target: String?): Boolean {
        if (target == null) return false
        val t = target.lowercase()
        return t.contains("webview") || t.contains("browser") || t.contains("webactivity") ||
            t.contains("webcontainer") || t.contains("immerse") || t.contains("hybrid")
    }

    /**
     * 空壳中转页兜底：递归挖 Intent / Bundle（含嵌套 Bundle）里的所有链接，
     * 排除中转域名本身和当前 data，优先取名字像链接的字段（url / target / link…）。
     * QQ 邮箱就把真实地址放在 arg_safe_check_params 这个嵌套 Bundle 的 url 里。
     */
    private fun pickRealUrl(intent: Intent, bundle: Bundle?, relayUrl: String): String? {
        val candidates = ArrayList<Pair<String, String>>() // key -> url
        fun walk(prefix: String, b: Bundle?, depth: Int) {
            if (b == null || depth > 3) return
            for (k in b.keySet()) {
                val v = runCatching { b.get(k) }.getOrNull() ?: continue
                if (v is Bundle) {
                    walk("$prefix$k.", v, depth + 1)
                    continue
                }
                val text = runCatching { v.toString() }.getOrNull() ?: continue
                val url = UrlUtil.findUrl(text) ?: continue
                if (url == relayUrl) continue
                if (UrlUtil.isRelayHost(url)) continue
                candidates.add("$prefix$k" to url)
            }
        }
        walk("", intent.extras, 0)
        walk("", bundle, 0)
        if (candidates.isEmpty()) return null
        val preferred = candidates.firstOrNull { (key, _) ->
            key.substringAfterLast('.').lowercase() in URL_KEYS
        }
        return (preferred ?: candidates.first()).second
    }

    /** 诊断用：列出 Intent/Bundle 里含 http 的字段名和值的前 80 个字符。 */
    private fun httpKeysOf(intent: Intent, bundle: Bundle?): String {
        val sb = StringBuilder()
        fun scan(prefix: String, keys: Set<String>, get: (String) -> Any?) {
            for (k in keys) {
                val v = runCatching { get(k)?.toString() }.getOrNull() ?: continue
                if (UrlUtil.findUrl(v) == null) continue
                if (sb.isNotEmpty()) sb.append(',')
                sb.append(prefix).append(k).append('=').append(v.take(80))
            }
        }
        intent.extras?.let { scan("i.", it.keySet()) { k -> it.get(k) } }
        bundle?.let { scan("b.", it.keySet()) { k -> it.get(k) } }
        return if (sb.isEmpty()) "-" else sb.toString()
    }

    private fun openExternal(activity: Activity, url: String) {
        runCatching {
            val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Constants.EXTRA_GUARD, true)
            }
            activity.startActivity(view)
        }.onFailure { Log.w(TAG, "open external failed", it) }
    }

    /** 液态玻璃风格选择框：背景实时模糊 + 半透明卡片。 */
    private fun showChooser(activity: Activity, origin: Intent, url: String) {
        runCatching {
            val dark = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val density = activity.resources.displayMetrics.density
            fun dp(v: Int) = (v * density + 0.5f).toInt()

            val glassColor = if (dark) 0xF21C1C1E.toInt() else 0xF2FFFFFF.toInt()
            val glassStroke = if (dark) 0x40FFFFFF else 0x99FFFFFF.toInt()
            val softFill = if (dark) 0x1FFFFFFF else 0x0F000000
            val softStroke = if (dark) 0x2AFFFFFF else 0x1F000000
            val textColor = if (dark) 0xFFF5F5F7.toInt() else 0xFF111114.toInt()

            val dialog = Dialog(activity)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(30).toFloat()
                    setColor(glassColor)
                    setStroke(dp(1), glassStroke)
                }
            }

            fun option(
                label: String,
                onClick: () -> Unit,
            ) {
                val bg = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(softFill)
                    setStroke(dp(1), softStroke)
                }
                val tv = TextView(activity).apply {
                    text = label
                    textSize = 15.5f
                    gravity = Gravity.CENTER
                    setTextColor(textColor)
                    background = bg
                    setPadding(dp(12), dp(17), dp(12), dp(17))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        dialog.dismiss()
                        onClick()
                    }
                }
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                if (card.childCount > 0) lp.topMargin = dp(9)
                card.addView(tv, lp)
            }

            option("应用内浏览器打开") {
                runCatching {
                    val again = Intent(origin).putExtra(Constants.EXTRA_GUARD, true)
                    activity.startActivity(again)
                }.onFailure { Log.w(TAG, "reopen inside failed", it) }
            }
            option("手机浏览器打开") { openExternal(activity, url) }

            dialog.setContentView(card)
            dialog.setCanceledOnTouchOutside(true)
            dialog.show()

            dialog.window?.let { w ->
                w.setBackgroundDrawableResource(android.R.color.transparent)
                w.setLayout(dp(296), WindowManager.LayoutParams.WRAP_CONTENT)
                val attrs = w.attributes
                attrs.dimAmount = 0.12f
                w.attributes = attrs
            }
        }.onFailure { Log.w(TAG, "dialog failed", it) }
    }
}
