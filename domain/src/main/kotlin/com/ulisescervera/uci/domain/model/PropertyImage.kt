package com.ulisescervera.uci.domain.model

/**
 * One carousel entry.
 *
 * [localizedName] is what the backend already translated ("Salón", "Cocina").
 * We prefer it over [tag] for `contentDescription`, which is why accessibility
 * does not need a tag-to-string table in `:app`.
 */
data class PropertyImage(
    val url: String,
    val tag: String?,
    val localizedName: String?,
    val multimediaId: Long?,
) {
    /** Stable identity for DiffUtil and for shared-element transition names. */
    val stableId: String get() = multimediaId?.toString() ?: url
}
