package com.ulisescervera.uci.domain.model

/**
 * Favourite and discarded are **mutually exclusive by construction**: a single
 * enum column in `property_flags` makes the illegal "favourite and discarded"
 * state unrepresentable, which two booleans would not.
 *
 * The DTOs never carry this -- it is purely local user state, joined in when
 * building the model.
 */
enum class PropertyFlag {
    NONE,
    FAVOURITE,
    DISCARDED;

    val isFavourite: Boolean get() = this == FAVOURITE
    val isDiscarded: Boolean get() = this == DISCARDED

    /** Tapping the favourite button toggles between [FAVOURITE] and [NONE]. */
    fun toggledFavourite(): PropertyFlag = if (isFavourite) NONE else FAVOURITE

    /** Tapping discard toggles between [DISCARDED] and [NONE]. */
    fun toggledDiscarded(): PropertyFlag = if (isDiscarded) NONE else DISCARDED
}
