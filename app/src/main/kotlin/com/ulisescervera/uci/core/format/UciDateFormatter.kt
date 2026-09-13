package com.ulisescervera.uci.core.format

import android.content.Context
import com.ulisescervera.uci.domain.model.FavouriteMark
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dates for the screen.
 *
 * `java.time` is used on minSdk 24 thanks to core library desugaring (see
 * `isCoreLibraryDesugaringEnabled` in the build scripts). The alternative --
 * `SimpleDateFormat` + `Calendar` -- is mutable, not thread-safe, and has no
 * concept of a stored zone, which is exactly what the favourite mark needs.
 *
 * [favouriteMark] renders the moment **in the zone it was created in**, which is
 * the whole point of persisting `time_zone_id`: a property favourited at 19:12
 * in Madrid still reads 19:12 after the user lands in Mexico City. Rendering it
 * in the current zone would silently rewrite the user's history.
 */
@Singleton
class UciDateFormatter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT)

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)

    /** Long date + short time, in the original zone, in the device locale. */
    fun favouriteMark(mark: FavouriteMark): String =
        mark.atOriginalZone().format(dateTimeFormatter.withLocale(locale()))

    /** For `moreCharacteristics.modificationDate`, which carries no zone. */
    fun epochMillisAsDate(epochMillis: Long): String =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
            .format(dateFormatter.withLocale(locale()))

    private fun locale() = context.resources.configuration.locales[0]
}
