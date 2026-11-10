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
        maven {
            url = uri("https://jitpack.io")
            content { includeGroupByRegex("com\\.github\\..*") }
        }
    }
}

rootProject.name = "Gamut"

include(":app")
include(":baselineprofile")
include(":core:core-common")
include(":core:core-database")
include(":core:core-network")
include(":core:core-model")
include(":core:core-designsystem")
include(":core:core-player")
include(":core:core-sync")
include(":feature:feature-search")
include(":feature:feature-library")
include(":feature:feature-player-ui")
include(":feature:feature-resolver")
include(":tools:extractor-smoke")
