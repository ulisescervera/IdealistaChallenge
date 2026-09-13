package com.ulisescervera.uci.domain.usecase

import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.model.PropertyFlag
import com.ulisescervera.uci.domain.repository.PropertyRepository
import javax.inject.Inject

/**
 * Marks a property as discarded so it drops out of the list.
 *
 * Idempotent on purpose: discarding something already discarded is a no-op
 * rather than a toggle, because the caller here is a one-way "remove" button
 * and re-adding on a double tap would be surprising. Restoring goes through
 * [RestorePropertyUseCase].
 */
class DiscardPropertyUseCase @Inject constructor(
    private val repository: PropertyRepository,
) {
    suspend operator fun invoke(propertyId: String): UciResult<Unit> =
        when (val result = repository.setFlag(propertyId, PropertyFlag.DISCARDED)) {
            is UciResult.Success -> UciResult.Success(Unit)
            is UciResult.Failure -> result
        }
}
