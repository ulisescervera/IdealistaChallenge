---
name: uci-crear-entidad
description: Crea una entidad de Room con su DAO y su test en el módulo :data de UCI (Ulises-Cervera-Idealista). Úsala cuando haya que persistir datos nuevos, añadir una consulta o cambiar el esquema local.
---

# Crear una entidad de Room y su DAO

## Antes de escribir nada

Responde a **quién es el dueño de estos datos**, porque de ahí sale todo lo demás:

| Dueño | ¿Sobrevive a un borrado de caché? | Ejemplo |
|---|---|---|
| El backend | no | `properties`, `property_details` |
| **El usuario** | **sí** | `property_flags` |

Datos del backend son una caché reemplazable. Datos del usuario son los únicos que no se pueden
recuperar, y por eso `property_flags` **no** tiene clave ajena a `properties`: un favorito
sobrevive a que un inmueble desaparezca temporalmente del feed.

## Ubicación

```
data/src/main/kotlin/com/ulisescervera/uci/data/local/entity/<Nombre>Entity.kt
data/src/main/kotlin/com/ulisescervera/uci/data/local/dao/<Nombre>Dao.kt
data/src/test/kotlin/com/ulisescervera/uci/data/local/UciDaoTest.kt   (amplíalo)
```

## Plantilla de entidad

```kotlin
@Entity(tableName = <Nombre>Entity.TABLE)
data class <Nombre>Entity(
    @PrimaryKey
    @ColumnInfo(name = "property_code") val propertyCode: String,
    @ColumnInfo(name = "snake_case") val camelCase: String,
    @ColumnInfo(name = "cached_at") val cachedAtEpochMillis: Long,
) {
    companion object {
        const val TABLE = "<tabla_en_snake_case>"
    }
}
```

Reglas:

- **Columnas en `snake_case` con `@ColumnInfo` explícito.** El nombre de la columna es un contrato
  con las migraciones; que dependa del nombre de la propiedad Kotlin es cómo un refactor inocente
  se convierte en una migración destructiva.
- `TABLE` como constante, y úsala en las `@Query` que puedas. Room necesita SQL constante, así que
  no siempre se puede — cuando no, el nombre literal va en la query y la constante documenta el
  emparejamiento.
- **Fila plana, no `@Embedded`.** Room soporta objetos anidados, pero una fila plana mantiene el SQL
  generado legible y hace cada campo consultable si algún día hay un filtro.
- **Nullable = "no informado".** Si el backend puede omitirlo, la columna es nullable, y `null` no
  significa `false`.
- **Un campo por dueño.** No dupliques en la caché del backend un dato que solo conoce el detalle
  (el ascensor); se sobrescribiría en cada refresco. Únelo con un `@Relation`.

## Enums y listas

Un enum se persiste con un **literal explícito**, nunca con `enum.name`: R8 en `fullMode` puede
renombrar constantes de enum y el contenido de la BD dependería del tipo de build.

```kotlin
@TypeConverter
fun flagToToken(flag: PropertyFlag): String = when (flag) {
    PropertyFlag.NONE -> Flags.NONE
    PropertyFlag.FAVOURITE -> Flags.FAVOURITE
    PropertyFlag.DISCARDED -> Flags.DISCARDED
}
```

El `when` exhaustivo hace que añadir un valor al enum sea un error de compilación aquí — que es
justo el momento en el que hay que pensar en la migración.

Una lista se guarda como columna JSON cuando el orden importa y nunca se consulta por sus
elementos (ver `ImageRecord`). Si vas a filtrar por los elementos, haz una tabla hija.

## Plantilla de DAO

```kotlin
@Dao
interface <Nombre>Dao {

    @Transaction
    @Query(
        """
        SELECT properties.* FROM properties
        LEFT JOIN property_flags ON properties.property_code = property_flags.property_code
        WHERE property_flags.flag IS NULL OR property_flags.flag != 'discarded'
        ORDER BY properties.order_in_feed ASC
        """,
    )
    fun observeVisible(): Flow<List<PropertyWithLocalState>>

    @Upsert
    suspend fun upsertAll(entities: List<<Nombre>Entity>)
}
```

Reglas:

- **`LEFT JOIN` + `IS NULL`** cuando la tabla unida es opcional. La mayoría de inmuebles no tiene
  fila en `property_flags`, y un `INNER JOIN` devolvería lista vacía en una instalación nueva. Este
  bug es exactamente el que un DAO mockeado no puede tener y uno real sí.
- **`@Transaction` en toda consulta que devuelva un POJO con `@Relation`**, o las dos consultas se
  ejecutan sin aislamiento.
- **`@Upsert`, no `@Insert(REPLACE)`.** `REPLACE` borra e inserta, lo que dispara las cascadas de
  claves ajenas.
- **Refrescar sin destruir.** El patrón del proyecto es `upsertAll` + borrado explícito de los ids
  que ya no vienen, todo en un `@Transaction`. Un `DELETE FROM` + insert se llevaría por cascada los
  detalles cacheados.
- Un `Flow` para leer, `suspend` para escribir. `Flow` nunca falla; los errores se manejan en el
  repositorio.
- Proyecciones estrechas para vistas que solo necesitan cuatro campos (ver `DiscardedPropertyRow`):
  leer la fila completa arrastraría cada blob JSON de imágenes a un bottom sheet.

## Test obligatorio

Room **in-memory** con Robolectric. Lo que se prueba *es* el SQL, así que un mock no vale.

```kotlin
@Test
fun `a fresh install with no flags still shows every property`() = runTest {
    propertyDao.replaceFeed(listOf(entity("1"), entity("2")))

    assertThat(propertyDao.observeVisible().first()).hasSize(2)
}
```

Cubre siempre: instalación nueva sin filas en la tabla unida, el orden del `ORDER BY`, la
exclusión que promete la query, y que un refresco no destruye datos del usuario.

## R8

Las anotaciones de Room (`@Entity`, `@ColumnInfo`…) tienen retención **CLASS**, es decir son
*invisibles* en runtime. Desde R8 9.2 el comodín `-keepattributes *Annotation*` **ya no las
conserva**: `data/consumer-rules.pro` las lista explícitamente
(`RuntimeInvisibleAnnotations`, etc.). Si añades una anotación nueva que se lea por reflexión,
comprueba su retención antes de confiar en un comodín.

## Migraciones

Al cambiar el esquema:

1. Sube `UciDatabase.VERSION`.
2. Escribe la `Migration` — **no** añadas `fallbackToDestructiveMigration()`: tiraría los favoritos
   del usuario, que es la única tabla no recuperable.
3. Añade un test con `MigrationTestHelper` contra el JSON exportado en `data/schemas/`.
4. Commitea el JSON nuevo.
