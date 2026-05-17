pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "Styx-Downloader"

val localCommon = file("../Styx-Common")
if (localCommon.isDirectory) {
    includeBuild(localCommon) {
        dependencySubstitution {
            substitute(module("moe.styx:styx-common"))
                .using(project(":styx-common"))
        }
    }
}
