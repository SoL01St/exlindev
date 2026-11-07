package io.github.sol01st.nobrowser.hook

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * libxposed API 102 入口，见 META-INF/xposed/java_init.list。
 */
class HookEntry : XposedModule() {

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        log(Log.INFO, TAG, "module loaded: " + param.processName)
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        runCatching {
            HookEngine.onPackageLoaded(this, param.packageName, param.defaultClassLoader)
        }.onFailure { log(Log.WARN, TAG, "onPackageLoaded failed", it) }
    }

    private companion object {
        const val TAG = "ExLinkNoBrowser"
    }
}
