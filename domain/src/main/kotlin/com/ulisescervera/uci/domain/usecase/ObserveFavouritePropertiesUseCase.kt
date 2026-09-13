package com.ulisescervera.uci.domain.usecase

import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.domain.repository.PropertyRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Feeds the Compose favourites tab. Ordered by "most recently favourited". */
class ObserveFavouritePropertiesUseCase @Inject constructor(
    private val repository: PropertyRepository,
) {
    operator fun invoke(): Flow<List<Property>> = repository.observeFavouriteProperties()
}
