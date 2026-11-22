package io.github.sol01st.nobrowser.data

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * root 命令执行通道。
 *
 * 只服务于两件仍然离不开 root 的事：
 *  1. 模块日志页读 logcat 环形缓冲（应用自身没有 READ_LOGS）；
 *  2. 老版本兼容：框架 service 不可用时，把配置写到 /data/local/tmp 给宿主读。
 *
 * 拦截判定、作用域读写、配置下发都已经改走 [XposedConnection]，
 * 因此 root 不授权也不会影响模块的核心功能。
 */
object RootShell {

    private const val TAG = "ExLinkNoBrowser"

    /** su 调用超时：未授权时 su 会一直等授权框，不自己断开会把调用方协程挂住。 */
    private const val SU_TIMEOUT_MS = 8000L

    private val SU_PATHS = listOf("su", "/system/bin/su", "/system/xbin/su", "/sbin/su")

    @Volatile
    private var rootCache: Boolean? = null

    fun hasRoot(refresh: Boolean = false): Boolean {
        if (!refresh) rootCache?.let { return it }
        val ok = capture("id -u")?.trim() == "0"
        rootCache = ok
        return ok
    }

    /** 执行一条 root 命令并返回输出；失败、超时或未授权都返回 null。 */
    fun capture(command: String): String? {
        for (exe in SU_PATHS) {
            val result = runCatching { execWithTimeout(exe, command, SU_TIMEOUT_MS) }.getOrElse { e ->
                Log.w(TAG, "su[$exe] 启动失败: ${e.javaClass.simpleName}: ${e.message}")
                null
            } ?: continue
            if (result.first == 0) return result.second
        }
        return null
    }

    /**
     * 老通道：把配置写到 /data/local/tmp/nxb_config.json 供宿主进程读取。
     * 只有拿到 root 才会成功；框架 service 可用时不需要走这里。
     */
    fun publishConfig(context: Context, json: String): Boolean {
        val local = File(context.filesDir, "nxb_config.json")
        if (!runCatching { local.writeText(json) }.isSuccess) return false
        return capture(
            "cp ${local.absolutePath} /data/local/tmp/nxb_config.json && " +
                "chmod 644 /data/local/tmp/nxb_config.json",
        ) != null
    }

    private fun execWithTimeout(exe: String, command: String, timeoutMs: Long): Pair<Int, String>? {
        val process = ProcessBuilder(exe, "-c", command)
            .redirectErrorStream(true)
            .start()
        var output = ""
        val reader = Thread {
            output = runCatching {
                process.inputStream.bufferedReader().use { it.readText() }
            }.getOrDefault("")
        }.apply {
            isDaemon = true
            start()
        }
        val finished = runCatching { process.waitFor(timeoutMs, TimeUnit.MILLISECONDS) }
            .getOrDefault(false)
        if (!finished) {
            runCatching { process.destroyForcibly() }
            reader.interrupt()
            Log.w(TAG, "su[$exe] 超时（${timeoutMs}ms）")
            return null
        }
        reader.join(300)
        return process.exitValue() to output
    }
}
