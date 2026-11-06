# libxposed 入口类：不能被裁掉，类名要做资源适配
-dontwarn io.github.libxposed.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# 注入到宿主进程的代码保持稳定
-keep class com.exlink.nobrowser.hook.** { *; }
-keep class com.exlink.nobrowser.core.** { *; }

# Miuix / Compose 保守保留
-keep class top.yukonga.miuix.** { *; }
-dontwarn top.yukonga.miuix.**

-dontwarn kotlinx.coroutines.**
-dontwarn org.jetbrains.annotations.**

# 保留 Compose 注解方法
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
