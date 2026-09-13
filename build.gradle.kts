// ---------------------------------------------------------------------------
// UCI :: root build script.
//
// No project dependencies are declared here; every module owns its own
// dependency block and reads versions from gradle/libs.versions.toml.
// ---------------------------------------------------------------------------

// This block is the whole reason the project can pick its own Kotlin version.
//
// AGP 9 has built-in Kotlin support, which replaced the `org.jetbrains.kotlin.android`
// plugin -- applying that plugin now fails outright. The version is no longer
// chosen by applying a plugin, but by overriding the runtime dependency AGP
// itself has on the Kotlin Gradle Plugin. AGP only ever raises a lower version,
// so putting a higher one on the buildscript classpath wins.
//
// Verify it actually took effect:
//     ./gradlew buildEnvironment | grep kotlin-gradle-plugin
// The line should read `-> 2.3.21`.
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // :domain is a plain JVM module; built-in Kotlin only covers Android ones.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.navigation.safeargs) apply false
    alias(libs.plugins.room) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
