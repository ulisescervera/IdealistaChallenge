---
name: uci-crear-modelo
description: Crea un modelo de negocio en el módulo :domain de UCI (Ulises-Cervera-Idealista), con sus invariantes, sus tipos cerrados y su test. Úsala cuando haya que representar un concepto nuevo del negocio inmobiliario -- un tipo de inmueble, un estado, un dato del anuncio -- o cuando un campo de un DTO tenga que dejar de ser un String suelto.
---

# Crear un modelo de `:domain`

## Antes de escribir nada

Mira el payload real. Los dos endpoints están en el README y sus rarezas han determinado la mitad
de los modelos existentes. Preguntas a responder **antes** de teclear:

1. ¿El backend puede omitir este campo? → nullable, y `null` significa **"no informado"**, que no
   es lo mismo que `false` ni que `0`.
2. ¿Tiene un conjunto cerrado de valores? → `enum` con `apiValue` y un `from()` que **nunca lanza**.
3. ¿Tiene un conjunto cerrado pero con variantes que llevan datos? → `sealed interface`.
4. ¿Se puede derivar de otro campo? → propiedad calculada, nunca un campo almacenado. Un valor
   almacenado puede acabar contradiciendo su origen.
5. ¿Se puede construir en un estado ilegal? → constructor privado + *factory* que devuelve nullable.

## Ubicación

```
domain/src/main/kotlin/com/ulisescervera/uci/domain/model/<Nombre>.kt
domain/src/test/kotlin/com/ulisescervera/uci/domain/model/<Nombre>Test.kt
```

Sin prefijo `Uci`: los modelos son vocabulario del negocio (`Property`, `Money`, `Floor`), no del
proyecto.

## Plantillas

### Enum con valor de API

```kotlin
package com.ulisescervera.uci.domain.model

/**
 * <Qué representa.>
 *
 * El backend manda un string libre, así que [from] nunca lanza: un valor
 * desconocido degrada a [UNKNOWN] en lugar de tirar el inmueble entero.
 */
enum class <Nombre>(val apiValue: String) {
    VALOR_A("valorA"),
    UNKNOWN("");

    companion object {
        fun from(apiValue: String?): <Nombre> =
            entries.firstOrNull { it.apiValue.equals(apiValue, ignoreCase = true) } ?: UNKNOWN
    }
}
```

`ignoreCase` no es paranoia: el payload del challenge no es consistente en mayúsculas.

### Tipo sellado para un campo que no siempre es un número

Patrón de `Floor`: el API manda `"2"`, pero también `"bj"`, `"ss"`, `"en"`.

```kotlin
sealed interface <Nombre> {
    data class Numbered(val value: Int) : <Nombre>
    data object SpecialCase : <Nombre>
    /** El tipo de inmueble hace que este dato no exista. */
    data object NotApplicable : <Nombre>
    /** Venía en el payload pero no lo reconocemos; se guarda crudo para poder mostrarlo. */
    data class Unknown(val raw: String) : <Nombre>
    /** No venía en el payload. */
    data object Missing : <Nombre>

    companion object {
        fun from(raw: String?, context: PropertyType): <Nombre> = when { /* … */ }
    }
}
```

`NotApplicable` y `Missing` son **distintos** a propósito: "un chalet no tiene planta" y "no
sabemos en qué planta está" se renderizan igual (nada) pero significan cosas diferentes, y algún
día una de las dos necesitará copy propio.

### Valor con validación

Patrón de `GeoPoint`: mejor `null` que un dato que parece bueno y no lo es.

```kotlin
data class <Nombre>(val a: Double, val b: Double) {
    companion object {
        fun orNull(a: Double?, b: Double?): <Nombre>? {
            if (a == null || b == null) return null
            if (a !in VALID_RANGE) return null
            return <Nombre>(a, b)
        }
    }
}
```

Un `0.0, 0.0` de latitud/longitud es lo que manda un backend cuando quiere decir "no lo sé":
`GeoPoint.orNull` lo rechaza porque un pin en el Golfo de Guinea es peor que ocultar el botón.

### Agregado

```kotlin
data class <Nombre>(
    val id: String,
    val closedTypeField: SomeEnum,
    /** `null` mientras no se sepa. Ver README > Limitaciones. */
    val maybeUnknown: Boolean?,
    val flag: PropertyFlag = PropertyFlag.NONE,
) {
    /** Calculada, nunca almacenada: así no puede contradecir su origen. */
    val derived: Money? get() = price.perSquareMeter(size)
}
```

## Prohibido en `:domain`

- `android.*`, `androidx.*` — el módulo es `kotlin-jvm` y el compilador lo impide.
- `@Serializable` — eso es del DTO.
- `@Entity`, `@ColumnInfo` — eso es de la entidad.
- `@DrawableRes`, `@StringRes` — eso es de la vista.
- Un `getDisplayName()`. Formatear necesita `Context` y locale: es `PropertyFormatter` en `:app`.

## Test obligatorio

Un test por invariante, con el nombre en forma de regla:

```kotlin
@Test
fun `an unknown value degrades instead of throwing`() {
    assertThat(<Nombre>.from("houseboat")).isEqualTo(<Nombre>.UNKNOWN)
    assertThat(<Nombre>.from(null)).isEqualTo(<Nombre>.UNKNOWN)
}
```

Cubre siempre: valor válido, valor con otra capitalización, valor desconocido, `null`, y cada
estado ilegal que la *factory* debe rechazar.

## Después

- Añade el campo al mapper (`PropertyEntityMapper` / `PropertyDetailMapper`) y a la entidad si hay
  que persistirlo.
- Si el modelo se renderiza, añade el método a `PropertyFormatter` con su `<string>` en `values/`
  **y** en `values-en/`.
- Si el enum se persiste, añade la rama al `when` de `UciTypeConverters` — literal explícito, nunca
  `enum.name`.
