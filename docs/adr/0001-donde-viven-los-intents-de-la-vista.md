# ADR 0001 — ¿Dónde se definen los intents que puede hacer la vista?

- **Estado:** aceptada
- **Fecha:** 2026-09-09
- **Contexto del enunciado:** *"Crea un módulo domain, donde habrá usecase, interfaces de repositorios. Estudia si este es el lugar correcto para definir los intents que puede hacer la vista."*

## Decisión

**No.** Los intents viven en `:app`, junto al ViewModel que los consume.
`:domain` expone *capacidades* (casos de uso); `:app` expone *gestos* (intents).

```
app/…/feature/list/PropertyListIntent.kt      ← aquí
domain/…/usecase/DiscardPropertyUseCase.kt    ← no aquí
```

## Razonamiento

Un intent no es una regla de negocio, es el nombre de un gesto de una pantalla concreta.
Tres pruebas lo dejan claro:

1. **Cardinalidad.** `PropertyListIntent.DiscardClicked`,
   `PropertyListIntent.UndoLastDiscardClicked` y `DiscardedPropertiesIntent.RestoreAllClicked`
   acaban los tres en un puñado de casos de uso compartidos.
   La correspondencia intent → caso de uso es N:M, así que ninguno de los dos conjuntos puede
   derivarse del otro. Si los intents estuvieran en `:domain` tendríamos allí tres tipos que
   solo se diferencian por *qué botón* los originó.

2. **Motivo de cambio.** Rediseñar el listado (pasar el borrado a un swipe, añadir un menú
   contextual) cambia los intents y no cambia el dominio. Al contrario: cambiar la regla de
   exclusividad favorito/descartado cambia el dominio y no cambia los intents. Son ejes de cambio
   independientes; el ADR los mantiene en módulos independientes.

3. **Payload.** Los intents transportan tipos de presentación —posición en el `RecyclerView`,
   índice de la imagen del carrusel para la transición compartida, `View` de origen— que `:domain`
   no puede ni debe conocer siendo un módulo Kotlin puro sin Android en el classpath.
   De hecho el compilador lo impide: `:domain` no tiene `android.view.View`.

Dicho de otro modo: si mañana esta misma app tuviera una interfaz de voz o un Wear OS companion,
reutilizaría los 12 casos de uso íntegros y tiraría el 100 % de los intents.

## Qué sí pertenece a `:domain`

- Modelos (`Property`, `PropertyDetail`, `PropertyFlag`, `Floor`…) y sus reglas invariantes:
  `PropertyType.isVerticalHousing`, la exclusividad favorito/descartado, la validación de
  `GeoPoint.orNull`.
- Interfaces de repositorio.
- Casos de uso, uno por capacidad.
- `UciResult` / `UciError`: el vocabulario cerrado de fallos.

## Consecuencias

- `:app` define un `UiIntent` por feature. El contrato común (`UciViewModel`, `UiState`, `UiEffect`)
  está en `app/…/core/mvi/`.
- Cada ViewModel traduce intents a casos de uso en un único `when` exhaustivo, así que añadir un
  intent sin gestionarlo es un error de compilación.
- El coste es una capa de traducción explícita en cada ViewModel. Es un coste que se paga a gusto:
  es justo el sitio donde se quiere leer "qué hace esta pantalla cuando el usuario toca aquí".

## Alternativas descartadas

| Alternativa | Por qué no |
|---|---|
| Intents en `:domain` como sellada única `UciIntent` | Acopla el dominio a la navegación y a la forma actual de las pantallas; obliga a un `when` gigante con ramas imposibles por pantalla. |
| Sin intents: el fragment llama métodos del ViewModel | Es una opción defendible y más corta, pero pierde la traza serializable de "qué hizo el usuario" que hace triviales los tests de ViewModel y la reproducción de bugs. |
| Intents en un módulo `:presentation` aparte | Añade un cuarto módulo cuyo único cliente es `:app`. Coste de build sin beneficio de aislamiento. |
