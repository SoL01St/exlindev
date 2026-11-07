package io.github.sol01st.nobrowser.data

import android.content.Context
import android.os.Build
import io.github.sol01st.nobrowser.BuildConfig
import io.github.sol01st.nobrowser.core.TargetApp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 诊断报告：把环境状态 + 规则统计 + 最近的注入/命中日志拼成纯文本，方便反馈问题时直接贴。
 * 日志来自 logcat 的 ExLinkNoBrowser tag（宿主进程与模块共用同一个 tag）。
 */
object Diagnostics {

    private const val TAG = "ExLinkNoBrowser"

    fun build(context: Context, apps: List<TargetApp>, moduleEnabled: Boolean?): String {
        val sb = StringBuilder()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        sb.appendLine("===== NiXiangBrowser 诊断报告 =====")
        sb.appendLine("生成时间: $now")
        sb.appendLine()

        sb.appendLine("--- 应用 ---")
        sb.appendLine("版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        sb.appendLine("包名: ${BuildConfig.APPLICATION_ID}")
        sb.appendLine("构建类型: ${BuildConfig.BUILD_TYPE}")
        sb.appendLine()

        sb.appendLine("--- 设备 ---")
        sb.appendLine("机型: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("指纹: ${Build.FINGERPRINT}")
        sb.appendLine()

        sb.appendLine("--- 运行环境 ---")
        sb.appendLine("Root: ${if (ScopeManager.hasRoot(context)) "已授权" else "未授权"}")
        sb.appendLine(
            "模块开关(modules_state.enabled): " + when (moduleEnabled) {
                true -> "已启用"
                false -> "未启用"
                null -> "读取失败"
            },
        )
        val scope = ScopeManager.readScope(context)
        sb.appendLine(
            "LSPosed 作用域: " + if (scope == null) {
                "读取失败"
            } else if (scope.isEmpty()) {
                "空"
            } else {
                "${scope.size} 个 → ${scope.sorted().joinToString(", ")}"
            },
        )
        sb.appendLine()

        sb.appendLine("--- 规则 ---")
        val enabledApps = apps.filter { it.enabled }
        sb.appendLine("应用总数: ${apps.size}，已接管: ${enabledApps.size}")
        sb.appendLine("规则总数: ${apps.sumOf { it.rules.size }}，白名单总数: ${apps.sumOf { it.whiteUrls.size }}")
        apps.sortedByDescending { it.enabled }.forEach { app ->
            val flag = if (app.enabled) "启用" else "关闭"
            sb.appendLine(
                "  [$flag] ${app.appName} (${app.packageName}) " +
                    "rules=${app.rules.size} white=${app.whiteUrls.size} ask=${app.askEachTime}",
            )
        }
        sb.appendLine()

        sb.appendLine("--- 最近日志（ExLinkNoBrowser，最多 300 行）---")
        sb.appendLine(readLogs(context))
        sb.appendLine()
        sb.appendLine("===== 报告结束 =====")
        return sb.toString()
    }

    /** 默认文件名，例如 nxb-diag-20251107-141530.txt */
    fun defaultFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "nxb-diag-$stamp.txt"
    }

    private fun readLogs(context: Context): String {
        val raw = ScopeManager.capture("logcat -d -s $TAG:V -t 300 2>/dev/null")
            ?: return "（读取 logcat 失败，需要 root）"
        val lines = raw.lineSequence()
            .filter { it.isNotBlank() }
            .filterNot { it.contains("su[") }
            .toList()
        if (lines.isEmpty()) return "（暂无记录）"
        return lines.joinToString("\n")
    }
}
