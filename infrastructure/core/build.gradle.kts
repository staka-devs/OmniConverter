plugins {
    id("omni.android-library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.staka.omniconverter.infrastructure.core"
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    api(project(":domain"))
    implementation(project(":application"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    api(libs.work.runtime)

    testImplementation(libs.kotlinx.coroutines.test)
    // Android の org.json は単体テストではスタブなので、実装を足す
    testImplementation(libs.orgjson)
}
