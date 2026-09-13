---
name: uci-crear-servicio
description: Crea un servicio de red (Retrofit) o un servicio simulado en el módulo :data de UCI (Ulises-Cervera-Idealista), con su binding de Hilt, su repositorio y sus tests. Úsala cuando haya que hablar con un endpoint nuevo o cuando haya que fingir uno que todavía no existe.
---

# Crear un servicio

Dos variantes, misma forma: un **servicio Retrofit** para un endpoint real y un **servicio
simulado** para uno que no existe todavía. La segunda es interesante porque el proyecto ya la usa
(`FakeRelatedPropertiesService`) y la clave es que sea una **interfaz**, para que sustituirla por la
real sea cambiar una línea.

## Ubicación

```
data/src/main/kotlin/com/ulisescervera/uci/data/network/<Nombre>Api.kt         (real)
data/src/main/kotlin/com/ulisescervera/uci/data/network/di/NetworkModule.kt    (provider)
data/src/main/kotlin/com/ulisescervera/uci/data/related/<Nombre>Service.kt     (simulado: interfaz)
data/src/main/kotlin/com/ulisescervera/uci/data/related/Fake<Nombre>Service.kt (simulado: impl)
app/src/main/kotlin/com/ulisescervera/uci/core/di/UciBindingsModule.kt         (binding)
```

## Variante A — servicio Retrofit

```kotlin
interface <Nombre>Api {

    @GET("recurso.json")
    suspend fun items(): List<<Nombre>Dto>

    @GET("recurso/{id}.json")
    suspend fun item(@Path("id") id: String): <Nombre>Dto
}
```

Reglas:

- `suspend`, no `Call` ni `Single`. Retrofit lo soporta nativamente y el resultado se compone con
  el resto de coroutines.
- Devuelve **DTOs**, nunca modelos.
- Sin `try/catch`: el error sube y `ErrorMapper.runCatchingUci` lo traduce en el repositorio.
- **No mientas en la firma.** Si el endpoint no acepta un id, no le pongas un parámetro `id` para
  que quede bonito. `UciPropertyApi.propertyDetail()` no lo tiene, y el KDoc explica por qué; volver
  a asociar la respuesta con el id pedido es trabajo del mapper.

Provider en `NetworkModule`:

```kotlin
@Provides
@Singleton
fun <nombre>Api(retrofit: Retrofit): <Nombre>Api = retrofit.create(<Nombre>Api::class.java)
```

Si hace falta otro `baseUrl`, añade un `@Qualifier` propio para el `Retrofit`; no muevas el
existente.

## Variante B — servicio simulado

**La interfaz se diseña como si el endpoint existiera.** Eso es lo que hace que la sustitución sea
trivial y que el resto del código no sepa que hoy es falso.

```kotlin
/**
 * <Endpoint que el backend no ofrece todavía.>
 *
 * Forma prevista:
 * ```
 * GET /related?propertyCode=1&limit=10  ->  { "propertyCodes": ["2","3"] }
 * ```
 *
 * Devuelve **ids** y no objetos completos porque es el contrato realista: un
 * recomendador ordena, no reserializa el catálogo. El repositorio hidrata los
 * ids desde la caché, lo que además hace que lleguen con su estado de
 * favorito/descartado ya aplicado.
 */
interface <Nombre>Service {
    suspend fun <operacion>(id: String, limit: Int): List<String>
}
```

Implementación con la latencia **inyectada**, no constante:

```kotlin
@Singleton
class Fake<Nombre>Service(
    private val propertyDao: PropertyDao,
    private val simulatedLatencyMillis: Long,
) : <Nombre>Service {

    // Hilt ignora los argumentos por defecto de Kotlin, así que un único
    // constructor con default no compilaría como binding. Dos constructores es
    // lo que mantiene libre la costura para el test.
    @Inject
    constructor(propertyDao: PropertyDao) : this(propertyDao, DEFAULT_LATENCY_MILLIS)

    override suspend fun <operacion>(id: String, limit: Int): List<String> {
        delay(simulatedLatencyMillis)
        return /* … */
    }

    companion object {
        const val DEFAULT_LATENCY_MILLIS = 650L
        const val NO_LATENCY_MILLIS = 0L
    }
}
```

La latencia de producción existe para que el skeleton se vea de verdad. La de los tests es 0, y es
la razón por la que el fichero de test corre en milisegundos.

## Repositorio

El servicio nunca se inyecta en un ViewModel. Va detrás de un repositorio cuya interfaz vive en
`:domain`:

```kotlin
@Singleton
class <Nombre>RepositoryImpl @Inject constructor(
    private val service: <Nombre>Service,
    private val dao: PropertyDao,
    private val mapper: PropertyEntityMapper,
    private val errorMapper: ErrorMapper,
    private val dispatchers: DispatcherProvider,
) : <Nombre>Repository {

    override suspend fun <operacion>(id: String, limit: Int): UciResult<List<Property>> =
        withContext(dispatchers.io) {
            errorMapper.runCatchingUci {
                val ranked = service.<operacion>(id, limit)
                // El servicio devuelve ids en orden de relevancia; SQLite los
                // devolvería en orden de clave primaria.
                val byId = ranked.mapNotNull { dao.findOne(it) }.associateBy { it.property.propertyCode }
                ranked.mapNotNull { byId[it]?.let(mapper::toDomain) }
            }
        }
}
```

Reglas invariables:

- `withContext(dispatchers.io)` en el borde del repositorio. Room ya despacha sus consultas fuera
  del hilo principal, pero el **mapeo** correría en el dispatcher del colector, que es el main.
- `errorMapper.runCatchingUci` es el **único** `try/catch` de `:data`. Relanza
  `CancellationException`; un `catch (e: Exception)` que se la coma es cómo un ViewModel filtra un
  job pasado `onCleared`.

## Binding de Hilt (en `:app`, la raíz de composición)

```kotlin
@Binds
@Singleton
abstract fun <nombre>Service(impl: Fake<Nombre>Service): <Nombre>Service

@Binds
@Singleton
abstract fun <nombre>Repository(impl: <Nombre>RepositoryImpl): <Nombre>Repository
```

Cuando el endpoint real llegue, se cambia `Fake<Nombre>Service` por `<Nombre>ApiService` en esta
línea y no se toca nada más. Esa es la prueba de que el diseño era correcto.

## Tests obligatorios

- **Servicio real:** `MockWebServer`, con el JSON real. Ver `/uci-crear-dto`.
- **Servicio simulado:** Robolectric + Room in-memory, latencia 0, comprobando el contrato que
  promete (que nunca devuelva el propio id, que respete el límite, el orden del ranking).
- **Repositorio:** Robolectric + Room in-memory + servicio *scripted*. Comprueba que el orden de
  relevancia se conserva, que los ids desconocidos se descartan en lugar de renderizarse en blanco,
  y que los flags locales llegan aplicados.
