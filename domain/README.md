# `:domain`

Módulo Kotlin/JVM puro. **No conoce Android**, y eso no es una convención sino una barrera que
vigila el compilador: no hay `Context`, no hay `View`, no hay Retrofit, no hay Room. Sus únicas
dependencias son `kotlinx-coroutines-core` (por `Flow`, que aparece en la ABI de los repositorios)
y `javax.inject` (solo la anotación, no un contenedor).

```
domain/src/main/kotlin/com/ulisescervera/uci/domain/
├── common/       UciResult, UciError, DispatcherProvider, UciClock
├── model/        Property, PropertyDetail, Money, Floor, PropertyFlag, GeoPoint…
├── repository/   PropertyRepository, RelatedPropertiesRepository
└── usecase/      12 casos de uso, uno por capacidad
```

Dos repositorios, no tres: `PropertyFlagRepository` existió y se absorbió en `PropertyRepository`
porque partía el concepto "favorito" en dos contratos —se leía por uno y se escribía por el otro—.
El razonamiento está en
[`docs/adr/0002-un-repositorio-por-agregado.md`](../docs/adr/0002-un-repositorio-por-agregado.md).

---

## Estudio: ¿es este el lugar correcto para definir los intents de la vista?

El enunciado pide estudiarlo explícitamente. **La respuesta es no.** Los intents viven en `:app`,
junto al ViewModel que los consume. `:domain` expone *capacidades* (casos de uso); `:app` expone
*gestos* (intents).

Tres pruebas lo deciden:

1. **Cardinalidad.** `PropertyListIntent.DiscardClicked`,
   `PropertyListIntent.UndoLastDiscardClicked` y `DiscardedPropertiesIntent.RestoreAllClicked` acaban
   los tres en un puñado de casos de uso compartidos. La correspondencia intent → caso de uso es
   N:M, así que ninguno de los dos conjuntos puede derivarse del otro. Con los intents aquí
   tendríamos tres tipos que solo se diferencian por *qué botón* los originó.
2. **Motivo de cambio.** Rediseñar el listado (mover el borrado a un swipe, añadir un menú
   contextual) cambia los intents y no cambia el dominio. Al contrario: cambiar la regla de
   exclusividad favorito/descartado cambia el dominio y no cambia los intents. Son ejes de cambio
   independientes.
3. **Payload.** Los intents transportan tipos de presentación —posición en el `RecyclerView`,
   índice de la imagen del carrusel para la transición compartida— que este módulo no puede
   conocer: no tiene `android.view.View` en el classpath. El compilador lo impide.

Dicho de otro modo: si esta app tuviera mañana una interfaz de voz o un companion de Wear OS,
reutilizaría los 12 casos de uso íntegros y tiraría el 100 % de los intents.

El razonamiento completo, con las alternativas descartadas, está en
[`docs/adr/0001-donde-viven-los-intents-de-la-vista.md`](../docs/adr/0001-donde-viven-los-intents-de-la-vista.md).

---

## Qué sí pertenece a este módulo

- **Modelos y sus invariantes.** `PropertyType.isVerticalHousing` (la planta y el ascensor solo
  existen en vivienda con plantas), la exclusividad favorito/descartado codificada en un enum, la
  validación de `GeoPoint.orNull` (rechaza coordenadas parciales, fuera de rango y `0,0`).
- **Interfaces de repositorio.** El contrato, no la implementación. Una por agregado, no una por
  origen del dato: los flags del usuario viven en `PropertyRepository` aunque no vengan de la red.
- **Casos de uso**, uno por capacidad, con `operator fun invoke`.
- **`UciResult` / `UciError`.** El vocabulario cerrado de fallos, para que un `when` exhaustivo
  obligue a decidir qué se le cuenta al usuario en cada caso.

## Qué no pertenece

- Formateo de texto: necesita `Context` y locale → `PropertyFormatter`, en `:app`.
- Serialización y persistencia: `@Serializable`, `@Entity` → `:data`.
- Intents, estados de UI y efectos → `:app`.

## Ofuscación

Este módulo es un JAR plano y **no tiene pasada de R8 propia**, ni debe tenerla: convertirlo en
`android-library` solo para conseguirla rompería la garantía de que Android no entra en el dominio.
Sus clases sí se ofuscan en el APK publicado, porque `:app` ejecuta R8 en *full mode* sobre todo el
programa —este JAR incluido— con las reglas de `domain/proguard-rules.pro`.

## Tests

`./gradlew :domain:test`. Corren en milisegundos: JUnit 4 + Truth, fakes escritos a mano para los
repositorios con estado y MockK para los delegadores puros, con `kotlinx-coroutines-test` para las
funciones `suspend`.
