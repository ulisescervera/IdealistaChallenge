# ADR 0002 — Un repositorio por agregado, no por propietario del dato

- **Estado:** aceptada
- **Fecha:** 2026-09-10
- **Supersede parcialmente a:** nada. Corrige una decisión implícita tomada al construir `:domain`.

## Contexto

`:domain` nació con tres interfaces de repositorio:

| Interfaz | Métodos | Idea original |
|---|---|---|
| `PropertyRepository` | 8 | Datos del backend: catálogo, detalle, refrescos |
| `PropertyFlagRepository` | 4 | Datos del usuario: favoritos y descartados |
| `RelatedPropertiesRepository` | 1 | Recomendaciones (servicio simulado) |

El eje de la separación entre las dos primeras era **quién es el dueño del dato**: `properties` es
una caché reemplazable que llega de `list.json`; `property_flags` es lo único que el usuario crea,
no viene en ningún DTO y debe sobrevivir a cualquier refresco.

Es una distinción real. El problema es que no era la distinción que el código necesitaba en el
*contrato*.

## Decisión

**Dos repositorios, no tres.** `PropertyFlagRepository` desaparece: tres de sus cuatro métodos se
absorben en `PropertyRepository` y el cuarto se borra. 8 + 4 − 2 muertos = **10**.

```
PropertyRepository          ← catálogo, detalle, refrescos y flags del usuario
RelatedPropertiesRepository ← recomendaciones
```

La separación **sobrevive en la implementación**: `PropertyRepositoryImpl` delega los tres métodos
de flags en `PropertyFlagStore`, una clase de `:data` que no implementa ninguna interfaz de
`:domain` y que sigue siendo el único escritor de `property_flags`.

De paso se eliminan dos métodos muertos, `isCacheEmpty()` y `observeFlag()`: ningún llamante fuera
de sus propios tests. Un test que solo prueba código que nadie usa no es cobertura, es lastre.

## Razonamiento

### 1. El concepto "favorito" estaba a caballo entre las dos interfaces

Este es el argumento decisivo. Con el diseño anterior:

```kotlin
propertyRepository.observeFavouriteProperties()   // se lee aquí
flagRepository.setFlag(id, PropertyFlag.FAVOURITE) // se escribe allí
```

Una costura debe separar cosas que cambian por motivos distintos. Esta partía un concepto por la
mitad: cualquier cambio en cómo se modela "favorito" —añadir un tercer estado, permitir carpetas,
guardar una nota— tocaría **las dos** interfaces a la vez. Eso es exactamente la señal de que la
línea está mal trazada.

### 2. Los cuatro casos de uso de escritura necesitaban ambos lados de todas formas

`ToggleFavouriteUseCase` lee el estado actual (`flagOf`) antes de escribirlo, y su efecto observable
—que el inmueble aparezca o desaparezca de una lista— se materializa a través de queries del otro
repositorio. Los ViewModels ya recibían los dos, siempre juntos; ningún consumidor usaba uno sin el
otro. Una interfaz que nunca se inyecta sola no está desacoplando nada.

### 3. Es un solo agregado y una sola transacción conceptual

El criterio útil para trocear repositorios no es el origen del dato, es el **agregado**: la unidad
de consistencia. Aquí el agregado es el inmueble, y la exclusividad favorito/descartado es su
invariante —una columna, una escritura, estados imposibles irrepresentables (ver regla 3 de
`CLAUDE.md`). Repartir los guardianes de una invariante entre dos contratos hace que ninguno de
los dos pueda garantizarla por sí mismo.

### 4. Contar métodos no es contar responsabilidades

La objeción esperable es que diez métodos son muchos. Pero un repositorio de un agregado con
lecturas observables y escrituras tiende a ese tamaño, y los consumidores **no ven diez métodos**:
ven un caso de uso con un método, que es la capa que existe precisamente para eso (ADR 0001, y el
`Observe…UseCase` de una línea que el skill `/uci-crear-caso-de-uso` documenta como aceptable).

Si el número creciera de verdad, la respuesta correcta sería partir por agregado —un
`SearchRepository`, un `UserProfileRepository`— no volver a partir este por propietario del dato.

## Consecuencias

**A favor**

- Un solo sitio donde buscar cualquier cosa relacionada con un inmueble.
- Un fake por módulo de test en lugar de dos, y los tests de ViewModel dejan de construir dos
  dobles que siempre iban en pareja.
- `UciBindingsModule` baja de cuatro `@Binds` a tres.
- Se van 2 métodos y ~40 líneas de código muerto.

**En contra, y asumido**

- `PropertyRepository` es la interfaz más grande de `:domain`. Aceptable mientras siga siendo *un*
  agregado; si aparece un segundo, se parte por ahí.
- `PropertyRepositoryImpl` gana un colaborador. A cambio no gana los cuatro parámetros de
  constructor que tendría si absorbiera la lógica de flags: `PropertyFlagStore` sigue siendo el
  único escritor de `property_flags`, con su reloj y su zona horaria.
- La delegación es literal (tres métodos de una línea). Es deliberado: envolverla en otro
  `runCatchingUci` enterraría un error real dentro de un segundo `try/catch` sin sentido.

**Lo que no cambia**

La distinción caché/dato-de-usuario, que era correcta, sigue viva donde de verdad aplica:

- En el **esquema**: `property_flags` no tiene clave ajena a `properties`, así que un refresco que
  borre y reescriba el catálogo no puede llevarse por delante un favorito.
- En la **implementación**: `PropertyFlagStore`, único escritor de esa tabla.

Cohesión en el borde, separación por detrás.

### Nota: por qué `PropertyFlagStore` no es `internal`

Lo fue durante un commit y no compila. `PropertyRepositoryImpl` es público porque `:app` lo nombra
en `UciBindingsModule`, y en Kotlin **un constructor público no puede aceptar un parámetro de un
tipo menos visible** (`EXPOSED_PARAMETER_TYPE`, que es error, no aviso).

De las tres salidas se eligió la barata. `internal constructor` depende de que Dagger tolere una
visibilidad que no documenta. Hacer `internal` el propio `PropertyRepositoryImpl` obligaría a mover
la raíz de composición a `:data` y contradiría el ADR 0001.

Y la garantía que se pierde era en buena medida decorativa: `PropertyFlagDao` es público y está
expuesto vía `@Provides` en `DatabaseModule`, así que cualquier clase de `:app` ya tenía un camino
**más corto** a esa tabla que pasar por aquí. "Solo `PropertyRepositoryImpl` escribe flags" es una
convención que sostiene la revisión de código, no el compilador. Siempre lo fue; ahora está dicho.

## Alternativas descartadas

**Dejar las tres interfaces.** Mantiene el problema del punto 1 sin dar nada a cambio: ningún
consumidor se beneficiaba de poder depender de una sola.

**Un único repositorio con las tres.** `RelatedPropertiesRepository` tiene otro origen (un servicio
simulado, no la base de datos), otro modo de fallo y ningún estado compartido; unirlo sería agrupar
por tipo de retorno, que es la razón que motivó este ADR.

**Interfaces segregadas al estilo ISP** (`PropertyReader`, `PropertyWriter`, `FlagWriter`) con una
única implementación. Tres tipos para que cada consumidor declare el subconjunto que usa. En un
proyecto con un solo implementador y una sola app, el coste de navegación supera al beneficio, y
los casos de uso ya cumplen esa función de fachada estrecha.
