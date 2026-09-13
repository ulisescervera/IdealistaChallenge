import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// ---------------------------------------------------------------------------
// :domain -- pure Kotlin/JVM module. It must never depend on Android, on
// Retrofit, on Room or on :data. If a compile error tells you otherwise, the
// abstraction is in the wrong place.
//
// This is the one module AGP's built-in Kotlin does NOT cover, so it keeps
// applying `org.jetbrains.kotlin.jvm` exactly as before.
// ---------------------------------------------------------------------------
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions {
        // Bytecode target, not a toolchain. Gradle runs on JDK 25 and compiles
        // down to 17 -- see the note in the Android modules for why this project
        // deliberately does not request a Java 17 toolchain.
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // `api`, not `implementation`: `Flow` appears in the return type of every
    // repository interface, so it is part of this module's ABI. Hiding it would
    // force :data and :app to re-declare coroutines just to see their own
    // dependency's signatures.
    api(libs.kotlinx.coroutines.core)
    // Only the annotation, not a DI framework: :domain stays container-agnostic.
    api(libs.javax.inject)

    testImplementation(libs.bundles.unit.test)
}

// ---------------------------------------------------------------------------
// Obfuscation
//
// This module is a plain jar, so it has no R8 pass of its own -- and it must not
// become an `android-library` just to get one, because being a non-Android
// module is what stops Android from creeping into the domain.
//
// Every class in here *is* obfuscated in the shipped APK: :app runs R8 in full
// mode over the whole program, this jar included, and pulls in the keep rules
// from `domain/proguard-rules.pro` (see app/build.gradle.kts). Verify it with
// `./gradlew assembleRelease` and a mapping-file diff.
// ---------------------------------------------------------------------------
