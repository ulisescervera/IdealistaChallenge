# CLAUDE.md — Ulises-Cervera-Idealista (UCI)

Guía de trabajo para este repositorio. Si algo de aquí contradice al código, gana el código y
este documento está desactualizado: arréglalo en el mismo PR.

---

## 1. Qué es este proyecto

App Android de visionado de inmuebles sobre los endpoints del *android-challenge* de Idealista.
Kotlin, `minSdk 24`, Clean Architecture en tres módulos, vistas XML con ViewBinding y dos pantallas
en Compose.

Toolchain: **AGP 9.3.2 + Gradle 9.5 + JDK 25**. El proyecto no pide toolchain de Java: compila con
el JVM que ejecute Gradle y emite bytecode 17, así que no hace falta instalar ningún JDK extra.

**Siglas UCI.** Prefijan todo lo que este proyecto crea y que podría colisionar: paquetes,
recursos, estilos, ids de navegación e infraestructura (`UciDatabase`, `UciResult`,
`Theme.Uci`, `uci_item_property.xml`). **No** prefijan el vocabulario del negocio: `Property`,
`PropertyDetail`, `Money`, `Floor`. Regla práctica: si el nombre sobreviviría a un cambio de app,
no lleva prefijo.

---

## 2. Reglas que no se negocian

Son las cinco cosas que hacen que este código se mantenga coherente. Romper cualquiera de ellas
debería fallar en revisión.

1. **`:domain` no conoce Android.** Es un módulo `kotlin-jvm`. No hay `Context`, no hay `View`, no
   hay `@Parcelize`, no hay Retrofit, no hay Room. El compilador lo vigila; no hace falta
   disciplina.
2. **La base de datos es la única fuente de verdad.** `refresh*` escribe, `observe*` lee. Nada
   devuelve datos "recién bajados de la red" a la UI. Si te ves haciendo
   `state = copy(properties = result.value)`, para y reconsidera.
3. **Los estados imposibles no se representan.** Un enum en lugar de dos booleanos; un `surface`
   derivado en lugar de `isLoading` + `isError` + `isEmpty`; `Boolean?` cuando "no informado" y
   "no lo tiene" son cosas distintas.
4. **Ningún texto se concatena en código.** Todo sale de `strings.xml` con marcadores posicionales.
   Los plurales son `<plurals>`.
5. **Cada `when` sobre un tipo sellado es exhaustivo y sin `else`.** Añadir un `UciError`, un
   `DetailRow` o un intent debe romper la compilación en todos los sitios que hay que tocar. Ese es
   el mecanismo, no un efecto secundario molesto.

---

## 3. Dónde va cada cosa

| Voy a crear… | Va en… | Skill |
|---|---|---|
| Un modelo de negocio | `:domain/model/` | `/uci-crear-modelo` |
| Un caso de uso | `:domain/usecase/` | `/uci-crear-caso-de-uso` |
| Una interfaz de repositorio | `:domain/repository/` | — |
| Un colaborador interno de `:data` | `:data/repository/` (p. ej. `PropertyFlagStore`) | — |
| Un DTO | `:data/network/dto/` | `/uci-crear-dto` |
| Una entidad de Room + DAO | `:data/local/entity/` y `/dao/` | `/uci-crear-entidad` |
| Un servicio de red o simulado | `:data/network/` o `:data/related/` | `/uci-crear-servicio` |
| Un mapper | `:data/mapper/` | — |
| Un binding de Hilt (`@Binds`) | `:app/core/di/UciBindingsModule.kt` | `/uci-crear-servicio` |
| Un ViewModel + su contrato MVI | `:app/feature/<feature>/` | `/uci-crear-viewmodel` |
| Un fragment | `:app/feature/<feature>/` | `/uci-crear-fragment` |
| Un layout XML o un composable | `:app/res/layout/` o `:app/feature/<feature>/` | `/uci-crear-vista` |
| Un test | junto al código, en `test/` o `androidTest/` | `/uci-crear-test` |

Los intents viven en `:app`, no en `:domain`. El razonamiento está en
`docs/adr/0001-donde-viven-los-intents-de-la-vista.md`; si quieres cambiarlo, escribe otro ADR que
lo supersede en lugar de moverlos sin más.

**Antes de crear un repositorio nuevo, lee `docs/adr/0002-un-repositorio-por-agregado.md`.** El eje
es el **agregado** (la unidad de consistencia), no el origen ni el propietario del dato. Hay dos:
`PropertyRepository` y `RelatedPropertiesRepository`; los flags del usuario están en el primero
aunque no vengan de la red. Si la interfaz se hace incómoda, la respuesta es partirla por otro
agregado, no volver a partirla por procedencia. Y si lo que necesitas es aislar una zona de
`:data` —otro reloj, otra tabla, otro modo de fallo— crea una clase en la que el repositorio
delegue, como `PropertyFlagStore`, y no una interfaz en `:domain`.

