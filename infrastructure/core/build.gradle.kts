plugins {
    id("omni.android-library")
}

android {
    namespace = "dev.staka.omniconverter.infrastructure.core"
}

dependencies {
    api(project(":domain"))
    implementation(project(":application"))
}
