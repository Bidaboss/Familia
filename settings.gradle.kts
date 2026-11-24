pluginManagement {
    repositories {
        google()
        // The Google mirror of Maven Central first: Maven Central limits the shared GitHub
        // machines (« 429 Too Many Requests »), the mirror does not. Maven Central stays behind it.
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // The Google mirror of Maven Central first: Maven Central limits the shared GitHub
        // machines (« 429 Too Many Requests »), the mirror does not. Maven Central stays behind it.
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        mavenCentral()
        // The Maps SDK of Mapbox (the globe of the trip, récit 94): only its own groups come from here.
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            content { includeGroupByRegex("com\\.mapbox.*") }
        }
    }
}

rootProject.name = "Familia"
include(":app")
