---
name: uci-crear-caso-de-uso
description: Crea un caso de uso en el módulo :domain de UCI (Ulises-Cervera-Idealista) con su test y el fake de repositorio correspondiente. Úsala cuando la app necesite una capacidad nueva -- observar algo, refrescar algo, escribir algo -- y no cuando solo haga falta un gesto nuevo de una pantalla (eso es un intent en :app).
---

# Crear un caso de uso

## Regla de decisión

Un caso de uso es una **capacidad** del negocio; un intent es un **gesto** de una pantalla. Si tres
botones distintos acabarían llamando a lo mismo, es un caso de uso. Si el mismo caso de uso se
invoca desde el listado, del detalle y de la pestaña de favoritos, estás en lo correcto.

Si lo que quieres es "que este botón haga X", no necesitas un caso de uso nuevo: necesitas un
intent que llame a uno existente.

## Ubicación

```
domain/src/main/kotlin/com/ulisescervera/uci/domain/usecase/<Verbo><Sustantivo>UseCase.kt
domain/src/test/kotlin/com/ulisescervera/uci/domain/usecase/<...>Test.kt
```

Nombres: `Observe*` (devuelve `Flow`), `Refresh*` (escribe desde red), `Get*` (lectura puntual
`suspend`), y verbos de negocio para las escrituras (`ToggleFavourite`, `DiscardProperty`).

## Plantillas

### Observación (reactivo)

```kotlin
class Observe<Algo>UseCase @Inject constructor(
    private val repository: PropertyRepository,
) {
    operator fun invoke(): Flow<List<Property>> = repository.observe<Algo>()
}
```

Un *passthrough* de una línea **está bien**. Su valor no es la lógica, es que el ViewModel dependa
de una capacidad con nombre en lugar de de un repositorio con diez métodos.

Si necesitas filtrar, hazlo en SQL, no aquí: un `filter` en el caso de uso reasigna la lista
completa en cada emisión.

### Escritura

```kotlin
class <Verbo><Sustantivo>UseCase @Inject constructor(
    private val repository: PropertyRepository,
) {
    suspend operator fun invoke(propertyId: String): UciResult<Unit> =
        when (val result = repository.setFlag(propertyId, PropertyFlag.X)) {
            is UciResult.Success -> UciResult.Success(Unit)
            is UciResult.Failure -> result
        }
}
```

### Escritura que depende del estado actual

**Lee el estado del repositorio, no lo recibas por parámetro.** Dos pantallas pueden mostrar el
mismo inmueble a la vez, y confiar en un estado de UI obsoleto hace que un doble toque acabe
marcando como favorito algo que el usuario acababa de desmarcar en otro sitio.

```kotlin
suspend operator fun invoke(propertyId: String): UciResult<PropertyFlag> {
    val target = repository.flagOf(propertyId).toggledFavourite()
    return when (val result = repository.setFlag(propertyId, target)) {
        is UciResult.Success -> UciResult.Success(target)
        is UciResult.Failure -> result
    }
}
```

### Caso de uso que refuerza una garantía

Si el contrato promete algo, hazlo cumplir aquí además de en `:data`. Un endpoint real futuro está
fuera de nuestro control:

```kotlin
is UciResult.Success -> UciResult.Success(
    result.value.filterNot { it.id == propertyId }.take(limit),
)
```

## Reglas

- Una responsabilidad. Si el nombre necesita un "y", son dos casos de uso.
- `operator fun invoke`, para que el call site se lea `refreshProperties()`.
- `@Inject constructor` con `javax.inject`. **No** Hilt: `:domain` es agnóstico del contenedor.
- Devuelve `UciResult<T>` en escrituras y lecturas puntuales; `Flow<T>` en observaciones (un `Flow`
  no falla, se queda sin emitir).
- Idempotencia explícita: si es un *toggle*, dilo en el KDoc; si es de un solo sentido (el botón
  "borrar" del listado), dilo también y explica por qué.

## Test obligatorio

Con un **fake escrito a mano**, no un mock. El caso de uso `ToggleFavourite` se prueba llamándolo
dos veces y comprobando que se vuelve al estado inicial; un mock que devuelve un valor fijo para
`flagOf` no puede expresar eso.

```kotlin
@Test
fun `toggling favourite twice returns to none`() = runTest {
    val repository = FakePropertyRepository()
    val toggle = ToggleFavouriteUseCase(repository)

    toggle("1")
    toggle("1")

    assertThat(repository.currentFlags()).doesNotContainKey("1")
}
```

Cubre siempre: camino feliz, el estado previo relevante, y el fallo del repositorio (que no debe
cambiar nada).

Si tu fake no existe, créalo en `domain/src/test/.../fake/` implementando la interfaz de verdad y
manteniendo sus invariantes.

## Después

- Inyéctalo en el ViewModel que lo necesita (Hilt lo resuelve sin registrarlo en ningún módulo:
  tiene `@Inject constructor`).
- Añade el intent correspondiente en `:app` si viene de un gesto.
- Si el caso de uso necesita un método nuevo de repositorio, añádelo a la interfaz en `:domain`,
  impleméntalo en `:data` y **testea la implementación con Room in-memory**, no con un mock.
