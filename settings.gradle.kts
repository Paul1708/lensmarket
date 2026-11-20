pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "lensmarket"

include("image-service")
include("image-service:application")
include("listing-service")
include("listing-service:application")
include("messaging-service")
include("messaging-service:application")
include("user-service")
include("user-service:application")
include("system-tests")
