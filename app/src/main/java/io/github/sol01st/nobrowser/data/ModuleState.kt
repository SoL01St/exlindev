package io.github.sol01st.nobrowser.data

/**
 * 框架与注入状态的快照，全部来自 LSPosed 的 service 通道，不需要 root。
 */
data class FrameworkInfo(
    /** 是否连接上框架（连不上 = LSPosed 未启用本模块，或框架版本过旧）。 */
    val connected: Boolean,
    /** 框架名，例如 LSPosed / Vector。 */
    val framework: String?,
    val version: String?,
    val apiVersion: Int,
    /** 当前作用域里的包。 */
    val scope: Set<String>,
    /** 正在注入的目标进程数（钩子已生效的进程）。 */
    val runningTargets: Int,
)

object ModuleState {

    fun query(): FrameworkInfo {
        val svc = XposedConnection.service()
            ?: return FrameworkInfo(false, null, null, 0, emptySet(), 0)
        val scope = runCatching { svc.scope.toSet() }.getOrDefault(emptySet())
        val running = runCatching { svc.runningTargets.size }.getOrDefault(0)
        return FrameworkInfo(
            connected = true,
            framework = XposedConnection.name(svc).ifBlank { null },
            version = XposedConnection.version(svc).ifBlank { null },
            apiVersion = XposedConnection.api(svc),
            scope = scope,
            runningTargets = running,
        )
    }

    /**
     * 模块是否已生效。
     * true = 框架已连接且有目标进程在注入；false = 连上了但还没有目标；
     * null = 连不上框架（未启用或框架不支持 service API）。
     */
    fun isEnabled(): Boolean? {
        val info = query()
        if (!info.connected) return null
        return info.runningTargets > 0
    }
}
