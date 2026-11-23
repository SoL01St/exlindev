package io.github.sol01st.nobrowser.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import io.github.sol01st.nobrowser.core.Constants

/**
 * 选择框位置的跨进程读写通道。
 *
 * 为什么要有它：libxposed 的 `getRemotePreferences()` 在目标进程里是只读的，
 * 宿主进程拖完提示框根本写不回模块配置（写入异常被吞掉，位置永远回到默认）。
 * 这里由模块自己开一个极小的 provider，宿主用 `contentResolver.call()` 同步读写，
 * 模块设置页也可以直接用它复位。
 *
 * 只认 "get" / "set" 两个方法，值只接受两个 0~1 的浮点；写坏了最多是位置不对，没有别的副作用。
 *
 * 这一份是位置的唯一权威副本：宿主读它、拖完写它，不再往 Settings.System 之类的地方同步
 * （HyperOS 不允许非系统应用写 Settings.System 的自定义 key，写只会白白多一次异常）。
 */
class ChooserPosProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        val sp = ctx.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

        return when (method) {
            // 诊断：设置页两个开关读到的真实系统状态（排障用，不影响功能）
            METHOD_DIAG -> Bundle().apply {
                putString(
                    KEY_DIAG,
                    "autoStart=" + KeepAlive.autoStartEnabled(ctx) +
                        " ignoringBattery=" + KeepAlive.ignoringBatteryOptimizations(ctx),
                )
            }

            METHOD_GET -> Bundle().apply {
                putString(KEY_POS, sp.getString(Constants.PREFS_KEY_CHOOSER_POS, null))
            }

            METHOD_SET -> {
                val raw = extras?.getString(KEY_POS)
                val valid = parse(raw) != null
                if (valid && raw != null) {
                    // 同步落盘：宿主下一次弹窗要立刻读到
                    sp.edit().putString(Constants.PREFS_KEY_CHOOSER_POS, raw).commit()
                }
                Bundle().apply { putBoolean(KEY_OK, valid) }
            }

            else -> null
        }
    }

    private fun parse(raw: String?): Pair<Float, Float>? {
        val parts = raw?.split(',') ?: return null
        if (parts.size != 2) return null
        val rx = parts[0].trim().toFloatOrNull() ?: return null
        val ry = parts[1].trim().toFloatOrNull() ?: return null
        if (rx !in 0f..1f || ry !in 0f..1f) return null
        return rx to ry
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        const val AUTHORITY = Constants.CHOOSER_POS_AUTHORITY
        const val METHOD_GET = "get"
        const val METHOD_SET = "set"
        const val METHOD_DIAG = "diag"
        const val KEY_DIAG = "diag"
        const val KEY_POS = "pos"
        const val KEY_OK = "ok"
    }
}
