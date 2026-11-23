import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

android {
    namespace = "io.github.sol01st.nobrowser"
    compileSdk = 37
    compileSdkMinor = 2
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "io.github.sol01st.nobrowser"
        minSdk = 33
        targetSdk = 34
        versionCode = 36
        versionName = "5.0.4"
    }

    // AGP 9 起 defaultConfig.resourceConfigurations 被移除，改用 androidResources.localeFilters
    androidResources {
        localeFilters += listOf("zh", "en")
    }

    // 签名信息一律从 gradle 属性读取，仓库里不保存任何密码：
    //   RELEASE_STORE_FILE / RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD
    // 写在 ~/.gradle/gradle.properties 或用 -P 传入。读不到就回退 debug 签名，clone 下来即可编译。
    signingConfigs {
        create("release") {
            val storePath = (project.findProperty("RELEASE_STORE_FILE") as String?)
                ?: "../keystore/release.jks"
            val ks = file(storePath)
            if (ks.exists()) {
                storeFile = ks
                storePassword = (project.findProperty("RELEASE_STORE_PASSWORD") as String?) ?: ""
                keyAlias = (project.findProperty("RELEASE_KEY_ALIAS") as String?) ?: ""
                keyPassword = (project.findProperty("RELEASE_KEY_PASSWORD") as String?) ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (signingConfigs.getByName("release").storeFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            signingConfig = if (signingConfigs.getByName("release").storeFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "META-INF/*.kotlin_module"
            excludes += "META-INF/version-control-info.textproto"
            excludes += "DebugProbesKt.bin"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.core:core-ktx:1.16.0")
    // A 步：升到 0.9.5-beta01，顶栏改用官方 GlassTopAppBar + progressiveBlur（miuix-glass / miuix-blur）
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.5-beta01")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.5-beta01")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.5-beta01")
    implementation("top.yukonga.miuix.kmp:miuix-glass:0.9.5-beta01")
    implementation("top.yukonga.miuix.kmp:miuix-blur:0.9.5-beta01")
    implementation("top.yukonga.miuix.kmp:miuix-material:0.9.5-beta01")
    // 真毛玻璃：提示条采样自己下层的内容再模糊（Android 12+ 走 RenderEffect，低版本自动回退成半透明底）
    implementation("dev.chrisbanes.haze:haze:1.6.10")

    compileOnly("io.github.libxposed:api:102.0.0")

    // LSPosed 的 service 通道：作用域读写 + 远程配置下发，全程不需要 root。
    // 自带 XposedProvider（authority 为 ${applicationId}.XposedService），由框架把 binder 送进来。
    implementation("io.github.libxposed:service:102.0.0")
}
