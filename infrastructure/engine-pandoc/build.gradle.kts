plugins {
    id("omni.engine")
    id("omni.android-test")
}

dependencies {
    // 経路探索で HTML を経由することを確かめる
    testImplementation(project(":application"))
}
