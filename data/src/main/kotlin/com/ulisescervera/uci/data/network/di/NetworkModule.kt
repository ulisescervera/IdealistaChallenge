package com.ulisescervera.uci.data.network.di

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.ulisescervera.uci.data.BuildConfig
import com.ulisescervera.uci.data.network.UciPropertyApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * The parsing contract with the backend, and the single most important
     * object in `:data`:
     *
     * - `ignoreUnknownKeys` -- the challenge payload already carries fields we
     *   do not model (`exterior` variants, `state`, `energyCertification.title`
     *   nesting). A new field on the server must never crash a shipped client.
     * - `coerceInputValues` -- turns an explicit `"rooms": null` into the
     *   declared default instead of throwing.
     * - `explicitNulls = false` -- we never *send* anything, so omitting nulls
     *   only makes debug logs readable.
     * - `isLenient` is left **off**: a malformed body should surface as
     *   `UciError.Serialization`, not be silently half-parsed.
     */
    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = false
    }

    @Provides
    @Singleton
    fun loggingInterceptor(): HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
        // Bodies in release builds would leak into logcat and bloat the APK's
        // string pool for no benefit.
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    @Provides
    @Singleton
    fun okHttpClient(
        cache: Cache,
        logging: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .cache(cache)
        .addInterceptor(logging)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.UCI_BASE_URL)
        .client(client)
        // GitHub Pages serves these files as application/json; the converter is
        // registered for that exact media type.
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun propertyApi(retrofit: Retrofit): UciPropertyApi = retrofit.create(UciPropertyApi::class.java)

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 20L
}
