// 具体的な実装に依存させないため、依存は Kotlin 標準ライブラリとコルーチンのみ
plugins {
    id("omni.jvm-library")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
}
