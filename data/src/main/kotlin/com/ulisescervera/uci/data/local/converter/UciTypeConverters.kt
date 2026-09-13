package com.ulisescervera.uci.data.local.converter

import androidx.room.TypeConverter
import com.ulisescervera.uci.data.local.entity.ImageRecord
import com.ulisescervera.uci.domain.model.PropertyFlag
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room type converters.
 *
 * ### Why [PropertyFlag] is not stored with `.name`
 * The app is obfuscated with R8 in full mode, which is allowed to rename enum
 * constants. Persisting `enum.name` would produce a database whose contents
 * depend on the build. The exhaustive `when` below pins the stored token to a
 * literal, so adding a value to [PropertyFlag] is a compile error here -- which
 * is exactly when you want to be forced to think about migrations.
 *
 * The same literals are hardcoded in the DAO queries; [Flags] is the shared
 * source of truth for both.
 */
class UciTypeConverters {

    @TypeConverter
    fun imagesToJson(images: List<ImageRecord>): String = json.encodeToString(images)

    @TypeConverter
    fun jsonToImages(raw: String?): List<ImageRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        // A corrupt cache must degrade to "no images", never crash the list.
        return runCatching { json.decodeFromString<List<ImageRecord>>(raw) }.getOrDefault(emptyList())
    }

    @TypeConverter
    fun flagToToken(flag: PropertyFlag): String = when (flag) {
        PropertyFlag.NONE -> Flags.NONE
        PropertyFlag.FAVOURITE -> Flags.FAVOURITE
        PropertyFlag.DISCARDED -> Flags.DISCARDED
    }

    @TypeConverter
    fun tokenToFlag(token: String?): PropertyFlag = when (token) {
        Flags.FAVOURITE -> PropertyFlag.FAVOURITE
        Flags.DISCARDED -> PropertyFlag.DISCARDED
        else -> PropertyFlag.NONE
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }

    /** Storage tokens, referenced verbatim from the DAO `@Query` strings. */
    object Flags {
        const val NONE = "none"
        const val FAVOURITE = "favourite"
        const val DISCARDED = "discarded"
    }
}