---

## 4. Convenciones de código

### Kotlin

- Código, nombres y commits en **inglés**. Textos de UI en **español** (`values/`) e inglés
  (`values-en/`). Comentarios y documentación de proyecto (`README`, `CLAUDE.md`, ADRs) en español.
- `ktlint` oficial vía `.editorconfig`. Línea máxima 120. Comas finales sí.
- Orden de imports: alfabético, sin comodines.
- `internal` por defecto para todo lo que no cruza el módulo. `private` para todo lo que no cruza
  el fichero.
- Un fichero por tipo público, salvo tipos sellados con sus variantes y contratos MVI, que van
  juntos porque se leen juntos.
- `data object` para variantes sin estado de un tipo sellado.
- Sin `!!` fuera de tests. En su lugar `checkNotNull` con un mensaje que nombre la clase.
- Sin `catch (e: Exception)`. En `:data` todo pasa por `ErrorMapper.runCatchingUci`, que además
   relanza `CancellationException`.

### Nombres

| Tipo | Patrón | Ejemplo |
|---|---|---|
| Caso de uso | verbo + sustantivo + `UseCase` | `ObserveVisiblePropertiesUseCase` |
| Repositorio | sustantivo + `Repository` / `RepositoryImpl` | `PropertyRepositoryImpl` |
| DTO | sustantivo + `Dto` | `PropertyListItemDto` |
| Entidad | sustantivo + `Entity` | `PropertyFlagEntity` |
| Mapper | origen + destino + `Mapper` | `PropertyDtoMapper` |
| Contrato MVI | `<Feature>Intent` / `UiState` / `Effect` | `PropertyListIntent` |
| Layout | `uci_<tipo>_<nombre>.xml` | `uci_item_property.xml` |
| Id de vista | `uci` + camelCase | `uciPropertyFavouriteButton` |
| String | `uci_<sección>_<clave>` | `uci_list_empty_title` |
| String de accesibilidad | `uci_a11y_<clave>` | `uci_a11y_favourite_state_on` |
| Destino de navegación | `uci_destination_<pantalla>` | `uci_destination_property_detail` |
| Acción de navegación | `uci_action_<origen>_to_<destino>` | `uci_action_list_to_detail` |

`uci_<tipo>_` en layouts: `fragment_`, `item_`, `view_`, `activity_`.

### Comentarios

El estándar de este repo es **comentar el *por qué*, nunca el *qué***.

```kotlin
// ❌ Incrementa el contador en uno.
counter++

// ✅ El servicio devuelve ids en orden de relevancia; SQLite los devolvería en
// orden de clave primaria, así que hay que reordenar para conservar el ranking.
rankedIds.mapNotNull { byId[it] }
```

Si una decisión tenía una alternativa razonable, el comentario dice cuál era y por qué se
descartó. Si el comentario ocuparía más de 15 líneas o afecta a más de un fichero, es un ADR.

### Versiones

Todas en `gradle/libs.versions.toml`. **Nunca** una versión escrita a mano en un
`build.gradle.kts`. Si añades una dependencia, añade también el `[versions]` correspondiente.

Tres versiones están **atadas a AGP** y no se suben por separado; si tocas una, tocas las cuatro
en el mismo commit:

La columna que manda es la del **mínimo**: por debajo de esa versión el build rompe. La del
catálogo es lo que hay fijado hoy, y puede ir por delante sin problema.

| Librería | Mínimo con AGP 9 | Fijado hoy | Qué pasa por debajo del mínimo |
|---|---|---|---|
| Hilt | 2.59.2 | 2.60.1 | `Could not find the Android Gradle Plugin (AGP) base extension` |
| Navigation (SafeArgs) | 2.9.6 | 2.10.0 | El plugin no entiende el Kotlin integrado de AGP 9 |
| KSP | 2.3.6 | 2.3.11 | KSP1 no es compatible con AGP 9 |

Y al revés: Hilt ≥ 2.59 **no funciona** sobre AGP 8. El acoplamiento va en las dos direcciones.

La versión de Kotlin **no** se elige aplicando un plugin (AGP 9 rechaza `kotlin-android`), sino
sobrescribiendo la dependencia de AGP sobre KGP en el `buildscript` de la raíz. Verifícalo:

```bash
./gradlew buildEnvironment | grep kotlin-gradle-plugin   # debe imprimir "-> 2.3.21"
```

---

## 5. Metodología

### Ciclo de trabajo

1. Entender el requisito y **mirar el payload real** antes de modelar nada. Los dos endpoints
   están en el README; sus rarezas (dos precios distintos en un alquiler, `propertyType` que es la
   familia y no el tipo, campos ausentes por inmueble) han determinado media arquitectura.
