package io.github.sol01st.nobrowser.data

import android.content.Context
import io.github.sol01st.nobrowser.core.Constants
import io.github.sol01st.nobrowser.core.DefaultRules
import io.github.sol01st.nobrowser.core.RulesCodec
import io.github.sol01st.nobrowser.core.TargetApp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 规则仓库。写入的 SharedPreferences 会被 Xposed 侧以
 * getRemotePreferences(Constants.PREFS_NAME) 读取，名字必须一致。
 */
class RuleStore(context: Context) {

    private val app = context.applicationContext
    private val sp = app.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val backupDir = File(app.filesDir, "backups")

    private val DEFAULT_ORDER = listOf(
        "com.tencent.mobileqq",
        "com.tencent.mm",
        "com.tencent.androidqqmail",
        "com.sina.weibo",
        "com.baidu.tieba",
        "com.alibaba.android.rimet",
        "com.tencent.tim",
        "tv.danmaku.bili",
        "com.xingin.xhs",
    )

    fun defaultApps(): List<TargetApp> {
        val all = RulesCodec.decode(DefaultRules.JSON).filter { it.frequent }
        return all.sortedBy { entry ->
            val index = DEFAULT_ORDER.indexOf(entry.packageName)
            if (index >= 0) index else Int.MAX_VALUE
        }
    }

    fun isDefaultApp(packageName: String): Boolean = packageName in DEFAULT_ORDER

    fun load(): MutableList<TargetApp> {
        val saved = sp.getString(Constants.PREFS_KEY_RULES, null)
        if (saved.isNullOrBlank()) {
            val list = defaultApps().toMutableList()
            save(list)
            return list
        }
        val list = RulesCodec.decode(saved)
        val defaultPkgs = defaultApps().map { it.packageName }.toHashSet()
        // 老版本会把所有内置模板合并进列表，这里只保留默认 8 个与用户手动添加的
        list.removeAll { it.packageName !in defaultPkgs && !it.addedByUser }
        // 内置默认项补全，避免版本升级后缺项
        val known = list.map { it.packageName }.toHashSet()
        val defaults = defaultApps()
        defaults.forEach { if (known.add(it.packageName)) list.add(it) }
        // 默认应用按固定顺序排在最前，其余保持添加顺序
        val order = defaults.map { it.packageName }
        list.sortWith(
            compareBy { entry ->
                val index = order.indexOf(entry.packageName)
                if (index >= 0) index else Int.MAX_VALUE
            },
        )
        return list
    }

    fun templateFor(packageName: String): TargetApp? =
        RulesCodec.decode(DefaultRules.JSON).firstOrNull { it.packageName == packageName }

    fun save(list: List<TargetApp>) {
        sp.edit().putString(Constants.PREFS_KEY_RULES, RulesCodec.encode(list)).apply()
    }

    fun exportJson(list: List<TargetApp>): String = RulesCodec.encode(list)

    fun importJson(text: String): MutableList<TargetApp>? {
        val list = RulesCodec.decode(text)
        return if (list.isEmpty()) null else list
    }

    fun resetToDefault(): MutableList<TargetApp> {
        val list = defaultApps().toMutableList()
        save(list)
        return list
    }

    fun backup(list: List<TargetApp>): File {
        backupDir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(backupDir, "rules_$stamp.json")
        file.writeText(exportJson(list))
        return file
    }

    fun latestBackup(): File? =
        backupDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.maxByOrNull { it.lastModified() }

    fun backupCount(): Int =
        backupDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.size ?: 0
}
