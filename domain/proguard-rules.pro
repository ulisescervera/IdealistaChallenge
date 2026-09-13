# ---------------------------------------------------------------------------
# :domain keep rules -- consumed by :app (see app/build.gradle.kts).
# The domain layer is fully obfuscated; nothing here is reflected upon, so we
# only protect the pieces the Kotlin runtime itself needs.
# ---------------------------------------------------------------------------

# Sealed hierarchies are matched structurally, never by name -> free to rename.
# Enum values ARE resolved by name when persisted, so keep valueOf/values.
-keepclassmembers enum com.ulisescervera.uci.domain.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Kotlin metadata is only needed for reflection-heavy libraries; we have none
# in :domain, so let R8 drop it.
-dontwarn kotlin.Metadata
