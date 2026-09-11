---
name: uci-crear-test
description: Escribe un test en UCI (Ulises-Cervera-Idealista) en la capa correcta y con la herramienta correcta -- JUnit puro, MockWebServer, Room in-memory, Robolectric, Espresso o compose-ui-test. Úsala al añadir cobertura o al reproducir un bug.
---

# Crear un test

## Elegir la herramienta

La regla es: **la herramienta más ligera que pueda fallar por el motivo correcto.** Un test que
pasa porque el objeto está mockeado no vale nada.

| Qué pruebas | Dónde | Con qué | Por qué no algo más simple |
|---|---|---|---|
| Invariante de un modelo | `:domain/test` | JUnit + Truth | Kotlin puro, milisegundos |
| Caso de uso | `:domain/test` | JUnit + fake escrito a mano | Un mock no puede expresar "alterna dos veces y vuelve al inicio" |
| Mapper | `:data/test` | JUnit + fixtures del payload real | Función pura |
| Parseo de red | `:data/test` | **MockWebServer** | Verifica los DTOs contra lo que el servidor sirve de verdad y fija la config de `Json` |
| DAO / SQL | `:data/test` | **Robolectric** + Room in-memory | Lo que se prueba *es* el SQL; un DAO mockeado no puede tener el bug del `INNER JOIN` |
| Repositorio | `:data/test` | Robolectric + Room + API *scripted* | Prueba la cadena DTO → mapper → Room → mapper → modelo completa |
| BD en dispositivo | `:data/androidTest` | Espresso runner | El SQLite de Robolectric no es el mismo binario: colación y UTF-8 solo fallan en dispositivo |
| Formatter | `:app/test` | **Robolectric** + `@Config(qualifiers = "es")` | El objeto del test son los *strings* y los *plurals*; un `Context` mockeado los aprueba todos |
| ViewModel | `:app/test` | JUnit + Turbine + fakes + casos de uso **reales** | Mockear los casos de uso dejaría pasar un ViewModel que llama al equivocado |
| Fragment | `:app/androidTest` | Espresso + Hilt testing | Todo es producción excepto la fuente de datos |
| Composable | `:app/androidTest` | `compose-ui-test-junit4` | Las pantallas son stateless: cada rama es una línea |

## Nombres

El nombre es la **regla**, no el método:

```kotlin
// ❌
@Test fun testDiscard() { … }

// ✅
@Test fun `a discarded property disappears from the list`() { … }
@Test fun `an unconfirmed lift says so instead of claiming there is none`() { … }
```

Un comportamiento por test. Si el nombre necesita un "y", son dos tests.

En `androidTest` no se pueden usar nombres con espacios (limitación de la instrumentación): usa
camelCase descriptivo — `theUndoBannerOffersARestoreForASingleDismissal`.

## Estructura

Arrange / act / assert separados por líneas en blanco, sin comentarios `// given`:

```kotlin
@Test
fun `favouriting a discarded property un discards it`() = runTest {
    val repository = FakePropertyRepository(mapOf("1" to PropertyFlag.DISCARDED))

    ToggleFavouriteUseCase(repository)("1")

    assertThat(repository.currentFlags()["1"]).isEqualTo(PropertyFlag.FAVOURITE)
}
```

Un comentario en un test explica **por qué la regla importa**, no qué hace el código:

```kotlin
@Test
fun `geo point rejects null island`() {
    // 0,0 es lo que manda un backend cuando quiere decir "no lo sé"; poner un
    // pin en el Golfo de Guinea es peor que ocultar el botón del mapa.
    assertThat(GeoPoint.orNull(0.0, 0.0)).isNull()
}
```

## Fakes, no mocks

Los fakes de este proyecto (`FakePropertyRepository`, `FakeRelatedPropertiesRepository`)
**mantienen las invariantes reales**: un flag por inmueble, `NONE` representado como ausencia de
entrada, las escrituras visibles a través de los `Flow`. Eso permite testear comportamiento a lo
largo del tiempo, que es donde están los bugs de verdad.

