plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

extensions.configure<com.android.build.api.dsl.CommonExtension>("android") {
    buildFeatures.compose = true
}
