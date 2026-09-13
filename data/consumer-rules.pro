# ---------------------------------------------------------------------------
# :data consumer rules -- automatically applied to every consumer of this
# library (i.e. :app). These are the ONLY things that must survive R8.
#
# NOTE (R8 9.x, shipped with AGP 9): global options such as `-dontoptimize` and
# `-dontobfuscate` are no longer allowed in a consumer rules file --
# `android.r8.globalOptionsInConsumerRules.disallowed` is now true and their
# presence fails publication. Anything like that belongs in the app's own rules.
# ---------------------------------------------------------------------------

# ---- Attributes ------------------------------------------------------------
# These are listed one by one, deliberately.
#
# R8 9.2 changed what wildcards mean: `-keepattributes *Annotation*` (and `*`,
# and `*Invisible*`) NO LONGER retain invisible annotations. That is not a
# cosmetic change here -- `@kotlinx.serialization.Serializable` and Room's
# `@Entity` both have BINARY/CLASS retention, i.e. they are *invisible*
# annotations. Losing them means the serializer lookup and the Room codegen
# metadata disappear in release builds only, which is the worst possible way to
# find out.
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
-keepattributes RuntimeInvisibleTypeAnnotations
-keepattributes AnnotationDefault

# ---- kotlinx.serialization -------------------------------------------------
# The compiler plugin generates a `Companion.serializer()` per @Serializable
# class and looks it up reflectively at runtime for polymorphic/def-value paths.
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    *** Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
# @SerialName values are read from the annotation, so the DTO *properties* may
# be renamed freely -- that is why we serialize by explicit @SerialName only.

# ---- Retrofit --------------------------------------------------------------
# Retrofit builds its service implementations with a dynamic proxy and reads
# the generic return types via reflection.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation interface <1>
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit

# ---- OkHttp ----------------------------------------------------------------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- Room ------------------------------------------------------------------
# Room's generated implementations reference the @Database class by name.
# The constructor is named explicitly because AGP 9 turned on
# `android.r8.strictFullModeForKeepRules`: `-keep class A` alone no longer
# implies `-keep class A { <init>(); }`.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**
