package com.ulisescervera.uci.domain.usecase

import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.domain.repository.RelatedPropertiesRepository
import javax.inject.Inject

/**
 * Loaded when the detail screen reaches its end.
 *
 * The "never return the property itself" rule is enforced twice: once by the
 * fake service in `:data` and once here, so a future real endpoint cannot
 * regress the behaviour the UI depends on.
 */
class GetRelatedPropertiesUseCase @Inject constructor(
    private val repository: RelatedPropertiesRepository,
) {
    suspend operator fun invoke(
        propertyId: String,
        limit: Int = RelatedPropertiesRepository.DEFAULT_LIMIT,
    ): UciResult<List<Property>> = when (val result = repository.relatedTo(propertyId, limit)) {
        is UciResult.Success -> UciResult.Success(
            result.value.filterNot { it.id == propertyId }.take(limit),
        )
        is UciResult.Failure -> result
    }
}
