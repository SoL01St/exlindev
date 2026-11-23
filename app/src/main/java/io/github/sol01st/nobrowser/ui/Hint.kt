package io.github.sol01st.nobrowser.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/**
 * 全局轻提示。
 *
 * 队列、显示时长、滑动手势、无障碍播报全部交给 Miuix 官方的 [SnackbarHostState]，
 * 这里只保留一个进程内的单例入口，调用方式和以前一样（[show] 是普通函数，不要求协程上下文）。
 * 视觉直接用官方 Snackbar（不透明 squircle 表面），保证在任何背景上的可读性。
 */
object HintState {

    val host = SnackbarHostState()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun show(message: String) {
        scope.launch {
            host.showSnackbar(
                message = message,
                duration = SnackbarDuration.Custom(ShowMillis),
            )
        }
    }

    /** 与原自绘提示条一致：2.4 秒后自动消失。 */
    private const val ShowMillis = 2400L
}
