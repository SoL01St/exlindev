package io.github.sol01st.nobrowser.hook

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
 */
internal object HookEngine {

    private const val TAG = "ExLinkNoBrowser"
    private const val PRIMARY = 0xFF3482FF.toInt()
    private const val CONFIG_PATH = "/data/local/tmp/nxb_config.json"
    private val PROCEED = Any()

    /** 扫一扫/扫码结果页：从这里打开的网页保持在应用内，不参与拦截。 */
    private val SCANNER_HINTS = listOf("plugin.scanner", "scanner.ui", "qrcode", "scanui", "barcode")

    /** ExAuto 优先尝试的常见链接字段，避免把 Intent 里任意一个 http 串当成目标链接。 */
    private val URL_KEYS = listOf(
        "url", "targetUrl", "target_url", "link", "jumpUrl", "jump_url",
        "extraUrl", "webUrl", "redirectUrl", "loadUrl",
    )

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
        Log.i(TAG, "attached to $packageName, rules=${app.rules.size}, ask=${app.askEachTime}")
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
        Log.i(TAG, "config reloaded for $packageName, ask=${currentApp?.askEachTime}")
    }

    private fun loadRules(module: XposedModule): List<TargetApp> {
        // 优先读模块发布的全局配置文件，改完设置杀掉目标应用即可生效
        val fromFile = runCatching {
            val file = java.io.File(CONFIG_PATH)
            if (file.exists() && file.canRead()) file.readText() else null
        }.getOrNull()
        if (!fromFile.isNullOrBlank()) {
            val parsed = RulesCodec.decode(fromFile)
            if (parsed.isNotEmpty()) return parsed
        }
        val remote = runCatching {
            module.getRemotePreferences(Constants.PREFS_NAME)
                .getString(Constants.PREFS_KEY_RULES, null)
        }.getOrNull()
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
            Log.i(TAG, "hooked $name/${params.size}")
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
        val caller = activity.javaClass.name
        val target = intent.component?.className

        // 扫一扫打开的网页留在应用内；从聊天/朋友圈点链接仍然照常拦截
        val callerLower = caller.lowercase()
        if (SCANNER_HINTS.any { callerLower.contains(it) }) {
            Log.d(TAG, "skip(scanner) caller=$caller target=$target")
            return PROCEED
        }

        // 只按目标 Activity 匹配。把发起方也算进来时，「网页里发起任意跳转」都会命中 WebView 通配规则
        val candidates = ArrayList<String>(2)
        if (target != null) candidates.add(target) else candidates.add(caller)

        // 单条规则开关：关掉的规则不参与匹配
        val rules = app.rules.filter { rule ->
            rule.enabled && candidates.any { rule.matches(it) }
        }
        if (rules.isEmpty()) {
            Log.d(TAG, "skip(no-rule) caller=$caller target=$target")
            return PROCEED
        }

        val rawUrl = rules.asSequence()
            .mapNotNull { extractUrl(it, intent, bundle, target) }
            .mapNotNull { UrlUtil.normalize(it) }
            .firstOrNull()
        if (rawUrl == null) {
            Log.d(TAG, "skip(no-url) caller=$caller target=$target keys=${rules.joinToString { it.extrasKey }}")
            return PROCEED
        }

        if (!rawUrl.startsWith("http")) return PROCEED
        val activeWhite = app.whiteUrls.filter { it !in app.disabledWhiteUrls }
        if (UrlUtil.matchWhite(rawUrl, activeWhite)) {
            Log.i(TAG, "whitelisted: $rawUrl")
            return PROCEED
        }

        Log.i(TAG, "intercept caller=$caller target=$target url=$rawUrl")
        if (app.askEachTime) {
            activity.runOnUiThread { showChooser(activity, intent, rawUrl) }
        } else {
            openExternal(activity, rawUrl)
        }
        return null
    }

    /**
     * 取值：优先按规则指定的 key 精确取，取不到自动退化到 ExAuto 全文嗅探，
     * 这样应用改版换了 extras key 也不会直接失效。
     */
    private fun extractUrl(rule: LinkRule, intent: Intent, bundle: Bundle?, target: String?): String? {
        fun sniff(): String? = findInIntent(intent) ?: bundle?.let { findInBundle(it) }
        // 只有目标是 WebView / 浏览器类页面时才做全文嗅探；
        // 否则页面上随便一个 extra 里的 http 串都会把原生跳转误判成打开网页
        fun sniffIfWeb(): String? = if (targetLooksWeb(target)) sniff() else null
        return when (rule.extrasKey) {
            Constants.KEY_EX_DAT -> intent.dataString ?: sniffIfWeb()
            Constants.KEY_EX_AUTO -> intent.dataString
                ?: commonKeyUrl(intent, bundle)
                ?: sniffIfWeb()
            else -> intent.getStringExtra(rule.extrasKey)
                ?: bundle?.getString(rule.extrasKey)
                ?: sniffIfWeb()
        }
    }

    private fun commonKeyUrl(intent: Intent, bundle: Bundle?): String? {
        for (key in URL_KEYS) {
            intent.getStringExtra(key)?.let { UrlUtil.normalize(it) }?.let { return it }
            bundle?.getString(key)?.let { UrlUtil.normalize(it) }?.let { return it }
        }
        return null
    }

    private fun targetLooksWeb(target: String?): Boolean {
        if (target == null) return false
        val t = target.lowercase()
        return t.contains("webview") || t.contains("browser") || t.contains("webactivity")
    }

    private fun findInIntent(intent: Intent): String? {
        intent.dataString?.let { UrlUtil.findUrl(it) }?.let { return it }
        val extras = intent.extras ?: return null
        for (key in extras.keySet()) {
            val value = runCatching { extras.get(key)?.toString() }.getOrNull() ?: continue
            UrlUtil.findUrl(value)?.let { return it }
        }
        return null
    }

    private fun findInBundle(bundle: Bundle): String? {
        for (key in bundle.keySet()) {
            val value = runCatching { bundle.get(key)?.toString() }.getOrNull() ?: continue
            UrlUtil.findUrl(value)?.let { return it }
        }
        return null
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
