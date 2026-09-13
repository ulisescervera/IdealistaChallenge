package com.ulisescervera.uci.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ulisescervera.uci.domain.model.PropertyFlag

/**
 * The user's own state for a property: favourite **or** discarded, never both.
 *
 * The exclusivity the brief asks for is enforced by the *schema*, not by
 * application code: one row per property, one enum column. Two boolean columns
 * would allow `is_favourite = 1 AND is_discarded = 1` to exist, and sooner or
 * later a race would create it.
 *
 * [timeZoneId] is stored next to [updatedAtEpochMillis] so the detail screen can
 * render "guardado el 3 de marzo a las 19:12" in the zone the user was actually
 * in when they tapped, even if the device has travelled since.
 *
 * There is intentionally **no foreign key** to [PropertyEntity]: a flag must
 * outlive a cache wipe. A property that vanishes from the feed and comes back
 * later keeps its favourite mark.
 */
@Entity(tableName = PropertyFlagEntity.TABLE)
data class PropertyFlagEntity(
    @PrimaryKey
    @ColumnInfo(name = "property_code") val propertyCode: String,
    @ColumnInfo(name = "flag") val flag: PropertyFlag,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "time_zone_id") val timeZoneId: String,
) {
    companion object {
        const val TABLE = "property_flags"
    }
}
