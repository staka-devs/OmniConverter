// 合成ルート。全モジュールを束ね、ここでだけ Infrastructure を配線する
plugins {
    id("com.android.application")
    id("omni.android-compose")
    id("omni.android-test")
}

android {
    namespace = "dev.staka.omniconverter"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    defaultConfig {
        applicationId = "dev.staka.omniconverter"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "dev.staka.omniconverter.E2ETestRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        // 同梱した実行ファイルを nativeLibraryDir に展開して実行するため。ADR-0004
        jniLibs.useLegacyPackaging = true
    }
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":infrastructure:core"))
    listOf("ffmpeg", "imagemagick", "libheif", "libarchive", "mupdf", "pandoc").forEach {
        implementation(project(":infrastructure:engine-$it"))
    }
    implementation(project(":presentation"))
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.espresso.intents)
    debugImplementation(libs.compose.ui.test.manifest)
}