2. Modelar en `:domain` primero: modelo → interfaz de repositorio → caso de uso. Con tests.
3. Implementar en `:data`: DTO → entidad → mapper → repositorio. Con tests de mapper y de DAO.
4. Implementar en `:app`: contrato MVI → ViewModel → vista. Con tests de ViewModel.
5. Comprobar accesibilidad **antes** de considerarlo terminado (ver checklist).
6. `./gradlew test lint` en verde.

### Tests

- Nombre en forma de regla, no de método: ``fun `a discarded property disappears from the list`()``.
- Un comportamiento por test. Si el nombre necesita un "y", son dos tests.
- **Fakes escritos a mano** para repositorios; MockK solo para interfaces sin estado. El motivo:
  los tests de ViewModel usan casos de uso **reales** sobre fakes, así que un ViewModel que llame
  al caso de uso equivocado falla. Con mocks pasaría.
- Robolectric solo cuando el objeto del test necesita Android de verdad: recursos (formatters),
  SQLite (DAOs), Looper (ViewModels con `LiveData`). Nunca "por si acaso".
- Robolectric corre sobre JDK 25 gracias a los `--add-opens` del bloque `testOptions` de `:app` y
  `:data`. Si añades un módulo con tests de Robolectric, cópialos. `sdk=36` en
  `robolectric.properties` es el techo de la 4.16: **no** lo subas a 37 para igualar `compileSdk`.
- Los tests de `:app` fijan el locale con `@Config(qualifiers = "es")`. Sin eso, una máquina de CI
  en otro locale cambia los separadores de miles y el test falla sin motivo.
- El reloj y los dispatchers se inyectan. Si necesitas `Thread.sleep` o `Instant.now()` en un test,
  hay un diseño que arreglar antes.

### Checklist de accesibilidad

Antes de dar por terminada cualquier vista:

- [ ] Todo lo pulsable mide ≥ 48 dp (`uci_touch_target_min`).
- [ ] Todo `ImageView`/`Icon` tiene `contentDescription` o es explícitamente decorativo
      (`importantForAccessibility="no"` / `contentDescription = null`).
- [ ] Los bloques de texto de una tarjeta están **fusionados** en un solo nodo
      (`mergeChildrenForAccessibility` / `semantics(mergeDescendants = true)`).
- [ ] Los botones con estado tienen `contentDescription` (la acción) **y** `stateDescription`
      (el estado). No son lo mismo.
- [ ] Los skeletons son invisibles al lector de pantalla y el contenedor anuncia "cargando" una vez.
- [ ] Los cambios transitorios (banners, cambio de página) se anuncian con
      `announceForAccessibility`.
- [ ] Ningún tamaño de texto en `dp`; la fila reflúye a escala de fuente del 200 %.
- [ ] El contraste está verificado en claro **y** en oscuro.
- [ ] `./gradlew lint` pasa (`ContentDescription`, `ClickableViewAccessibility` y `LabelFor` son
      fatales).

### Definición de "terminado"

- Compila en `debug` y en `release` (es decir: R8 no rompe nada).
- Tests unitarios verdes en los tres módulos.
- `lint` sin errores.
- Los `when` nuevos son exhaustivos.
- Los textos nuevos existen en `values/` y en `values-en/`.
- La checklist de accesibilidad está cubierta.
- Las decisiones no obvias están comentadas.

---

## 6. Trampas conocidas de este código

Cosas que ya han costado tiempo. Léelas antes de tocar la zona correspondiente.

- **`ViewPager2` dentro de `RecyclerView`.** Todas las filas comparten el mismo id, y el framework
  lanza *"Page can only be offset by a positive amount"* al restaurar estado. Solución en
  `PropertyCarouselView.init`: `isSaveEnabled = false`.
- **Rebind completo de una fila.** Sin *change payload*, marcar un favorito rebinda toda la fila y
  el carrusel salta a la foto 1. Ver `PropertyDiffCallback.getChangePayload`.
- **`MapView` de osmdroid.** Posee un ejecutor de descarga de teselas. Hay que llamar a
  `UciMapConfigurator.release` en `onViewRecycled` y en `onDestroyView`, o se acumulan mapas vivos.
- **Mapa dentro de un pager.** Un mapa con multitouch se come el arrastre horizontal. En el
  carrusel se usa `configureStatic` + una vista transparente encima que solo captura clics.
- **Transición compartida postergada.** `postponeEnterTransition()` sin un
  `startPostponedEnterTransition()` garantizado deja la pantalla en blanco para siempre. Hay tres
  disparadores: primera imagen cargada, estado de error y un timeout.
- **Binding de fragment.** Un `Fragment` sobrevive a su vista. El binding se anula en
  `onDestroyView` (lo hace `ViewBindingFragment`); si escribes un fragment que no hereda de él,
  hazlo a mano.
