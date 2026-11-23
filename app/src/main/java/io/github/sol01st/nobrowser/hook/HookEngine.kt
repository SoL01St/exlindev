package io.github.sol01st.nobrowser.hook

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowInsets
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

    /** 宿主侧缓存的远程配置对象，避免每次拦截都重新跨进程取一遍。 */
    private var remotePrefs: android.content.SharedPreferences? = null

    /** 保留模块入口，选择框拖动后要把位置写回同一份远程配置。 */
    @Volatile
    private var moduleRef: XposedModule? = null

    /** 上次加载的配置指纹，内容变了才重新解析。 */
    @Volatile
    private var configSignature = Int.MIN_VALUE

    private var installed = false

    fun onPackageLoaded(module: XposedModule, packageName: String, loader: ClassLoader) {
        if (packageName == Constants.SELF_PACKAGE) return
        moduleRef = module
        val raw = readRawConfig(module)
        val app = decodeRules(raw).firstOrNull { it.packageName == packageName && it.enabled } ?: return
        currentApp = app
        configSignature = signatureOf(raw)
        i("attached to $packageName, rules=${app.rules.size}, ask=${app.askEachTime}")
        install(module, loader)
    }

    /**
     * 配置只在进程启动时读一次的话，用户改完设置而应用一直挂在后台就永远不生效。
     * 这里每次拦截前对一次配置指纹，变了就重新加载。
     */
    @Synchronized
    private fun refreshConfig(module: XposedModule, packageName: String) {
        val raw = readRawConfig(module)
        val signature = signatureOf(raw)
        if (signature == configSignature) return
        configSignature = signature
        currentApp = decodeRules(raw).firstOrNull { it.packageName == packageName && it.enabled }
        i("config reloaded for $packageName, ask=${currentApp?.askEachTime}")
    }

    /**
     * 配置源，按可靠性排序：
     *  1. 框架远程配置 —— 模块 App 通过 XposedService 写入，宿主读的是同一份，零 root，改完即生效；
     *  2. /data/local/tmp/nxb_config.json —— 老通道，只有 root 用户才写得进去，作为兼容保留；
     *  3. 内置默认规则。
     */
    private fun readRawConfig(module: XposedModule): String? {
        val remote = runCatching {
            val prefs = remotePrefs ?: module.getRemotePreferences(Constants.PREFS_NAME)
                .also { remotePrefs = it }
            prefs.getString(Constants.PREFS_KEY_RULES, null)
        }.getOrNull()
        if (!remote.isNullOrBlank()) return remote
        return runCatching {
            val file = java.io.File(CONFIG_PATH)
            if (file.exists() && file.canRead()) file.readText() else null
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun signatureOf(raw: String?): Int =
        raw?.let { it.length * 31 + it.hashCode() } ?: 0

    private fun decodeRules(raw: String?): List<TargetApp> {
        loggingEnabled = RulesCodec.loggingOf(raw)
        val list = RulesCodec.decode(raw ?: DefaultRules.JSON)
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
            // Application context 不够：QQ 换过 Instrumentation，execStartActivity 里还会弹
            // 「即将离开QQ 打开其他应用」的确认框。这里直接走 Binder 调 ActivityTaskManager。
            if (!startExternalDirect(activity, view)) {
                val ctx = activity.applicationContext
                if (ctx != null) ctx.startActivity(view) else activity.startActivity(view)
            }
        }.onFailure { Log.w(TAG, "open external failed", it) }
    }

    /**
     * 直接用 Binder 调 ActivityTaskManager.startActivity 启动外部应用。
     *
     * QQ 把 Instrumentation 换成了自己的实现（execStartActivity 里做「即将离开QQ」确认），
     * 所以 Activity / Application context 的 startActivity 都会被它拦一道；只有绕过
     * 应用层的 Instrumentation、直接走系统服务才能不弹框。
     */
    private fun startExternalDirect(activity: Activity, intent: Intent): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 29) return false
        return runCatching {
            // 宿主进程里非 SDK 接口默认受 hidden API 限制，先尝试解除
            runCatching {
                val vm = Class.forName("dalvik.system.VMRuntime").getMethod("getRuntime").invoke(null)
                vm?.javaClass?.getMethod("setHiddenApiExemptions", Array<String>::class.java)
                    ?.invoke(vm, arrayOf("Landroid/app/", "Landroid/os/", "Landroid/content/"))
            }
            val atm = Class.forName("android.app.ActivityTaskManager")
            val service = atm.getMethod("getService").invoke(null) ?: return false
            val atClass = Class.forName("android.app.ActivityThread")
            val appThread = atClass.getDeclaredField("mAppThread").apply { isAccessible = true }
                .get(atClass.getMethod("currentActivityThread").invoke(null))
            val method = service.javaClass.methods
                .filter { it.name == "startActivity" && it.parameterTypes.size >= 10 }
                .maxByOrNull { it.parameterTypes.size } ?: return false
            var stringSeen = 0
            var intSeen = 0
            val args = method.parameterTypes.map { t ->
                when {
                    t.name == "android.app.IApplicationThread" -> appThread
                    t == Intent::class.java -> intent
                    // 第一个 String 是 callingPackage，其余（callingFeatureId / resultWho）留空
                    t == String::class.java -> if (stringSeen++ == 0) activity.packageName else null
                    // 第一个 int 是 requestCode，第二个是 flags
                    t == Int::class.javaPrimitiveType -> if (intSeen++ == 1) intent.flags else 0
                    t == Long::class.javaPrimitiveType -> 0L
                    t == Boolean::class.javaPrimitiveType -> false
                    else -> null
                }
            }.toTypedArray()
            method.invoke(service, *args)
            Log.i(TAG, "start external via ATM: ${intent.data}")
            true
        }.onFailure { Log.w(TAG, "ATM start failed, fallback to context", it) }.getOrDefault(false)
    }
    /** 默认落点：可用区域中部偏下，单手拇指的自然位置。 */
    private val DEFAULT_CHOOSER_RATIO = 0.5f to 0.62f

    /** 阴影留白：阴影在这个范围内自绘，窗口尺寸 = 胶囊尺寸 + 2 × 留白。 */
    private const val CHOOSER_SHADOW_DP = 10

    /** 未长按时允许的手指抖动范围，超过就判定为「在滑动列表」而不是在按胶囊。 */
    private const val CHOOSER_SLOP_DP = 8

    /** 长按多久后进入拖动；只有长按到点才会拖，不按不拖。 */
    private const val CHOOSER_LONG_PRESS_MS = 250L

    /** 允许拖出屏幕的水平余量。 */
    private const val CHOOSER_OVERSCROLL_DP = 16

    /** 图标显示尺寸。 */
    private const val CHOOSER_ICON_DP = 28

    /** 宿主进程自己的位置副本（每个宿主应用各存一份，模块被冻结时兜底）。 */
    private const val LOCAL_POS_PREFS = "exlindev_chooser_pos"
    private const val LOCAL_POS_KEY = "pos"

    /** 本地值还没成功推给模块时置 true，读取时优先用本地值并后台补推。 */
    private const val LOCAL_PENDING_KEY = "pending"

    /** 选择框位置的读写口：宿主进程写不回远程配置（libxposed 在目标进程里只读），走模块的 provider。 */
    private val chooserPosUri = Uri.parse("content://${Constants.CHOOSER_POS_AUTHORITY}/pos")

    /**
     * 本进程最后已知的位置。
     *
     * 位置存在模块进程的 SharedPreferences 里，宿主进程要经 provider / 远程配置去拿；
     * 模块进程被系统回收、正在被拉起、Binder 不可用时两条路都会失败，此时直接回落
     * [DEFAULT_CHOOSER_RATIO] 就是用户看到的「弹回屏幕中间」。这里记一份本进程最近
     * 一次成功读写的位置，只在所有通道都失败时使用。
     *
     * 注意它不是缓存策略：能读到时永远用读到的值，只在全挂时兜底。
     */
    @Volatile
    private var lastKnownChooserRatio: Pair<Float, Float>? = null

    /** 解析 "rx,ry"；长度不对或不是 0~1 的浮点都算无效。 */
    private fun parseChooserRatio(raw: String?): Pair<Float, Float>? {
        val parts = raw?.split(',') ?: return null
        if (parts.size != 2) return null
        val rx = parts[0].trim().toFloatOrNull() ?: return null
        val ry = parts[1].trim().toFloatOrNull() ?: return null
        if (rx !in 0f..1f || ry !in 0f..1f) return null
        return rx to ry
    }

    /** 宿主进程自己的位置副本。模块进程被 ColorOS 冻结时 provider / 远程配置全部不可用，
     *  这里保证本应用至少还记得它上次看到的位置，不会直接跳回屏幕中间。 */
    private fun localPosPrefs(context: Context) =
        context.getSharedPreferences(LOCAL_POS_PREFS, Context.MODE_PRIVATE)

    /** 诊断输出用到的最后一个宿主 Context。 */
    @Volatile
    private var debugCtx: Context? = null

    /**
     * 诊断输出。
     *
     * proguard 的 -assumenosideeffects 会把 android.util.Log 全部删掉，System.err 在
     * HyperOS 上又看不到，所以同时追加到宿主自己的外部私有目录：
     * /sdcard/Android/data/<宿主包名>/files/nxb_pos.log
     */
    private fun dbg(msg: String) {
        System.err.println("[NXB-POS] " + msg)
        runCatching {
            val ctx = debugCtx ?: return
            val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
            val f = java.io.File(dir, "nxb_pos.log")
            if (f.length() > 300_000L) f.writeText("")
            f.appendText(System.currentTimeMillis().toString() + " " + msg + "\n")
        }
    }

    /** 读成功后同步到内存与宿主本地副本；synced=true 表示这一份已经和全局一致。 */
    private fun rememberRatio(context: Context, ratio: Pair<Float, Float>, synced: Boolean) {
        lastKnownChooserRatio = ratio
        runCatching {
            val edit = localPosPrefs(context).edit()
                .putString(LOCAL_POS_KEY, ratio.first.toString() + "," + ratio.second.toString())
            if (synced) edit.putBoolean(LOCAL_PENDING_KEY, false)
            edit.apply()
        }
    }

    /** 把本地未同步的值在后台补推给模块，成功就清掉 pending。 */
    private fun pushPending(context: Context, raw: String?) {
        if (raw.isNullOrEmpty()) return
        runCatching {
            Thread {
                runCatching {
                    val out = context.contentResolver.call(
                        chooserPosUri,
                        "set",
                        null,
                        Bundle().apply { putString("pos", raw) },
                    )
                    if (out?.getBoolean("ok") == true) {
                        localPosPrefs(context).edit().putBoolean(LOCAL_PENDING_KEY, false).apply()
                        dbg("pending 补推成功 raw=" + raw)
                    } else {
                        dbg("pending 补推失败 raw=" + raw)
                    }
                }
            }.start()
        }
    }

    /**
     * 读选择框位置（可用区域内 0~1 的比例）。
     *
     * 顺序：本应用未同步的本地值 → provider → 远程配置 → 本地副本 → 本进程最后已知值
     * → 默认值。每级独立 try，任一级拿到有效值就返回。
     *
     * 位置的唯一权威副本在模块进程的 SharedPreferences 里，宿主只能经 provider 去拿；
     * HyperOS 的 SmartPower 会冻结模块 UID，那一瞬间 provider / 远程配置都可能失败，
     * 所以末级用本应用上次同步过的本地副本兜底，至少不会跳回屏幕中间。
     */
    private fun loadChooserRatio(context: Context): Pair<Float, Float> {
        debugCtx = context.applicationContext
        val prefs = localPosPrefs(context)
        val localRaw = runCatching { prefs.getString(LOCAL_POS_KEY, null) }.getOrNull()
        val pending = runCatching { prefs.getBoolean(LOCAL_PENDING_KEY, false) }.getOrDefault(false)

        // 0. 本应用拖过但没推给模块：先用本地值，再后台补推
        if (pending) {
            parseChooserRatio(localRaw)?.let { ratio ->
                dbg("pending 本地值优先 host=" + context.packageName + " raw=" + localRaw)
                lastKnownChooserRatio = ratio
                pushPending(context, localRaw)
                return ratio
            }
        }

        // 1. 模块的 provider：位置的唯一权威副本
        val viaProvider = runCatching {
            context.contentResolver.call(chooserPosUri, "get", null, null)?.getString("pos")
        }.onFailure { dbg("provider get 抛异常 host=" + context.packageName + ": " + it) }.getOrNull()
        dbg("provider 返回 raw=" + viaProvider)
        parseChooserRatio(viaProvider)?.let {
            rememberRatio(context, it, synced = true)
            return it
        }

        // 2. 远程配置：provider 挂了但模块进程还在时这条可能还通
        val viaRemote = runCatching {
            (remotePrefs ?: moduleRef?.getRemotePreferences(Constants.PREFS_NAME))
                ?.getString(Constants.PREFS_KEY_CHOOSER_POS, null)
        }.onFailure { dbg("remote prefs 抛异常: " + it) }.getOrNull()
        dbg("remote 返回 raw=" + viaRemote)
        parseChooserRatio(viaRemote)?.let {
            rememberRatio(context, it, synced = true)
            return it
        }

        // 3. 宿主自己的副本（上一次同步过的值）
        parseChooserRatio(localRaw)?.let {
            dbg("回落到本地副本 raw=" + localRaw)
            lastKnownChooserRatio = it
            return it
        }

        // 4. 本进程最后已知位置，最后才用默认值
        val fallback = lastKnownChooserRatio
        dbg("全部失败，fallback=" + (fallback ?: DEFAULT_CHOOSER_RATIO))
        return fallback ?: DEFAULT_CHOOSER_RATIO
    }

    private fun saveChooserRatio(context: Context, rx: Float, ry: Float) {
        debugCtx = context.applicationContext
        val safeX = rx.coerceIn(0f, 1f)
        val safeY = ry.coerceIn(0f, 1f)
        val pos = safeX.toString() + "," + safeY.toString()
        lastKnownChooserRatio = safeX to safeY

        // 先落本地并 commit，同时标记 pending：模块推不动时本应用下次仍记得拖到哪
        runCatching {
            localPosPrefs(context).edit()
                .putString(LOCAL_POS_KEY, pos)
                .putBoolean(LOCAL_PENDING_KEY, true)
                .commit()
        }

        val saved = runCatching {
            val out = context.contentResolver.call(
                chooserPosUri,
                "set",
                null,
                Bundle().apply { putString("pos", pos) },
            )
            out?.getBoolean("ok") == true
        }.onFailure { dbg("provider set 抛异常: " + it) }.getOrDefault(false)
        dbg("save pos=" + pos + " provider ok=" + saved)

        if (saved) {
            // provider 已把值写进 Settings.System，本地不再是 pending
            runCatching {
                localPosPrefs(context).edit().putBoolean(LOCAL_PENDING_KEY, false).apply()
            }
        } else {
            // provider 不可用（模块被冻结等）时退回远程配置，模块进程内自己调用仍能写进去
            val viaRemote = runCatching {
                moduleRef?.getRemotePreferences(Constants.PREFS_NAME)
                    ?.edit()
                    ?.putString(Constants.PREFS_KEY_CHOOSER_POS, pos)
                    ?.commit() == true
            }.onFailure { dbg("remote prefs 写入抛异常: " + it) }.getOrDefault(false)
            dbg("save 回退远程配置 ok=" + viaRemote)
        }
    }

    /**
     * 系统默认浏览器的图标。没设默认浏览器时 resolveActivity 会返回系统 ResolverActivity，
     * 那时拿到的图标和真实点击结果对不上，这里直接返回 null 让调用方回退到通用图标。
     */
    private fun defaultBrowserIcon(activity: Activity): Drawable? = runCatching {
        val pm = activity.packageManager
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val info = pm.resolveActivity(probe, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
        val pkg = info?.packageName
        val name = info?.name ?: ""
        val looksLikeResolver = pkg.isNullOrEmpty() ||
            pkg == "android" ||
            pkg.contains("resolver", ignoreCase = true) ||
            name.contains("Resolver", ignoreCase = true)
        if (looksLikeResolver) null else pm.getApplicationIcon(pkg)
    }.getOrNull()

    /**
     * 横排单胶囊选择框：左段宿主应用图标（= 应用内打开），右段系统默认浏览器图标（= 外部打开）。
     *
     * 阴影不靠 elevation —— 窗口尺寸卡死时 elevation 的阴影会被窗口矩形裁掉，只剩底下一道黑边。
     * 这里照 LinkGo 的做法：View 自己留 10dp padding，在 onDraw 里用 setShadowLayer 画。
     * 圆角取内部高度的一半，所以不论胶囊多高都是正圆端头。
     */
    private fun showChooser(activity: Activity, origin: Intent, url: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        runCatching {
            val dark = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val density = activity.resources.displayMetrics.density
            fun dp(v: Int) = (v * density + 0.5f).toInt()

            val capsuleFill = if (dark) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt()
            val capsuleStroke = if (dark) 0x28FFFFFF else 0x00000000
            val pressFill = if (dark) 0x1AFFFFFF else 0x0F000000
            val shadowColor = if (dark) 0x78000000 else 0x23000000
            val glyphColor = if (dark) 0xFFF5F5F7.toInt() else 0xFF111114.toInt()
            val dividerColor = if (dark) 0x1FFFFFFF else 0x12000000

            val iconPx = dp(CHOOSER_ICON_DP)

            // 把任意 Drawable（含 AdaptiveIcon）压成固定尺寸位图，ImageView 用 CENTER 就不会被放大
            fun rasterize(source: Drawable): android.graphics.Bitmap {
                val bmp = android.graphics.Bitmap.createBitmap(
                    iconPx,
                    iconPx,
                    android.graphics.Bitmap.Config.ARGB_8888,
                )
                val canvas = Canvas(bmp)
                source.setBounds(0, 0, iconPx, iconPx)
                source.draw(canvas)
                return bmp
            }

            val hostIcon = runCatching {
                activity.packageManager.getApplicationIcon(activity.packageName)
            }.getOrNull()
            val hostIconBmp = hostIcon?.let { bmp -> runCatching { rasterize(bmp) }.getOrNull() }

            // 优先用系统默认浏览器的图标，识别不出来才退回自绘的通用浏览器图标
            val browserIcon = defaultBrowserIcon(activity)
            val browserIconBmp = runCatching {
                rasterize(browserIcon ?: BrowserGlyphDrawable(glyphColor))
            }.getOrNull()

            val dialog = Dialog(activity)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

            val card = DragCapsule(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                fillColor = capsuleFill
                strokeColor = capsuleStroke
                shadowColorValue = shadowColor
            }

            /** 胶囊的一半：圆角跟着高度走，只保留朝外两侧，按下时铺一层淡色。 */
            fun segmentIcon(
                bitmap: android.graphics.Bitmap?,
                leftSide: Boolean,
                label: String,
                onClick: () -> Unit,
            ): android.widget.ImageView {
                val bg = HalfCapsuleDrawable(leftSide, 0x00000000, pressFill)
                return android.widget.ImageView(activity).apply {
                    setImageBitmap(bitmap)
                    scaleType = android.widget.ImageView.ScaleType.CENTER
                    contentDescription = label
                    background = bg
                    setPadding(dp(24), dp(10), dp(24), dp(10))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        dialog.dismiss()
                        onClick()
                    }
                }
            }

            val insideSeg = segmentIcon(hostIconBmp, true, "应用内打开") {
                runCatching {
                    val again = Intent(origin).putExtra(Constants.EXTRA_GUARD, true)
                    activity.startActivity(again)
                }.onFailure { Log.w(TAG, "reopen inside failed", it) }
            }
            val externalSeg = segmentIcon(browserIconBmp, false, "浏览器打开") { openExternal(activity, url) }

            val divider = View(activity).apply { setBackgroundColor(dividerColor) }

            card.addView(
                insideSeg,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            card.addView(
                divider,
                LinearLayout.LayoutParams(dp(1), dp(20)).apply { gravity = Gravity.CENTER_VERTICAL },
            )
            card.addView(
                externalSeg,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )

            dialog.setContentView(card)
            dialog.setCanceledOnTouchOutside(true)

            val win = dialog.window ?: return@runCatching
            win.setBackgroundDrawableResource(android.R.color.transparent)
            win.setGravity(Gravity.TOP or Gravity.START)
            win.attributes = win.attributes.apply { dimAmount = 0.10f }

            val bounds = activity.windowManager.currentWindowMetrics.bounds
            val bars = activity.window?.decorView?.rootWindowInsets
                ?.getInsets(WindowInsets.Type.systemBars())
            val screenW = bounds.width()
            // Dialog 的坐标原点在状态栏下方，可用高度要扣掉系统栏
            val availH = (bounds.height() - (bars?.top ?: 0) - (bars?.bottom ?: 0)).coerceAtLeast(1)

            // 先离线测量、把位置算好再 show，否则会从屏幕正中闪一下再跳过来
            card.measure(
                View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            win.setLayout(
                card.measuredWidth.coerceAtLeast(1),
                card.measuredHeight.coerceAtLeast(1),
            )

            val shadowPad = dp(CHOOSER_SHADOW_DP)
            val overscroll = dp(CHOOSER_OVERSCROLL_DP)
            var maxX = (screenW - (card.measuredWidth - shadowPad * 2)).coerceAtLeast(0)
            var maxY = (availH - (card.measuredHeight - shadowPad * 2)).coerceAtLeast(0)

            // 对外一律用胶囊本身的视觉位置，窗口坐标 = 视觉坐标 - shadowPad
            fun applyPos(x: Int, y: Int) {
                val attrs = win.attributes
                attrs.gravity = Gravity.TOP or Gravity.START
                attrs.x = x.coerceIn(-overscroll, maxX + overscroll) - shadowPad
                attrs.y = y.coerceIn(0, maxY) - shadowPad
                win.attributes = attrs
            }

            val (ratioX, ratioY) = loadChooserRatio(activity)
            applyPos((ratioX * maxX).toInt(), (ratioY * maxY).toInt())

            dialog.show()

            // 真正上屏后按实际尺寸再校正一次边界
            card.post {
                maxX = (screenW - (card.width - shadowPad * 2)).coerceAtLeast(0)
                maxY = (availH - (card.height - shadowPad * 2)).coerceAtLeast(0)
                applyPos((ratioX * maxX).toInt(), (ratioY * maxY).toInt())
            }

            var dragStartX = 0
            var dragStartY = 0
            card.onDragStart = {
                dragStartX = win.attributes.x + shadowPad
                dragStartY = win.attributes.y + shadowPad
            }
            card.onDrag = { dx, dy -> applyPos((dragStartX + dx).toInt(), (dragStartY + dy).toInt()) }
            card.onDragEnd = {
                // 松手吸附：拖出边界或贴到边缘 16dp 以内就归位到边上
                val snap = dp(CHOOSER_OVERSCROLL_DP)
                val current = win.attributes
                var x = current.x + shadowPad
                val y = current.y + shadowPad
                if (x <= snap) x = 0 else if (x >= maxX - snap) x = maxX
                applyPos(x, y)
                val rx = if (maxX > 0) x.toFloat() / maxX else DEFAULT_CHOOSER_RATIO.first
                val ry = if (maxY > 0) y.toFloat() / maxY else DEFAULT_CHOOSER_RATIO.second
                saveChooserRatio(activity, rx, ry)
            }
        }.onFailure { Log.w(TAG, "chooser failed", it) }
    }

    /**
     * 可拖动的横排胶囊。
     *
     * 阴影在 [onDraw] 里用 setShadowLayer 自绘（而不是 elevation，后者会被窗口矩形裁成黑边），
     * 外面留 [CHOOSER_SHADOW_DP] 的 padding 当绘制区；圆角取内部高度的一半。
     *
     * 触摸：只有按住 [CHOOSER_LONG_PRESS_MS] 才会进入拖动。没长按就移动只当成「在滑动宿主列表」，
     * 仅取消长按判定、不位移胶囊，这样就不会在别人列表上滑一下就把胶囊拖走。
     */
    private class DragCapsule(context: Context) : LinearLayout(context) {

        var onDragStart: (() -> Unit)? = null
        var onDrag: ((Float, Float) -> Unit)? = null
        var onDragEnd: (() -> Unit)? = null

        private val density = resources.displayMetrics.density
        private val shadowPadPx = (CHOOSER_SHADOW_DP * density + 0.5f).toInt()
        private val dragSlop = (CHOOSER_SLOP_DP * density + 0.5f).toInt()

        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density
        }

        private val capsulePath = Path()
        private val capsuleRect = RectF()

        var fillColor: Int = 0
            set(value) {
                field = value
                bgPaint.color = value
                invalidate()
            }

        var strokeColor: Int = 0
            set(value) {
                field = value
                borderPaint.color = value
                invalidate()
            }

        var shadowColorValue: Int = 0
            set(value) {
                field = value
                // 填充色与阴影同色，避免路径抗锯齿边缘叠出纯黑锯齿（LinkGo 的处理）
                shadowPaint.color = value
                shadowPaint.setShadowLayer(8 * density, 0f, 2.5f * density, value)
                invalidate()
            }

        private var downRawX = 0f
        private var downRawY = 0f
        private var dragging = false

        private val longPressRunnable = Runnable {
            if (!dragging) {
                dragging = true
                onDragStart?.invoke()
                parent?.requestDisallowInterceptTouchEvent(true)
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
        }

        init {
            setWillNotDraw(false)
            clipChildren = false
            clipToPadding = false
            setPadding(shadowPadPx, shadowPadPx, shadowPadPx, shadowPadPx)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            capsuleRect.set(
                paddingLeft.toFloat(),
                paddingTop.toFloat(),
                (w - paddingRight).toFloat(),
                (h - paddingBottom).toFloat(),
            )
            capsulePath.reset()
            val radius = capsuleRect.height() / 2f
            capsulePath.addRoundRect(capsuleRect, radius, radius, Path.Direction.CW)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0 || capsuleRect.height() <= 0f) return
            canvas.drawPath(capsulePath, shadowPaint)
            canvas.drawPath(capsulePath, bgPaint)
            if ((strokeColor ushr 24) != 0) canvas.drawPath(capsulePath, borderPaint)
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    dragging = false
                    removeCallbacks(longPressRunnable)
                    postDelayed(longPressRunnable, CHOOSER_LONG_PRESS_MS)
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!dragging &&
                        (kotlin.math.abs(ev.rawX - downRawX) > dragSlop ||
                            kotlin.math.abs(ev.rawY - downRawY) > dragSlop)
                    ) {
                        // 没长按就滑动：只取消长按判定，绝不位移胶囊
                        removeCallbacks(longPressRunnable)
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(longPressRunnable)
            }
            return dragging
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> if (dragging) onDrag?.invoke(ev.rawX - downRawX, ev.rawY - downRawY)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(longPressRunnable)
                    if (dragging) {
                        dragging = false
                        onDragEnd?.invoke()
                    }
                }
            }
            return true
        }

        override fun onDetachedFromWindow() {
            removeCallbacks(longPressRunnable)
            super.onDetachedFromWindow()
        }
    }

    /**
     * 胶囊的一半：只保留朝外一侧的全圆角，半径按实际高度算，所以胶囊变高变矮都贴合。
     */
    private class HalfCapsuleDrawable(
        private val leftSide: Boolean,
        private val normalColor: Int,
        private val pressedColor: Int,
    ) : Drawable() {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val rect = RectF()
        private var pressed = false
        private var alphaValue = 255

        override fun draw(canvas: Canvas) {
            val h = bounds.height().toFloat()
            if (h <= 0f) return
            val radius = h / 2f
            rect.set(bounds)
            path.reset()
            val radii = if (leftSide) {
                floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
            } else {
                floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
            }
            path.addRoundRect(rect, radii, Path.Direction.CW)
            paint.color = withAlpha(if (pressed) pressedColor else normalColor)
            canvas.drawPath(path, paint)
        }

        private fun withAlpha(color: Int): Int {
            val a = (color ushr 24) * alphaValue / 255
            return (a shl 24) or (color and 0x00FFFFFF)
        }

        override fun isStateful(): Boolean = true

        override fun onStateChange(state: IntArray): Boolean {
            val now = state.contains(android.R.attr.state_pressed)
            if (now != pressed) {
                pressed = now
                invalidateSelf()
            }
            return true
        }

        override fun setAlpha(alpha: Int) {
            if (alphaValue == alpha) return
            alphaValue = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    /**
     * 兜底用的通用浏览器图标：圆角窗口 + 顶部地址栏，单色描边，按 bounds 等比缩放。
     * 只在识别不到系统默认浏览器（例如用户设成「每次询问」）时才会用到。
     */
    private class BrowserGlyphDrawable(private val colorInt: Int) : Drawable() {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = colorInt
        }
        private val rect = RectF()

        override fun draw(canvas: Canvas) {
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val size = minOf(w, h)
            val scale = size / 24f
            val offsetX = bounds.left + (w - size) / 2f
            val offsetY = bounds.top + (h - size) / 2f

            canvas.save()
            canvas.translate(offsetX, offsetY)
            paint.strokeWidth = 1.8f * scale
            val inset = 2.6f * scale
            rect.set(inset, inset, size - inset, size - inset)
            val corner = 4.2f * scale
            canvas.drawRoundRect(rect, corner, corner, paint)
            // 地址栏分隔线
            canvas.drawLine(inset + 0.4f * scale, 9.2f * scale, size - inset - 0.4f * scale, 9.2f * scale, paint)
            canvas.restore()
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }
}
