// Presentation は Application と Domain のみに依存する。Infrastructure への依存は layer-check が拒否する
plugins {
    id("omni.android-library")
    id("omni.android-compose")
    id("omni.android-test")
}

android {
    namespace = "dev.staka.omniconverter.presentation"
}

dependencies {
    implementation(project(":application"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
