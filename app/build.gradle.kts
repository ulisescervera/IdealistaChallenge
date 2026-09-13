// ---------------------------------------------------------------------------
// :app -- presentation layer. XML views + ViewBinding for the property list
// and detail, Jetpack Compose for the image viewer and the favourites list,
// single Activity + Navigation component, and the composition root.
// ---------------------------------------------------------------------------
plugins {
    alias(libs.plugins.android.application)
    // No `kotlin-android`: AGP 9 has built-in Kotlin support and rejects it.
    // `kotlin.plugin.compose` is still required -- built-in Kotlin replaces the
    // `kotlin-android` plugin and nothing else.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.navigation.safeargs)
}

android {
    namespace = "com.ulisescervera.uci"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.ulisescervera.uci"
        minSdk = libs.versions.minSdk.get().toInt()
        // Set explicitly, and one below compileSdk. AGP 9 flipped
        // `android.sdk.defaultTargetSdkToCompileSdkIfUnset` to true, so leaving
        // this out would silently promote the app to API 37 -- and Robolectric
        // 4.16 tops out at 36, so every unit test would break.
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "com.ulisescervera.uci.HiltTestRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        // A committed debug key keeps `assembleRelease` runnable out of the box
        // for reviewers. Replace it with your own before shipping anything.
        create("uciRelease") {
            storeFile = file("${rootProject.projectDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            // Obfuscation + shrinking, on the legacy DSL.
            //
            // AGP 9.3 adds `optimization { enable = true }`, which also moves
            // keep rules into `src/<variant>/keepRules/*.keep`. It is the
            // direction of travel, but :data still needs `consumerProguardFiles`
            // (no documented equivalent yet), and having the two modules
            // configure shrinking in two different styles would be worse than
            // being one release behind. Revisit when the library story lands.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                // Only the `-optimize` variant is still allowed: AGP 9 flipped
                // `android.r8.proguardAndroidTxt.disallowed` to true.
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                // :domain is a plain jar, so its keep rules ride along here.
                // The file must exist -- `android.proguard.failOnMissingFiles`
                // is now true and a missing path fails the build.
                "${rootProject.projectDir}/domain/proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("uciRelease")
        }
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
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
            // Keep in sync with the identical block in :data.
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
                    "--enable-native-access=ALL-UNNAMED",
                    "-XX:+EnableDynamicAgentLoading",
                )
            }
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE*",
                "/META-INF/DEPENDENCIES",
            )
        }
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        // Accessibility is a hard requirement for UCI, not a suggestion.
        fatal += setOf("ContentDescription", "ClickableViewAccessibility", "LabelFor")
        disable += setOf("GradleDependency", "NewerVersionAvailable")
    }
}

// No `sourceSets` block: `src/main/kotlin`, `src/test/kotlin`,
// `src/androidTest/kotlin` and `src/debug/kotlin` are all conventional paths
// that AGP's built-in Kotlin picks up by itself. See data/build.gradle.kts.

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // --- Views ---------------------------------------------------------------
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.startup)

    // --- Lifecycle -----------------------------------------------------------
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // --- Navigation ----------------------------------------------------------
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)

    // --- Compose (image viewer + favourites) ---------------------------------
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose.ui)
    // `BackHandler` in the image viewer lives here, not in compose-foundation.
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // --- DI ------------------------------------------------------------------
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // --- Images / maps -------------------------------------------------------
    implementation(libs.coil)
    implementation(libs.coil.compose)
    // Mandatory in Coil 3: coil-core no longer bundles a network fetcher, so
    // without this every remote URL silently resolves to the placeholder.
    implementation(libs.coil.network.okhttp)
    implementation(libs.osmdroid.android)

    implementation(libs.kotlinx.coroutines.android)

    // --- Unit tests ----------------------------------------------------------
    testImplementation(libs.bundles.unit.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)

    // --- Instrumented tests --------------------------------------------------
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.navigation.testing)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
