pluginManagement {
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
        // The Maps SDK of Mapbox (the globe of the trip, récit 94): only its own groups come from here.
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            content { includeGroupByRegex("com\\.mapbox.*") }
        }
    }
}

rootProject.name = "Familia"
include(":app")
