# ---------------------------------------------------------------------------
# UCI :: :app R8 configuration.
#
# `android.enableR8.fullMode=true` is on, which means R8 assumes NOTHING is
# reached by reflection. Anything the framework instantiates by name has to be
# declared here. Everything else -- our ViewModels' internals, use cases,
# mappers, domain models -- gets renamed and inlined, which is the point.
#
# Rules for :data (Retrofit / Room / kotlinx.serialization) arrive
# automatically through data/consumer-rules.pro. Rules for :domain are pulled
# in from domain/proguard-rules.pro by app/build.gradle.kts.
# ---------------------------------------------------------------------------

# Keep line numbers so Play Console / bugsnag stack traces stay usable, but
# hide the original file name.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Android entry points --------------------------------------------------
# The manifest and the navigation graph reference these classes by string name.
-keep class com.ulisescervera.uci.UciApplication { *; }
-keep class com.ulisescervera.uci.MainActivity { *; }

# Fragments are instantiated reflectively by FragmentFactory when Navigation
# inflates the graph or when the process is restored.
-keep public class * extends androidx.fragment.app.Fragment {
    public <init>();
}

# Custom views inflated from XML need their (Context, AttributeSet) ctor.
-keepclasseswithmembers class * extends android.view.View {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ---- Hilt / Dagger ---------------------------------------------------------
-keep class dagger.hilt.internal.aggregatedroot.codegen.** { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep class com.ulisescervera.**.*_HiltModules* { *; }
-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}
-dontwarn dagger.hilt.internal.**

# ---- Navigation / SafeArgs -------------------------------------------------
# Args classes are read from the Bundle by key, not reflectively, but the
# generated `fromBundle` is called from synthetic code R8 can trip over.
-keepclassmembers class * implements androidx.navigation.NavArgs {
    public static *** fromBundle(android.os.Bundle);
}
-keepnames class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ---- osmdroid --------------------------------------------------------------
# osmdroid loads tile-source and overlay implementations reflectively and reads
# its configuration from a SharedPreferences-backed bean.
-keep class org.osmdroid.** { *; }
-keep interface org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
-dontwarn android.support.**

# ---- Coil ------------------------------------------------------------------
-dontwarn coil3.**

# ---- Compose ---------------------------------------------------------------
# The Compose compiler already emits R8-friendly code; only the tooling hooks
# (which are debug-only) need silencing.
-dontwarn androidx.compose.ui.tooling.**

# ---- Kotlin ----------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
}
