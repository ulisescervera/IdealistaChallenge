package com.ulisescervera.uci.data.local.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A carousel image as stored in Room.
 *
 * Images live as a JSON column rather than in a child table. The trade-off,
 * stated plainly: we lose the ability to query by image, and we gain (a) the
 * order of the carousel for free, without an index column, (b) a single-row
 * read for a list item instead of a 1:N join, and (c) an atomic replace on
 * refresh. UCI never queries by image, so the join buys nothing.
 */
@Serializable
data class ImageRecord(
    @SerialName("u") val url: String,
    @SerialName("t") val tag: String? = null,
    @SerialName("n") val localizedName: String? = null,
    @SerialName("i") val multimediaId: Long? = null,
)
