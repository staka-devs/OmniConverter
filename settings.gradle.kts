pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "OmniConverter"

include(":domain")
include(":application")
include(":infrastructure:core")
listOf("ffmpeg", "imagemagick", "libheif", "libarchive", "mupdf", "pandoc").forEach {
    include(":infrastructure:engine-$it")
}
include(":presentation")
include(":app")
