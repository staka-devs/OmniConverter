// 変換エンジンモジュールの共通設定。モジュール名 engine-<name> から native/out/<name> を参照する
plugins {
    id("omni.android-library")
}

val engineName = project.name.removePrefix("engine-")

android {
    namespace = "dev.staka.omniconverter.engine.${engineName}"
    sourceSets.getByName("main").jniLibs.srcDir(rootProject.file("native/out/$engineName"))
    // 計装テストの APK でも、同梱した実行ファイルを nativeLibraryDir に展開させる。ADR-0004
    packaging.jniLibs.useLegacyPackaging = true
}

dependencies {
    "implementation"(project(":domain"))
    "implementation"(project(":infrastructure:core"))
}
