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
        versionCode = 30
        versionName = "4.2.0"
        resourceConfigurations += listOf("zh", "en")
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/nixiang.jks")
            storePassword = "nixiang2026"
            keyAlias = "nixiang"
            keyPassword = "nixiang2026"
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
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
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

    compileOnly("io.github.libxposed:api:102.0.0")
}
