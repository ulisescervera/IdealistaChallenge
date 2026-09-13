# Ulises-Cervera-Idealista (UCI)

App Android de visionado de inmuebles. Listado, detalle, visor de imágenes a pantalla completa y
favoritos, sobre los endpoints públicos del *android-challenge* de Idealista.

Las siglas **UCI** prefijan todo lo que el proyecto crea y que podría colisionar con nombres de
librerías: paquetes (`com.ulisescervera.uci`), recursos (`uci_fragment_property_list.xml`,
`@string/uci_list_title`), estilos (`Theme.Uci`, `Widget.Uci.Card`), clases de infraestructura
(`UciDatabase`, `UciResult`) e ids de navegación (`uci_destination_property_list`). Los nombres de
dominio —`Property`, `PropertyDetail`, `Money`— **no** llevan prefijo: son el vocabulario del
negocio, no del proyecto.

---

## Índice

- [Motivación](#motivación)
- [Requisitos](#requisitos)
- [Puesta en marcha](#puesta-en-marcha)
- [Arquitectura](#arquitectura)
- [Decisiones técnicas](#decisiones-técnicas)
- [Accesibilidad](#accesibilidad)
- [Tests](#tests)
- [Ofuscación](#ofuscación)
- [Deeplinks](#deeplinks)
- [Limitaciones conocidas](#limitaciones-conocidas)

---

## Motivación

Este repositorio es una **demostración de código, criterio y conocimientos**: la prueba técnica de
Idealista, resuelta como lo que es —una oportunidad de enseñar cómo trabajo, qué decisiones tomo y
por qué las tomo—.

Gracias a Idealista por la oportunidad y por el enunciado, que no es un detalle menor: da un
entorno concreto sobre el que desarrollar, con dos endpoints de datos **reales** y sus rarezas
—dos precios distintos en un alquiler, un `propertyType` que es la familia y no el tipo, campos
que existen en unos inmuebles y no en otros—. Esas asperezas son las que obligan a decidir, y de
ellas sale buena parte de lo que hay aquí; un payload de juguete no habría dado para tanto.

El objetivo, por tanto, no es solo que la app funcione, sino que el código explique **por qué**
hace lo que hace. Un challenge de este tipo se lee más veces de las que se ejecuta, así que el
proyecto está optimizado para ser leído:

- cada decisión no obvia está comentada en el sitio donde se toma, no en un documento aparte;
- las decisiones estructurales con alternativas defendibles están en `docs/adr/`;
- los tests están escritos como especificación: el nombre dice la regla y el cuerpo la comprueba.

Ejemplos de lo que eso significa en la práctica: el precio se toma de `priceInfo` y no de la raíz
porque en un alquiler los dos números difieren en tres órdenes de magnitud; el flag de
favorito/descartado es **una columna** y no dos booleanos porque así el estado ilegal "favorito y
descartado a la vez" no es representable; el ascensor es `Boolean?` y no `Boolean` porque el
endpoint de listado no lo informa y decir "sin ascensor" sería afirmar algo que no sabemos.

---

## Requisitos

| Requisito | Versión |
|---|---|
| JDK | **17 o superior** (probado con el JDK 25 que trae Android Studio) |
| Android Studio | Quail (2026.1) o superior |
| Gradle | 9.5.0 (vía wrapper) |
| AGP | 9.3.2 |
| Kotlin | 2.3.21 |
| `minSdk` | **24** |
| `targetSdk` | 36 |
| `compileSdk` | 37 |

**No hay que configurar ningún JDK.** El proyecto no pide un *toolchain* de Java: compila con el
JVM que ejecute Gradle —el JBR que Studio trae de serie— y genera bytecode 17
(`compileOptions { sourceCompatibility/targetCompatibility = VERSION_17 }`). Pedir
`jvmToolchain(17)` obligaría a tener instalado un JDK 17 aparte, o a descargarlo, sin ganar nada.

> Si al abrir el proyecto Studio dice *"Gradle version X is incompatible with the Gradle JVM
> version Y"*, el wrapper y el JDK se han desincronizado. Gradle 9.5 admite JVM 17–26; si tu Studio
> es más nuevo y trae un JVM 27+, sube el wrapper antes de tocar la configuración del IDE.

No hace falta ninguna API key. El mapa usa **OpenStreetMap** vía osmdroid precisamente por eso:
un build con Google Maps sin clave compila y luego muestra un rectángulo gris, que es el peor modo
de fallo posible para quien clona el repo.

---

## Puesta en marcha

```bash
git clone <este-repo>
cd ulises-cervera-aprendiendo-android
```

El wrapper JAR no está versionado (está en `.gitignore`, como recomienda Gradle para repos
públicos). Genéralo una vez:

```bash
gradle wrapper --gradle-version 9.5.0
```

…o simplemente abre el proyecto en Android Studio, que lo crea en el primer *sync*.

A partir de ahí:

```bash
./gradlew assembleDebug          # APK de debug
./gradlew installDebug           # instalar en el dispositivo conectado
./gradlew test                   # tests unitarios de los tres módulos
./gradlew connectedAndroidTest   # tests instrumentados (necesita emulador/dispositivo)
./gradlew lint                   # incluye las reglas de accesibilidad como fatales
./gradlew assembleRelease        # build ofuscado y firmado con la clave de debug incluida
```

> `debug.keystore` está versionado a propósito para que `assembleRelease` funcione al clonar.
> **No es una clave de producción**: sustitúyela antes de publicar nada.

La configuración cache de Gradle está desactivada en `gradle.properties` para que el primer sync
sea lo más liso posible. Actívala en local cuando el build esté verde.

---

## Notas del toolchain (AGP 9)

El proyecto está en la línea AGP 9, que trae varios cambios que no son evidentes leyendo el código:

- **Kotlin va integrado en AGP.** El plugin `org.jetbrains.kotlin.android` ya no se aplica: AGP 9
  lo rechaza (*"Cannot add extension with name 'kotlin'"*). La versión de Kotlin se elige
  sobrescribiendo la dependencia que AGP tiene sobre el Kotlin Gradle Plugin, en el bloque
  `buildscript` del `build.gradle.kts` raíz. Compruébalo con
  `./gradlew buildEnvironment | grep kotlin-gradle-plugin`.
  Los plugins `kotlin.plugin.compose` y `kotlin.plugin.serialization` **siguen haciendo falta**:
  el Kotlin integrado sustituye a `kotlin-android` y a nada más.
- **Los fuentes Kotlin ya no cuelgan del source set `java`**, y por eso el proyecto ya no declara
  ningún `sourceSets`: las rutas convencionales (`src/main/kotlin`, `src/test/kotlin`…) las
  registra el Kotlin integrado por su cuenta. El bloque `android.sourceSets{}` de la guía de
  migración es solo para directorios *adicionales*.
- **Tres versiones van atadas a AGP 9 y no se pueden subir por separado:** Hilt ≥ 2.59.2,
  Navigation ≥ 2.9.6 (SafeArgs no entendía el Kotlin integrado antes) y KSP ≥ 2.3.6.
- **AGP 9.3.2 y no 9.3.0/9.3.1**: esas dos hacen *crashear* `./gradlew lint`, que aquí es parte de
  la definición de terminado.
- **`targetSdk` se declara explícito** aunque `compileSdk` sea mayor. AGP 9 hace que `targetSdk`
  herede de `compileSdk` si no se pone, y eso subiría la app a API 37 — donde Robolectric 4.16 ya
  no tiene imagen y todos los tests unitarios se caen.
- **R8 9.2 cambió el significado de los comodines en `-keepattributes`.** `*Annotation*` ya no
  conserva las anotaciones *invisibles*, que es justo la retención de `@Serializable` y de las de
  Room. Por eso `data/consumer-rules.pro` las lista una a una: con el comodín, la serialización se
  rompería **solo en release**.
- **`checkNotNull` pierde su mensaje en release.** R8 9 aplica `-processkotlinnullchecks remove_message`
  por defecto. Los mensajes que nombran la clase (`ViewBindingFragment`, por ejemplo) siguen siendo
  útiles en debug; en un *stack trace* de producción no aparecerán.
- **Los tests unitarios corren con `--add-opens`.** Robolectric usa reflexión sobre las internas
  del JDK, cerradas desde Java 17 y más estrictas en el 25. Los argumentos están en el bloque
  `testOptions` de `:app` y `:data`.

---

## Arquitectura

Clean Architecture en tres módulos Gradle, con la dependencia apuntando siempre hacia dentro:

```
┌──────────────────────────────────────────────────────────┐
│  :app          vistas XML + Compose, ViewModels, Hilt    │
│                intents, formatters, navegación           │
└───────────────┬──────────────────────────┬───────────────┘
                │                          │
                ▼                          ▼
┌───────────────────────────┐   ┌──────────────────────────┐
│  :domain   (Kotlin puro)  │◄──│  :data                   │
│  modelos                  │   │  Retrofit + kotlinx.ser  │
│  interfaces de repositorio│   │  Room                    │
│  casos de uso             │   │  DTOs, entities, mappers │
│  UciResult / UciError     │   │  impl. de repositorios   │
└───────────────────────────┘   └──────────────────────────┘
```

- **`:domain`** es un módulo `kotlin-jvm`, no Android. Eso no es una convención: es una barrera que
  el compilador vigila. Si alguien intenta meter un `Context` o un `View` en el dominio, el build
  falla. Sus únicas dependencias son coroutines (por `Flow`, que aparece en la ABI de los
  repositorios) y `javax.inject` (solo la anotación).
- **`:data`** implementa los repositorios y es el único módulo que conoce Retrofit, Room y los DTOs.
  Expone `:domain` como `api`, así que `:app` ve los modelos pero no las entidades.
- **`:app`** contiene vistas, ViewModels y **la raíz de composición**:
  `app/…/core/di/UciBindingsModule.kt` es el único sitio donde se decide qué implementación
  satisface cada interfaz de `:domain`. Es una decisión de aplicación, no un detalle de la capa de
  datos, y tenerla en un fichero permite ver el cableado completo de un vistazo y sustituirlo
  entero en un test instrumentado. `:data` conserva solo los módulos que construyen su propia
  infraestructura (Retrofit, OkHttp, Room, dispatchers, reloj), porque de esos `:app` no tiene
  ninguna alternativa que elegir.

### ¿Dónde viven los intents?

El enunciado pide estudiarlo. La respuesta es **en `:app`**, y el razonamiento completo está en
[`docs/adr/0001-donde-viven-los-intents-de-la-vista.md`](docs/adr/0001-donde-viven-los-intents-de-la-vista.md).
Resumen: un intent es el nombre de un gesto de una pantalla concreta, transporta tipos de
presentación (posición en el `RecyclerView`, índice de imagen para la transición compartida) y
cambia cuando cambia el diseño, no cuando cambia el negocio. Los casos de uso sí son del dominio.

### ¿Cuántos repositorios?

**Dos:** `PropertyRepository` y `RelatedPropertiesRepository`. Hubo un tercero,
`PropertyFlagRepository`, separado por *propietario del dato* —catálogo del backend frente a
favoritos y descartados del usuario—. Se fusionó porque esa línea partía el concepto "favorito" en
dos: se leía por `observeFavouriteProperties()` y se escribía por `setFlag()`, así que cualquier
cambio en cómo se modela tocaba las dos interfaces. El criterio que sí aguanta es el **agregado**,
y aquí solo hay uno.

La distinción caché/dato-de-usuario no se pierde: vive en el esquema (`property_flags` no tiene
clave ajena a `properties`, así que un refresco no puede borrar un favorito) y en la
implementación, donde `PropertyFlagStore` sigue siendo el único escritor de esa tabla. Cohesión en
el borde, separación por detrás:
[`docs/adr/0002-un-repositorio-por-agregado.md`](docs/adr/0002-un-repositorio-por-agregado.md).

### Flujo de datos

```
refresh*  : red   → mappers → Room               (solo escribe, devuelve un resultado)
observe*  : Room  → mappers → :domain → UI       (solo lee, nunca toca la red)
```

Nada lee "a través de" la red. Consecuencias directas, todas deseables:

- la app funciona sin conexión con lo que ya tenía cacheado;
- un favorito marcado en el detalle aparece en el listado y en la pestaña Compose **sin ningún
  event bus**, porque las tres pantallas leen la misma consulta de Room;
- los ViewModels se testean con un repositorio falso y cero HTTP.

**Quién dispara un `refresh`.** Tres gestos distintos, tres comportamientos distintos, tres
intents distintos en `PropertyListIntent`:

| Gesto | Intent | Spinner | Notas |
|---|---|---|---|
| Entrar en el listado | `ScreenStarted` | no, ya hay skeleton | Una sola vez por ViewModel: `ScreenStarted` llega en cada `onViewCreated` (rotación, volver del detalle), y refrescar en todas sería una llamada de red por navegación. |
| **Deslizar hacia abajo** | `RefreshRequested` | **sí**, `SwipeRefreshLayout` | El usuario lo ha pedido y espera respuesta visible. Siempre va a red, sin deduplicar. |
| Botón de reintentar | `RetryRequested` | no | Limpia el error del estado antes de volver a pedir. |

El *pull-to-refresh* es, por tanto, la única forma de forzar una lectura de red desde la UI. Como
todo pasa por `refreshProperties()` → Room → `observeVisibleProperties()`, un refresco fallido con
inmuebles ya en pantalla es un snackbar y no una pantalla de error, y los favoritos sobreviven al
refresco porque viven en otra tabla.

### Base de datos

| Tabla | Dueño | ¿Sobrevive a un borrado de caché? |
|---|---|---|
| `properties` | backend | no |
| `property_details` | backend | no |
| `property_flags` | **el usuario** | **sí** |

`property_flags` es la única tabla con datos que el usuario ha creado, y por eso no tiene clave
ajena a `properties`: un favorito sobrevive a que un inmueble desaparezca temporalmente del feed.
El refresco del listado hace *upsert* + borrado explícito de los ids que ya no vienen, en lugar de
`DELETE FROM properties`, para no arrastrarse por cascada los detalles cacheados (y con ellos, los
ascensores ya conocidos).

---

## Decisiones técnicas

| Decisión | Alternativa | Por qué esta |
|---|---|---|
| **osmdroid** para el mapa | Google Maps SDK | Sin API key: el repo enseña un mapa real al clonarlo. Con Maps sin clave se ve un rectángulo gris. El repo de osmdroid está **archivado** (6.1.20 es la última versión que existirá); ver Limitaciones. |
| **Hilt** | Koin | Errores en compilación, no en runtime; integración directa con `ViewModel`, `Fragment` y Compose; encaja bien en multi-módulo. |
| **kotlinx.serialization** | Moshi / Gson | Sin reflexión: menos reglas de R8 y compatible con el multiplataforma si el proyecto creciera. |
| **Un `UciResult` propio** | `kotlin.Result` | `Result` no se puede devolver desde una `suspend fun` sin sorpresas de boxing, y dejaría escapar `Throwable`s de Retrofit hasta la UI. `UciError` mantiene el vocabulario de fallos cerrado y traducible. |
| **Skeleton propio** | `facebook:shimmer` | 120 líneas conocidas frente a una dependencia, y respeta el ajuste de accesibilidad "quitar animaciones", que la mayoría de librerías ignora. |
| **Imágenes en una columna JSON** | tabla hija 1:N | Se conserva el orden del carrusel gratis, una fila = una lectura, y el reemplazo en el refresco es atómico. UCI nunca consulta por imagen. |
| **Enum del flag guardado como literal** | `enum.name` | R8 en `fullMode` puede renombrar constantes de enum: persistir `name` haría que el contenido de la BD dependiera del tipo de build. |
| **`java.time` con desugaring** | `SimpleDateFormat` | `SimpleDateFormat` es mutable, no *thread-safe* y no tiene el concepto de zona almacenada, que es justo lo que necesita la fecha de favorito. |
| **Compose como isla dentro de fragments** | `NavHost` de Compose anidado | Un único modelo de navegación (el grafo de Jetpack Navigation) compartido con las pantallas XML, la tab bar y los deeplinks. Anidar un segundo grafo para dos pantallas duplicaría back stacks y reglas de deeplink. |

### Estructura de `:app`

```
app/src/main/kotlin/com/ulisescervera/uci/
├── UciApplication.kt        Hilt root; configura osmdroid y el modo claro/oscuro
├── MainActivity.kt          single activity: nav host + tab bar, nada más
├── core/
│   ├── di/                  UciBindingsModule: la raíz de composición, y nada más
│   ├── mvi/                 UiState, UiIntent, UiEffect, UciViewModel
│   ├── ui/                  ViewBindingFragment, ComposeFragment, extensiones
│   ├── ui/skeleton/         SkeletonView + SkeletonShimmerDrawable
│   ├── format/              PropertyFormatter, CharacteristicsFormatter, fechas, errores
│   ├── carousel/            PropertyCarouselView + su adapter (XML)
│   ├── compose/             UciPropertyCard, UciImageCarousel, UciSkeletonBox
│   ├── map/                 UciMapConfigurator (todo el conocimiento de osmdroid)
│   ├── image/               carga con Coil + nombre de transición compartida
│   ├── share/               PropertySharer
│   └── theme/               UciTheme (la mitad Compose del tema)
└── feature/
    ├── list/                listado (XML)
    ├── detail/              detalle (XML) + DetailRowsFactory
    ├── discarded/           hoja de borrados (XML)
    ├── favourites/          favoritos (Compose)
    └── viewer/              visor de imágenes (Compose)
```

---

## Accesibilidad

No es una lista de deseos; está implementada y testeada.

- **Objetivos táctiles de 48 dp** como mínimo en todo lo pulsable (`uci_touch_target_min`). Los
  chips de datos, que **no** son pulsables, llevan `ensureMinTouchTargetSize=false` a propósito:
  la norma aplica a *targets*, y esos son etiquetas.
- **Nodos fusionados.** Una tarjeta de inmueble es **una** parada de TalkBack con una descripción
  completa (título, precio, €/m², datos), no nueve. El carrusel y los botones se reincorporan
  individualmente porque son accionables.
- **Etiqueta de acción ≠ descripción de estado.** El botón de favorito dice qué hará el toque
  (`contentDescription = "Añadir a favoritos"`) y, por separado, qué es verdad ahora
  (`stateDescription = "No está en favoritos"`). Fundirlos en la palabra "favorito" no comunica
  ninguna de las dos cosas.
- **Los skeletons son invisibles al lector de pantalla** (`importantForAccessibility="noHideDescendants"`,
  `clearAndSetSemantics {}` en Compose). Es el *contenedor* el que anuncia "Cargando inmuebles",
  una sola vez.
- **Se respeta "quitar animaciones".** Si `ANIMATOR_DURATION_SCALE` es 0, el shimmer se dibuja
  estático. Un degradado barriendo indefinidamente es un disparador documentado de migraña y de
  problemas vestibulares.
- **Anuncios de cambios transitorios.** El banner de deshacer se anuncia al aparecer: un "deshacer"
  que no se puede descubrir no es un deshacer. El carrusel anuncia "Foto 3 de 7" al cambiar de
  página.
- **El mapa lleva una descripción textual.** Un ráster de teselas es invisible para un lector de
  pantalla, así que una vista superpuesta dice en palabras dónde está el inmueble.
- **Nada se concatena en código.** Todos los textos con partes variables son *format strings* con
  marcadores posicionales, porque el orden de las palabras cambia entre idiomas. Los plurales son
  `<plurals>` reales.
- **Escalado de fuente.** Ninguna medida de texto está en `dp`; la fila de datos usa `ChipGroup`
  (XML) y `FlowRow` (Compose) para reflujo a escalas del 200 %.
- `lint` está configurado con `ContentDescription`, `ClickableViewAccessibility` y `LabelFor` como
  **fatales**: un `ImageView` sin descripción rompe el build.

Tema claro y oscuro son dos paletas verificadas por separado (`values/colors.xml` y
`values-night/colors.xml`), con contraste ≥ 4.5:1 en texto y ≥ 3:1 en componentes. No se usa
*dynamic color* porque una paleta derivada del fondo de pantalla no es verificable.

---

## Tests

Biblioteca elegida por caso, no una sola para todo:

| Capa | Herramientas | Por qué |
|---|---|---|
| `:domain` | JUnit 4 + Truth + MockK + Turbine + `coroutines-test` | Kotlin puro: los tests corren en milisegundos y sin Android. |
| `:data` mappers y errores | JUnit + Truth | Funciones puras alimentadas con fixtures de los payloads reales. |
| `:data` red | **MockWebServer** | Verifica que los DTOs parsean la forma que el challenge sirve de verdad, y fija la configuración de `Json`. |
| `:data` Room | **Robolectric** + Room in-memory | Lo que se prueba *es* el SQL. Un DAO mockeado no puede tener el bug del `INNER JOIN`. |
| `:data` BD en dispositivo | Espresso runner + `MigrationTestHelper` | El SQLite de Robolectric no es el mismo binario: colación y UTF-8 solo fallan en dispositivo. |
| `:app` formatters | **Robolectric** con `@Config(qualifiers = "es")` | El objeto del test son los *strings* y los *plurals*; un `Context` mockeado los aprobaría todos. |
| `:app` ViewModels | JUnit + Turbine + fakes de repositorio | Se testea el camino intent → caso de uso → repositorio con casos de uso **reales**. Mockearlos dejaría pasar un ViewModel que llama al caso de uso equivocado. |
| `:app` fragments | **Espresso** + Hilt testing (`@UninstallModules` + `@BindValue`) | Todo es código de producción excepto la fuente de datos. |
| `:app` Compose | `compose-ui-test-junit4` | Las pantallas son *stateless*, así que cada rama (cargando, vacío, con datos) es una línea. |

```bash
./gradlew test                    # 281 tests unitarios, sin dispositivo
./gradlew :domain:test            # instantáneo
./gradlew connectedAndroidTest    # UI, requiere emulador
```

Los dispatchers y el reloj se inyectan (`DispatcherProvider`, `UciClock`) precisamente para que
los tests no dependan del hilo ni de la hora: `FixedUciClock` permite afirmar un timestamp exacto.

---

## Ofuscación

R8 activo en `release` **en todos los módulos**, con `android.enableR8.fullMode=true`, que hace que
R8 no asuma nada sobre reflexión.

- `:app` → `isMinifyEnabled = true`, `isShrinkResources = true`, reglas en `app/proguard-rules.pro`.
- `:data` → `isMinifyEnabled = true` a nivel de librería, y lo que debe sobrevivir está en
  `data/consumer-rules.pro`, que se aplica automáticamente a `:app` (kotlinx.serialization,
  Retrofit, OkHttp, Room).
- `:domain` es un JAR plano y **no tiene pasada de R8 propia**, ni debe tenerla: convertirlo en
  `android-library` solo para eso rompería la garantía de que Android no entra en el dominio. Sus
  clases sí se ofuscan en el APK publicado, porque `:app` ejecuta R8 en *full mode* sobre todo el
  programa —este JAR incluido— con las reglas de `domain/proguard-rules.pro`. Compruébalo con
  `./gradlew assembleRelease` y el `mapping.txt`.

Se conservan `SourceFile` y `LineNumberTable` con `-renamesourcefileattribute` para que los stack
traces sigan siendo útiles sin revelar la estructura del proyecto.

---

## Deeplinks

Declarados **una sola vez**, dentro del grafo de navegación; la etiqueta `<nav-graph>` del manifest
los expande a `intent-filter`s en tiempo de build. Declararlos dos veces es cómo el manifest y el
grafo acaban desincronizados.

```
uci://property/{propertyId}
uci://property/{propertyId}/map?focusOnMap=true
https://ulisescervera.dev/uci/property/{propertyId}
```

Pruébalos con:

```bash
adb shell am start -a android.intent.action.VIEW -d "uci://property/3"
adb shell am start -a android.intent.action.VIEW -d "uci://property/1/map?focusOnMap=true"
```

Un deeplink puede llegar con la base de datos vacía en un arranque en frío. El ViewModel del
detalle lo maneja: si no hay nada cacheado, refresca primero el listado y solo después pide el
detalle. Sin eso, `observeProperty` emitiría `null` para siempre y la pantalla se quedaría en el
skeleton.

El texto que se comparte desde el detalle incluye la URL `https://`, que abre la app si está
instalada y una web si no.

---

## Limitaciones conocidas

Son limitaciones del challenge, no del wiring. Están listadas porque ocultarlas sería peor.

1. **`detail.json` es un fixture estático.** Devuelve siempre `adid: 1`, sin parámetros. La app
   almacena la respuesta bajo el id que se pidió (`PropertyDetailMapper.toEntity`), así que abrir
   el inmueble 3 funciona, pero el comentario y las características que se ven son los del inmueble
   1. El día que el endpoint acepte un id, no hay que cambiar nada.
2. **`list.json` no informa del ascensor.** Solo lo hace el detalle. Por eso `Property.hasLift` es
   `Boolean?` y la lista muestra "Ascensor sin confirmar" hasta que el detalle de ese inmueble se
   ha abierto una vez y se ha cacheado. A partir de entonces la lista lo muestra correctamente,
   porque el mapper lo lee del `@Relation` con `property_details`.
3. **`detail.json` no informa del garaje.** El único origen es el resumen cacheado del listado, y
   así está implementado.
4. **El endpoint de inmuebles relacionados no existe.** Lo simula
   `FakeRelatedPropertiesService`: ordena el catálogo cacheado por similitud (misma operación →
   precio más cercano → tamaño más cercano) y paga 650 ms de latencia para que el skeleton se vea.
   Es una interfaz, así que sustituirlo por un servicio Retrofit real es cambiar una línea en
   `app/…/core/di/UciBindingsModule.kt`.
5. **Las imágenes son de `img4.idealista.com` y pueden caducar.** Coil cae al placeholder, que es
   una caja gris con un icono de foto, y nada se rompe.
6. **`assembleRelease` usa la keystore de debug versionada.** Cámbiala antes de distribuir.
7. **Sin migraciones de Room todavía** (esquema en versión 1). No hay
   `fallbackToDestructiveMigration()` a propósito: tirar los favoritos del usuario en el primer
   cambio de esquema no es aceptable, y el harness de `MigrationTestHelper` ya está montado para
   cuando toque. Falta un paso deliberado: la carpeta de esquemas no está en el *asset path* de
   `androidTest`, porque nada la lee en la versión 1. Está anotado en el propio test.
8. **osmdroid está archivado.** 6.1.20 es la última versión que existirá (último commit en
   noviembre de 2024). No hay riesgo técnico inmediato —la API que usamos es estable y el AAR no
   arrastra dependencias— pero tampoco habrá parches. A medio plazo la alternativa sin API key es
   MapLibre.
9. **`swiperefreshlayout` está anclada en 1.1.0.** La 1.2.0 cambia el manejo táctil
   (`requestDisallowInterceptTouchEvent` pasa a respetarse), y eso cae justo sobre el
   `ViewPager2` dentro de `RecyclerView` con un mapa dentro. Subirla requiere probar el arrastre
   horizontal en un dispositivo real.

---

## Documentación adicional

- [`CLAUDE.md`](CLAUDE.md) — convenciones, metodología y formas de trabajar en este repo.
- [`docs/adr/`](docs/adr/) — decisiones de arquitectura con sus alternativas descartadas.
- [`.claude/skills/`](.claude/skills/) — nueve skills que generan cada tipo de artefacto del
  proyecto siguiendo estas convenciones.
