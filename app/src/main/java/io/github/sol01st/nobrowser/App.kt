package io.github.sol01st.nobrowser

import android.app.Application
import io.github.sol01st.nobrowser.data.XposedConnection

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 尽早注册：框架的 binder 通过 XposedProvider 送进来，注册晚了会错过绑定，
        // 之后作用域读写与配置下发都会退回需要 root 的老通道。
        XposedConnection.init()
    }
}
