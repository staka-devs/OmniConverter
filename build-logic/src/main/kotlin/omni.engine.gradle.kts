// 変換エンジンモジュールの共通設定。モジュール名 engine-<name> から native/out/<name> を参照する
plugins {
    id("omni.android-library")
}

val engineName = project.name.removePrefix("engine-")

android {
    namespace = "dev.staka.omniconverter.engine.${engineName}"
    sourceSets.getByName("main").jniLibs.srcDir(rootProject.file("native/out/$engineName"))
}

dependencies {
    "implementation"(project(":domain"))
    "implementation"(project(":infrastructure:core"))
}
