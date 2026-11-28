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
        versionCode = providers.gradleProperty("versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("versionName").orNull ?: "0.1.0"
        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "dev.staka.omniconverter.E2ETestRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    val ciReleaseKeystore = System.getenv("OMNI_KEYSTORE")
    if (ciReleaseKeystore != null) {
        signingConfigs.create("release") {
            storeFile = file(ciReleaseKeystore)
            storePassword = System.getenv("OMNI_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("OMNI_KEY_ALIAS")
            keyPassword = System.getenv("OMNI_KEY_PASSWORD")
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }
    val engineLicenseAssets = rootProject.file("native/out/assets")
    sourceSets.getByName("main").assets.srcDir(engineLicenseAssets)
    packaging.extractBundledExecutables()
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
