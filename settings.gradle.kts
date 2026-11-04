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
        maven {
            name = "projectDistribution"
            url = uri(rootDir.resolve("build/maven-repository"))
            content { includeGroup("io.github.phillipsk.roadsignal") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "RoadSignalLab"

include(
    ":telematics-core",
    ":telematics-sdk",
    ":cmt-bridge",
    ":sample-app",
    ":distribution-consumer",
)
