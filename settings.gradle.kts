pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "LabelPrinter"
include(":app")

// For using a local copy of the Android Image Cropper project
// this will be removed the official packages are updated
include(":cropper")
project(":cropper").projectDir = File("C:/Users/han/Documents/Android-Image-Cropper-main/cropper")

