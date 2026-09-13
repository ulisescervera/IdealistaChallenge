// ---------------------------------------------------------------------------
// :data -- implementation details. Retrofit + kotlinx.serialization for the
// remote source, Room for the local source, mappers to and from :domain.
// Nothing in here is visible to :app except the repository implementations the
// composition root binds.
// ---------------------------------------------------------------------------
plugins {
    alias(libs.plugins.android.library)
    // No `kotlin-android`: AGP 9 has built-in Kotlin support and rejects it.
    // The Kotlin *version* is selected in the root buildscript block.
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.ulisescervera.uci.data"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField(
            type = "String",
            name = "UCI_BASE_URL",
            value = "\"https://idealista.github.io/android-challenge/\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            // Library-level shrinking + obfuscation, kept on the legacy DSL.
            //
            // AGP 9.3 introduces `optimization { enable = true }`, but its
            // documented scope is app modules and library module *tests*, and
            // there is no documented replacement for `consumerProguardFiles`.
            // Adopting it here would trade a working configuration for an
            // undocumented one; the legacy DSL remains supported.
            isMinifyEnabled = true
            proguardFiles("proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        // No Java toolchain is requested on purpose. A toolchain would make the
        // build demand a JDK 17 installation; compiling with the Gradle JVM
        // (25) down to bytecode 17 needs nothing extra and is what lets this
        // project build with only the JDK that Android Studio ships.
        // Built-in Kotlin derives its `jvmTarget` from `targetCompatibility`.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true

            // Robolectric reflects into the JDK internals, which have been
            // closed by default since Java 17 and are enforced harder on 25.
            // Without these the Room/DAO tests fail with InaccessibleObjectException.
            // Keep in sync with the identical block in :app.
            all { test ->
                test.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/java.security=ALL-UNNAMED",
                    "--add-opens=java.base/java.text=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                    // JDK 24+ warns on restricted native access and will block
                    // it in a future release; MockK's agent needs the second.
                    "--enable-native-access=ALL-UNNAMED",
                    "-XX:+EnableDynamicAgentLoading",
                )
            }
        }
    }
}

// No `sourceSets` block. `src/<sourceSet>/kotlin` is the convention and AGP's
// built-in Kotlin registers it on its own -- the `android.sourceSets{}` section
// of the migration guide is about *additional* directories (its example is
// "additionalSourceDirectory/kotlin"). The old
// `getByName("main").java.srcDirs("src/main/kotlin")` was already redundant
// under the kotlin-android plugin, which did the same thing.

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":domain"))

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization.converter)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.bundles.unit.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.room.testing)
}
