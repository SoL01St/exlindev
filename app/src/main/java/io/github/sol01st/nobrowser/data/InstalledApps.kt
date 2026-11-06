package io.github.sol01st.nobrowser.data

import android.content.Context
import android.content.Intent

/** 手机上可启动的应用列表。 */
object InstalledApps {

    data class Item(val label: String, val pkg: String)

    fun load(context: Context): List<Item> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
        return resolved.mapNotNull { it.activityInfo?.applicationInfo?.packageName }
            .distinct()
            .filter { it != context.packageName }
            .mapNotNull { pkg ->
                runCatching {
                    Item(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(), pkg)
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }
}
