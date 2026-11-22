# libxposed 入口类：不能被裁掉，类名要做资源适配
-dontwarn io.github.libxposed.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# 注入到宿主进程的代码保持稳定
-keep class io.github.sol01st.nobrowser.hook.** { *; }
-keep class io.github.sol01st.nobrowser.core.** { *; }

# LSPosed service 通道：provider 会被系统按类名实例化，AIDL 与 remote preferences
# 跨进程使用，任何混淆/裁剪都会让作用域与配置下发失效。
-keep class io.github.libxposed.service.** { *; }
-keep interface io.github.libxposed.service.** { *; }
-dontwarn io.github.libxposed.service.**

# Miuix / Compose 保守保留
-keep class top.yukonga.miuix.** { *; }
-dontwarn top.yukonga.miuix.**

-dontwarn kotlinx.coroutines.**
-dontwarn org.jetbrains.annotations.**

# 保留 Compose 注解方法
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
