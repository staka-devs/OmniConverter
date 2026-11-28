import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.TestOptions

/**
 * 計装テストを回す Gradle Managed Devices。ciGroup<Variant>AndroidTest で全端末を回す。
 * x86_64 でも ARM 変換を持つ google イメージを使い、arm64 専用の APK と実行ファイルを動かす。ADR-0007
 */
fun TestOptions.managedEmulators() {
    managedDevices {
        localDevices {
            create("api31") {
                device = "Medium Phone"
                apiLevel = 31
                systemImageSource = "google"
                testedAbi = "arm64-v8a"
            }
            create("api37") {
                device = "Medium Phone"
                apiLevel = 37
                systemImageSource = "google"
                testedAbi = "arm64-v8a"
            }
        }
        groups {
            create("ci") {
                targetDevices.add(localDevices["api31"])
                targetDevices.add(localDevices["api37"])
            }
        }
    }
}

pluginManager.withPlugin("com.android.application") {
    extensions.configure<ApplicationExtension> { testOptions.managedEmulators() }
}
pluginManager.withPlugin("com.android.library") {
    extensions.configure<LibraryExtension> {
        defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testOptions.managedEmulators()
    }
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
dependencies {
    "androidTestImplementation"(libs.findLibrary("androidx-test-runner").get())
    "androidTestImplementation"(libs.findLibrary("androidx-test-ext-junit").get())
    "androidTestImplementation"(kotlin("test-junit"))
}
