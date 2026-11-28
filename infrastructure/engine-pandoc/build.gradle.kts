plugins {
    id("omni.engine")
    id("omni.android-test")
}

dependencies {
    testImplementation(project(":application"))
}
