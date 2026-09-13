package com.ulisescervera.uci.core.di

import com.ulisescervera.uci.data.related.FakeRelatedPropertiesService
import com.ulisescervera.uci.data.related.RelatedPropertiesService
import com.ulisescervera.uci.data.repository.PropertyRepositoryImpl
import com.ulisescervera.uci.data.repository.RelatedPropertiesRepositoryImpl
import com.ulisescervera.uci.domain.repository.PropertyRepository
import com.ulisescervera.uci.domain.repository.RelatedPropertiesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The composition root: the seam between `:domain` and `:data`.
 *
 * This module lives in `:app` and not in `:data` on purpose. Choosing *which*
 * implementation satisfies a domain interface is an application-level decision,
 * not an implementation detail of the data layer -- it is the one place where a
 * reader can see the whole wiring of the app at a glance, and the one place a
 * variant (a demo build, an instrumented test) needs to touch.
 *
 * `:data` keeps only the modules that build its own infrastructure -- Retrofit,
 * OkHttp, Room, the dispatchers and the clock -- because those have no
 * meaningful alternative for `:app` to choose between.
 *
 * The practical payoff: swapping [FakeRelatedPropertiesService] for a real
 * Retrofit service when the endpoint exists is a one-line change *in this file*,
 * and `@UninstallModules(UciBindingsModule::class)` in an instrumented test
 * replaces every data source at once while leaving the ViewModels, the use cases
 * and the views untouched.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UciBindingsModule {

    @Binds
    @Singleton
    abstract fun propertyRepository(impl: PropertyRepositoryImpl): PropertyRepository

    @Binds
    @Singleton
    abstract fun relatedPropertiesRepository(impl: RelatedPropertiesRepositoryImpl): RelatedPropertiesRepository

    /**
     * The simulated recommendation endpoint. Replace the implementation here --
     * nothing else in the app names it.
     */
    @Binds
    @Singleton
    abstract fun relatedPropertiesService(impl: FakeRelatedPropertiesService): RelatedPropertiesService
}