MockK se reserva para interfaces sin estado. Si tu fake no existe, créalo junto a los demás en
`support/` o `fake/`.

Un fake puede **portarse mal a propósito**: `FakeRelatedPropertiesRepository` devuelve el propio
inmueble en la lista, porque es la única manera de probar que el caso de uso lo filtra en lugar de
confiar en una fuente educada.

## Utilidades del proyecto

- **`MainDispatcherRule`** (`:app/test`) — obligatoria en cualquier test de ViewModel:
  `viewModelScope` usa `Dispatchers.Main.immediate`, que no existe fuera de Android. Por defecto usa
  `UnconfinedTestDispatcher`, así que las coroutines del `init` ya han corrido cuando empieza el
  cuerpo del test.
- **`TestDispatcherProvider`** (`:data/test`) — todos los dispatchers son el mismo, así que
  `withContext(io)` no salta de hilo y `runTest` mantiene el control del tiempo virtual.
- **`FixedUciClock`** (`:data/test`) — un reloj que no avanza, para afirmar un timestamp exacto.
  `advanceTo` / `travelTo` para el único test que necesita dos instantes o dos zonas.
- **`AppFixtures` / `DtoFixtures` / `PropertyFixtures`** — builders con valores por defecto que
  reflejan el inmueble 1 del payload real. Un test que le importa la planta lo dice en un argumento
  y hereda el resto.
- **`launchFragmentInHiltContainer`** (`:app/androidTest`) — `launchFragmentInContainer` usa una
  activity que no es `@AndroidEntryPoint` y cualquier fragment con inyección revienta al *attach*.

## Reglas que evitan tests inestables

- **Nada de `Thread.sleep`.** Si lo necesitas, hay un diseño que arreglar: inyecta el reloj o la
  latencia.
- **Nada de `Instant.now()`.** Usa `FixedUciClock`.
- **Fija el locale** en los tests de `:app` con `@Config(qualifiers = "es")`. Sin eso, una máquina
  de CI en otro locale cambia los separadores de miles y el test falla sin motivo.
- **URLs de imagen inalcanzables** en los tests de UI (`https://uci.invalid/…`): Coil cae al
  placeholder, nada espera una descarga y las aserciones son sobre texto y comportamiento.
- **Latencia simulada a 0** en los tests (`FakeRelatedPropertiesService.NO_LATENCY_MILLIS`).
- **Robolectric solo cuando el objeto del test necesita Android de verdad**: recursos, SQLite,
  Looper. Nunca "por si acaso": multiplica el tiempo del build.
- **Robolectric sobre JDK 25** necesita los `--add-opens` del bloque `testOptions` de `:app` y
  `:data`. Si creas un módulo nuevo con tests de Robolectric, copia ese bloque o los tests fallan
  con `InaccessibleObjectException`. Y `sdk=36` en `robolectric.properties` es el techo de la
  4.16: no lo subas a 37 para igualar `compileSdk`.

## Test de Hilt en `androidTest`

```kotlin
@HiltAndroidTest
@UninstallModules(UciBindingsModule::class)
@RunWith(AndroidJUnit4::class)
class <Feature>FragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue @JvmField
    val propertyRepository: PropertyRepository = AndroidFakePropertyRepository()

    @Before fun setUp() = hiltRule.inject()
}
```

`@UninstallModules` quita los bindings de la raíz de composición (`:app/core/di/UciBindingsModule.kt`) y `@BindValue` pone fakes en su lugar: el
fragment, su ViewModel y los casos de uso reales corren sin cambios. Es la forma de mayor valor
para un test de UI — todo es producción excepto la fuente de datos.

## Qué cubrir siempre

Para cualquier cosa que escribas:

- el camino feliz;
- el caso `null` / ausente, distinguido del caso `false` / `0`;
- el fallo, y que no cambie nada;
- **la regla que motivó el código**. Si el mapper elige `priceInfo` sobre `price`, el test es
  precisamente el alquiler cuyos dos precios difieren.
