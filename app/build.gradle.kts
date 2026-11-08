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
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "io.github.sol01st.nobrowser"
        minSdk = 26
        targetSdk = 36
        versionCode = 33
        versionName = "5.0.1"
        resourceConfigurations += listOf("zh", "en")
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
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.0")
    // 真毛玻璃：提示条采样自己下层的内容再模糊（Android 12+ 走 RenderEffect，低版本自动回退成半透明底）
    implementation("dev.chrisbanes.haze:haze:1.6.10")

    compileOnly("io.github.libxposed:api:102.0.0")
}
