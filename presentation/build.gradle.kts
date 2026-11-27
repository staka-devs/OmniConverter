// Presentation は Application と Domain のみに依存する。Infrastructure への依存は layer-check が拒否する
plugins {
    id("omni.android-library")
    id("omni.android-compose")
}

android {
    namespace = "dev.staka.omniconverter.presentation"
}

dependencies {
    implementation(project(":application"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
