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
        ivy {
            name = "ChinaRegionData"
            url = uri("https://registry.npmjs.org")
            patternLayout {
                artifact("@province-city-china/level/-/level-[revision].[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("npm", "china-regions") }
        }
    }
}

rootProject.name = "RunTrip"
include(":app")
