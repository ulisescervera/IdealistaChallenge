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
    // No `plugins { }` block here on purpose. Pinning a plugin version in
    // settings would put a second source of truth next to
    // gradle/libs.versions.toml, and the two would drift: the catalog says
    // Kotlin 2.3.21 and the root buildscript overrides AGP's KGP to match.
    // Every plugin version comes from the catalog.
}

plugins {
    // Java toolchain auto-provisioning.
    //
    // The build deliberately does NOT request a toolchain -- it compiles with
    // whatever JVM runs Gradle and targets bytecode 17, so a plain Android
    // Studio install with only its bundled JDK is enough. This resolver is
    // insurance for the other direction: if a dependency's own build logic ever
    // asks for a specific JDK, Gradle downloads it instead of failing with
    // "No matching toolchains found".
    //
    // This is the one hardcoded version in the build, and it has to be: the
    // version catalog is not available yet when a settings plugin is resolved.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // osmdroid is published on Maven Central; JitPack kept for its optional bonus pack.
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github.*") }
        }
    }
}

rootProject.name = "Ulises-Cervera-Idealista"

include(":app")
include(":domain")
include(":data")
