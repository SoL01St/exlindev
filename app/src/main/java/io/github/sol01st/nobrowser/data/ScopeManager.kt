package io.github.sol01st.nobrowser.data

import android.content.Context
import android.util.Log
import io.github.sol01st.nobrowser.core.Constants
import java.io.File

/**
 * 通过 root 维护 LSPosed 的作用域配置，并记录每次权限使用。
 */
object ScopeManager {

    private const val TAG = "ExLinkNoBrowser"
    private const val DIR = "nxbin"
    private val LIBS = listOf(
        "libz.so.1",
        "libreadline.so.8",
        "libncursesw.so.6",
        "libtinfo.so.6",
        "libandroid-support.so",
    )
    private val SU_PATHS = listOf("su", "/system/bin/su", "/system/xbin/su", "/sbin/su")

    @Volatile
    private var prepared = false

    fun prepare(context: Context): File? {
        val dir = File(context.filesDir, DIR)
        val bin = File(dir, "sqlite3")
        if (prepared && bin.exists()) return bin
        runCatching {
            dir.mkdirs()
            val libDir = File(dir, "lib")
            libDir.mkdirs()
            if (!bin.exists()) copyAsset(context, "nxbin/sqlite3", bin)
            LIBS.forEach { name ->
                val target = File(libDir, name)
                if (!target.exists()) copyAsset(context, "nxbin/lib/$name", target)
            }
            bin.setExecutable(true, false)
            libDir.listFiles()?.forEach { it.setReadable(true, false) }
            prepared = true
        }.onFailure { Log.w(TAG, "prepare sqlite failed", it) }
        return if (bin.exists()) bin else null
    }

    private fun copyAsset(context: Context, asset: String, target: File) {
        context.assets.open(asset).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    /** 写入/移除作用域，并保证模块本身处于启用状态。 */
    fun setScope(context: Context, packageName: String, enabled: Boolean): Boolean {
        val bin = prepare(context) ?: return false
        val libDir = File(bin.parentFile, "lib").absolutePath
        val self = Constants.SELF_PACKAGE
        val sql = buildString {
            append("INSERT OR REPLACE INTO modules_state(module_pkg_name,user_id,enabled,scope_request_blocked) VALUES('$self',0,1,0);")
            if (enabled) {
                append("INSERT OR IGNORE INTO scope(module_pkg_name,app_pkg_name,user_id) VALUES('$self','$packageName',0);")
            } else {
                append("DELETE FROM scope WHERE module_pkg_name='$self' AND app_pkg_name='$packageName';")
            }
        }
        val cmd = "LD_LIBRARY_PATH=$libDir ${bin.absolutePath} ${Constants.LSPD_DB} \"$sql\""
        val ok = runSu(cmd)
        return ok
    }

    fun syncAll(context: Context, packages: Collection<String>): Boolean {
        val bin = prepare(context) ?: return false
        val libDir = File(bin.parentFile, "lib").absolutePath
        val self = Constants.SELF_PACKAGE
        val sql = buildString {
            append("INSERT OR REPLACE INTO modules_state(module_pkg_name,user_id,enabled,scope_request_blocked) VALUES('$self',0,1,0);")
            append("DELETE FROM scope WHERE module_pkg_name='$self';")
            packages.forEach {
                append("INSERT OR IGNORE INTO scope(module_pkg_name,app_pkg_name,user_id) VALUES('$self','$it',0);")
            }
        }
        val cmd = "LD_LIBRARY_PATH=$libDir ${bin.absolutePath} ${Constants.LSPD_DB} \"$sql\""
        val ok = runSu(cmd)
        return ok
    }

    /** 读取 LSPosed 里当前勾选的作用域。 */
    fun readScope(context: Context): Set<String>? {
        val bin = prepare(context) ?: return null
        val libDir = File(bin.parentFile, "lib").absolutePath
        val self = Constants.SELF_PACKAGE
        val cmd = "LD_LIBRARY_PATH=$libDir ${bin.absolutePath} ${Constants.LSPD_DB} " +
            "\"select app_pkg_name from scope where module_pkg_name='$self';\""
        val out = runSuCapture(cmd) ?: return null
        val set = out.lines().map { it.trim() }.filter { it.isNotEmpty() }.toHashSet()
        return set
    }

    fun restartPackage(packageName: String) {
        if (packageName == Constants.SELF_PACKAGE) return
        runSu("am force-stop $packageName")
    }

    /**
     * 把当前规则发布到 /data/local/tmp/nxb_config.json。
     * Xposed 侧优先读这个文件，避免 remote preferences 不刷新的问题。
     */
    fun publishConfig(context: Context, json: String): Boolean {
        val file = File(context.filesDir, "nxb_config.json")
        runCatching { file.writeText(json) }
        val ok = runSu(
            "cp " + file.absolutePath + " /data/local/tmp/nxb_config.json && " +
                "chmod 644 /data/local/tmp/nxb_config.json",
        )
        return ok
    }

    fun hasRoot(context: Context? = null): Boolean = runSu("id -u")

    /** 执行一条 root 命令并返回输出，用于收集 logcat 等。 */
    fun capture(command: String): String? = runSuCapture(command)

    private fun runSu(command: String): Boolean {
        for (exe in SU_PATHS) {
            val ok = runCatching {
                val process = ProcessBuilder(exe, "-c", command)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                val code = process.waitFor()
                Log.i(TAG, "su[$exe]($code): ${output.take(120)}")
                code == 0
            }.getOrElse {
                Log.w(TAG, "su[$exe] failed: ${it.message}")
                false
            }
            if (ok) return true
        }
        return false
    }

    private fun runSuCapture(command: String): String? {
        for (exe in SU_PATHS) {
            val result = runCatching {
                val process = ProcessBuilder(exe, "-c", command)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                val code = process.waitFor()
                if (code == 0) output else null
            }.getOrNull()
            if (result != null) return result
        }
        return null
    }
}
