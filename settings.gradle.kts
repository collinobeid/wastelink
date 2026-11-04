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
    }
}

rootProject.name = "WasteLink"
include(":app")
include(":core:common")
include(":core:domain")
include(":core:design-system")
include(":core:data")
include(":core:firebase")
include(":core:navigation")
include(":feature:auth")
 