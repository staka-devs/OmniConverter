plugins {
    id("omni.jvm-library")
}

dependencies {
    api(project(":domain"))
    testImplementation(libs.kotlinx.coroutines.test)
}
