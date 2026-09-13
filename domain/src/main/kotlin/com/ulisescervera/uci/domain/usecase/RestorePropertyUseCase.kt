package com.ulisescervera.uci.domain.usecase

import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.model.PropertyFlag
import com.ulisescervera.uci.domain.repository.PropertyRepository
import javax.inject.Inject

/**
 * Brings one discarded property back into the list -- the "undo" of the banner
 * and of each row of the discarded sheet.
 */
class RestorePropertyUseCase @Inject constructor(
    private val repository: PropertyRepository,
) {
    suspend operator fun invoke(propertyId: String): UciResult<Unit> =
        when (val result = repository.setFlag(propertyId, PropertyFlag.NONE)) {
            is UciResult.Success -> UciResult.Success(Unit)
            is UciResult.Failure -> result
        }
}
