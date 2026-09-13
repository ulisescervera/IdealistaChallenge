package com.ulisescervera.uci.domain.usecase

import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.model.PropertyFlag
import com.ulisescervera.uci.domain.repository.PropertyRepository
import javax.inject.Inject

/**
 * Toggles the favourite flag.
 *
 * The *current* flag is read from the repository rather than passed in by the
 * caller: two screens can show the same property at once, and trusting a stale
 * UI state would let a double tap end up favouriting something the user just
 * un-favourited elsewhere.
 *
 * Because [PropertyFlag] is one column, favouriting a discarded property also
 * restores it -- which is the behaviour a user expects from an exclusive pair.
 */
class ToggleFavouriteUseCase @Inject constructor(
    private val repository: PropertyRepository,
) {
    suspend operator fun invoke(propertyId: String): UciResult<PropertyFlag> {
        val target = repository.flagOf(propertyId).toggledFavourite()
        return when (val result = repository.setFlag(propertyId, target)) {
            is UciResult.Success -> UciResult.Success(target)
            is UciResult.Failure -> result
        }
    }
}