- **`repeatOnLifecycle`, no `launchWhenStarted`.** El segundo solo suspende al colector, así que un
  `Flow` que emite en background acumula trabajo y lo entrega de golpe al volver.
- **Compose y `DisposeOnViewTreeLifecycleDestroyed`.** La estrategia por defecto destruye la
  composición al desanclar la vista, que es lo que pasa en cada navegación: se pierde todo el
  `remember`, incluida la posición de scroll.
- **`Dispatchers.Main` en tests.** `viewModelScope` está fijado a `Main.immediate`, que no existe
  fuera de Android. Usa `MainDispatcherRule`.
- **`internal` en el constructor de una clase pública.** Kotlin da **error**, no aviso:
  `EXPOSED_PARAMETER_TYPE`. Como `:app` nombra los `*Impl` de `:data` en `UciBindingsModule`, esos
  `*Impl` son públicos y **todos sus colaboradores inyectados tienen que serlo**. Por eso
  `PropertyFlagStore` no es `internal` aunque conceptualmente lo sea; el razonamiento y las
  alternativas están en `docs/adr/0002-un-repositorio-por-agregado.md`.
- **Enum en la BD.** No persistas `enum.name`: R8 en `fullMode` puede renombrarlo. Usa un `when`
  exhaustivo a literales, como `UciTypeConverters.flagToToken`.
- **`-keepattributes` con comodines (R8 9.2).** `*Annotation*` ya **no** conserva las anotaciones
  *invisibles*, que es la retención de `@Serializable` y de las de Room. Lístalas una a una, como
  hace `data/consumer-rules.pro`. Si te equivocas aquí, la serialización se rompe **solo en
  release**.
- **Fuentes Kotlin en AGP 9.** No declares source sets para las rutas convencionales:
  `src/main/kotlin`, `src/test/kotlin`, `src/androidTest/kotlin` y `src/debug/kotlin` los registra
  solo el Kotlin integrado. La sección `android.sourceSets{}` de la guía de migración es para
  directorios **adicionales** (su ejemplo es literalmente `additionalSourceDirectory/kotlin`).
  Y si algún día necesitas uno, va **dentro** del bloque `android { sourceSets { … } }`: a nivel
  superior, `android.sourceSets.named("main") { … }` resuelve el receptor al tipo *legacy* y
  revienta con `DefaultAndroidLibrarySourceSet_Decorated cannot be cast to AndroidLibrarySourceSet`.
- **`targetSdk` explícito.** AGP 9 lo hereda de `compileSdk` si falta, y eso rompe Robolectric,
  que se queda una API por detrás.
- **Extensiones de Coil 3.** `crossfade`, `placeholder`, `error`, `fallback` y `allowHardware`
  dejaron de ser miembros del builder y son funciones de extensión: hay que importarlas una a una
  desde `coil3.request`.
- **`coil-network-okhttp` es obligatorio.** `coil-core` ya no trae *fetcher* de red; sin ese
  artefacto toda URL remota se queda en el placeholder, sin error.

---

## 7. Comandos

```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew test                             # los tres módulos
./gradlew :domain:test                     # instantáneo, sin Android
./gradlew connectedAndroidTest             # requiere dispositivo
./gradlew lint
./gradlew assembleRelease                  # comprueba que R8 no rompe nada
./gradlew :app:dependencies --configuration debugRuntimeClasspath
```

Deeplinks:

```bash
adb shell am start -a android.intent.action.VIEW -d "uci://property/3"
adb shell am start -a android.intent.action.VIEW -d "uci://property/1/map?focusOnMap=true"
```

---

## 8. Skills disponibles

Nueve skills en `.claude/skills/` generan los artefactos del proyecto siguiendo estas convenciones.
Úsalas: son la forma de que el código nuevo se parezca al existente sin tener que recordar nada de
este documento.

| Skill | Genera |
|---|---|
| `/uci-crear-modelo` | Modelo de `:domain` con sus invariantes y su test |
| `/uci-crear-caso-de-uso` | Caso de uso + test con fake de repositorio |
| `/uci-crear-dto` | DTO de `kotlinx.serialization` + test de parseo con MockWebServer |
| `/uci-crear-entidad` | Entidad de Room + DAO + test con Room in-memory |
| `/uci-crear-servicio` | Servicio Retrofit o simulado + binding de Hilt + test |
| `/uci-crear-viewmodel` | Contrato MVI + ViewModel + test |
| `/uci-crear-fragment` | Fragment XML o Compose + destino de navegación |
| `/uci-crear-vista` | Layout XML o composable, con accesibilidad y skeleton |
| `/uci-crear-test` | Test en la capa correcta con la herramienta correcta |
