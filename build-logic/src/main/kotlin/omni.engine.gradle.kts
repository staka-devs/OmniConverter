plugins {
    id("omni.android-library")
}

val engineName = project.name.removePrefix("engine-")
val nativeBuildOutput = rootProject.file("native/out/$engineName")

android {
    namespace = "dev.staka.omniconverter.engine.${engineName}"
    sourceSets.getByName("main").jniLibs.srcDir(nativeBuildOutput)
    packaging.extractBundledExecutables()
}

dependencies {
    "implementation"(project(":domain"))
    "implementation"(project(":infrastructure:core"))
}
