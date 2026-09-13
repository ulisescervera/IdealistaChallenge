package com.ulisescervera.uci.domain.usecase

import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.repository.PropertyRepository
import javax.inject.Inject

/**
 * Pulls `list.json` into the cache.
 *
 * Returns `Unit` rather than the list: the caller must read the data through
 * [ObserveVisiblePropertiesUseCase] so that the flags join is applied. Handing
 * back the network list here would create a second, flag-less source of truth.
 */
class RefreshPropertiesUseCase @Inject constructor(
    private val repository: PropertyRepository,
) {
    suspend operator fun invoke(): UciResult<Unit> = repository.refreshProperties()
}
