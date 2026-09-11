---
name: uci-crear-dto
description: Crea un DTO de kotlinx.serialization en el módulo :data de UCI (Ulises-Cervera-Idealista), con su mapper y su test de parseo contra MockWebServer. Úsala cuando haya que consumir un endpoint nuevo o cuando el payload existente añada campos.
---

# Crear un DTO

## Antes de escribir nada

**Copia el JSON real y míralo.** No el que describe la documentación: el que devuelve el servidor.
Los DTOs de este proyecto existen con la forma que tienen por rarezas concretas del payload del
challenge:

- `list.json` anida el importe un nivel más que `detail.json`
  (`priceInfo.price.amount` vs `priceInfo.amount`) → **dos DTOs**, no uno permisivo.
- El `price` de la raíz de un alquiler es el precio de **venta** y difiere de `priceInfo` en tres
  órdenes de magnitud.
- `propertyType` en el detalle es la *familia* (`"homes"`); el tipo de vivienda está en
  `extendedPropertyType`.
- `features` trae cinco claves en un inmueble y dos en otro. `parkingSpace` existe en uno y no en
  otro.

## Ubicación

```
data/src/main/kotlin/com/ulisescervera/uci/data/network/dto/<Nombre>Dto.kt
data/src/test/kotlin/com/ulisescervera/uci/data/network/<Nombre>ApiTest.kt
```

Fragmentos compartidos entre endpoints → `CommonDto.kt`.

## Plantilla

```kotlin
package com.ulisescervera.uci.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * <Endpoint que lo devuelve.>
 *
 * <Las trampas del payload, si tiene alguna.>
 */
@Serializable
data class <Nombre>Dto(
    @SerialName("campoDelJson") val campo: String? = null,
    @SerialName("otroCampo") val otro: Double? = null,
    @SerialName("anidado") val anidado: OtroDto? = null,
    @SerialName("lista") val lista: List<ItemDto> = emptyList(),
)
```

## Reglas

1. **Todo nullable con valor por defecto.** No es ruido defensivo: el payload real omite claves por
   inmueble. Un campo no nulo aquí es un fallo de parseo que se lleva por delante la lista entera.
   Excepción: una `List` va con `= emptyList()` en lugar de nullable, porque "sin elementos" y "sin
   la clave" se renderizan igual.
2. **`@SerialName` explícito siempre**, incluso si coincide con el nombre de la propiedad. Así R8
   puede ofuscar la clase libremente y un renombrado en Kotlin no rompe el parseo.
3. **Espejo del cable y nada más.** Sin enums, sin `Instant`, sin valores calculados, sin
   `require`. Interpretar es trabajo del mapper; así un renombrado del backend es una línea aquí en
   lugar de un refactor.
4. **Un DTO por forma, no por concepto.** Si dos endpoints anidan el mismo dato de forma distinta,
   son dos DTOs. La diferencia queda documentada por el sistema de tipos en lugar de por un
   comentario que se queda obsoleto.
5. `data class` siempre: la igualdad estructural hace que los tests se lean bien.

## Mapper

Un DTO no llega nunca a `:domain`. El mapper (`data/mapper/`) lo convierte en entidad:

```kotlin
@Singleton
class <Nombre>Mapper @Inject constructor(
    private val clock: UciClock,   // si hay que sellar la fila con `cached_at`
) {
    fun toEntity(dto: <Nombre>Dto): <Nombre>Entity = <Nombre>Entity(
        // El id no se puede inventar: si falta, el elemento se descarta.
        id = requireNotNull(dto.id),
        // priceInfo manda sobre el price de la raíz. Ver el KDoc de PropertyDtoMapper.
        amount = dto.priceInfo?.price?.amount ?: dto.price ?: 0.0,
    )
}
```

Decisiones que el mapper documenta en comentarios, no en el README:

- qué campo gana cuando dos se contradicen, **y qué pasa si eliges el otro**;
- qué elementos se descartan y por qué (sin id no se puede abrir, ni marcar, ni descartar);
- qué se conserva del orden del backend (`orderInFeed`: es un ranking de relevancia).

## Test obligatorio

Contra `MockWebServer`, con el JSON **real** recortado. El test tiene dos objetivos: que los DTOs
parseen lo que el servidor sirve, y fijar la configuración de `Json`.

```kotlin
@Test
fun `unknown server fields are ignored instead of failing the request`() = runTest {
    server.enqueue(jsonResponse("""[{"propertyCode":"1","brandNewField":{"nested":true}}]"""))

    val items = api.properties()

    assertThat(items.single().propertyCode).isEqualTo("1")
}
```

Cubre siempre:

- el payload completo, con sus rarezas incluidas (no las suavices en el fixture: harías el test
  inútil);
- un campo desconocido → se ignora (`ignoreUnknownKeys`);
- un array vacío → lista vacía, no error;
- un 500 → `HttpException`, para que `ErrorMapper` la traduzca;
- las rutas de los endpoints.

El converter es el de primera parte de Retrofit
(`retrofit2.converter.kotlinx.serialization.asConverterFactory`), no el de JakeWharton — ese
artefacto está archivado desde que Retrofit lo absorbió.

`Json` en el test se configura **igual** que en `NetworkModule`: `ignoreUnknownKeys = true`,
`coerceInputValues = true`, `explicitNulls = false`, `isLenient = false`. `isLenient` se deja en
`false` a propósito: un body malformado debe salir como `UciError.Serialization`, no parsearse a
medias.

## Después

- Añade el método al `UciPropertyApi` (o al servicio correspondiente).
- Añade el mapper y su test de mapeo (funciones puras, sin Robolectric).
- Si aparece un campo nuevo que hay que persistir, añade la columna a la entidad y **sube la versión
  de `UciDatabase` con su migración**.
- Comprueba que `data/consumer-rules.pro` cubre el nuevo `@Serializable` (las reglas son genéricas,
  normalmente sí).
