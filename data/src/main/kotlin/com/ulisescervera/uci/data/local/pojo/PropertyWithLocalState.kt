package com.ulisescervera.uci.data.local.pojo

import androidx.room.Embedded
import androidx.room.Relation
import com.ulisescervera.uci.data.local.entity.PropertyDetailEntity
import com.ulisescervera.uci.data.local.entity.PropertyEntity
import com.ulisescervera.uci.data.local.entity.PropertyFlagEntity

/**
 * A cached property together with everything local we know about it.
 *
 * Assembling this in Room instead of in Kotlin means the flag join happens once
 * in SQLite, not once per emission per screen. It is also why the repository can
 * expose a single `Flow` per screen with no `combine` of three sources -- and
 * therefore no window where the list shows a property as favourite while the
 * favourites tab has not caught up.
 *
 * [detail] is present only for properties whose detail has been opened at least
 * once. It is the only source for `hasLift`.
 */
data class PropertyWithLocalState(
    @Embedded val property: PropertyEntity,

    @Relation(parentColumn = "property_code", entityColumn = "property_code")
    val flag: PropertyFlagEntity?,

    @Relation(parentColumn = "property_code", entityColumn = "property_code")
    val detail: PropertyDetailEntity?,
)
