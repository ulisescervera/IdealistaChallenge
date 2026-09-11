---
name: uci-crear-vista
description: Crea una vista de UCI (Ulises-Cervera-Idealista) -- layout XML con ViewBinding o composable -- con accesibilidad, skeleton de carga y soporte de tema claro y oscuro. Úsala al maquetar cualquier pantalla, fila de lista o componente reutilizable.
---

# Crear una vista

## Nombres

| Qué | Patrón | Ejemplo |
|---|---|---|
| Layout de pantalla | `uci_fragment_<nombre>.xml` | `uci_fragment_property_list.xml` |
| Fila de lista | `uci_item_<nombre>.xml` | `uci_item_property.xml` |
| Componente reutilizable | `uci_view_<nombre>.xml` | `uci_view_carousel.xml` |
| Skeleton | `uci_item_<nombre>_skeleton.xml` | `uci_item_property_skeleton.xml` |
| Id de vista | `uci` + camelCase | `uciPropertyFavouriteButton` |

ViewBinding genera `UciItemPropertyBinding` a partir de `uci_item_property.xml`.

## Reglas de maquetación

1. **Ninguna medida literal.** Todo sale de `dimens.xml` (`uci_space_*`, `uci_touch_target_min`,
   `uci_carousel_height`). `values-w600dp/dimens.xml` adapta tablets sin tocar un layout.
2. **Ningún color literal.** Atributos del tema (`?attr/colorSurface`, `?attr/colorOnSurfaceVariant`)
   o `@color/uci_*`. Un color escrito a mano no tiene equivalente en modo oscuro.
3. **Ningún tamaño de texto.** `android:textAppearance="@style/TextAppearance.Uci.*"`, que hereda
   del type scale de Material 3 y respeta `fontScale`.
4. **48 dp mínimo en todo lo pulsable.** `android:minWidth`/`minHeight` a
   `@dimen/uci_touch_target_min`, no el tamaño del icono: un botón cuyos límites coinciden con su
   drawable de 24 dp falla el escáner de accesibilidad y es genuinamente difícil de acertar.
5. **Filas que reflúyen.** `ChipGroup` con `app:singleLine="false"` en XML, `FlowRow` en Compose.
   A escala de fuente del 200 % una fila de datos no cabe en una línea, y truncar información
   factual porque el usuario necesita texto grande no es aceptable.
6. **`tools:text` y `tools:visibility`** en todo lo que se rellena en código, para que el preview
   sirva de algo.

## Accesibilidad en un layout

Este es el bloque que más se olvida y el que más se nota.

```xml
<!-- Contenedor de texto: UNA parada de TalkBack. La descripción se pone en
     código con mergeChildrenForAccessibility(). -->
<LinearLayout android:id="@+id/uciPropertyInfo">

    <!-- Cada texto individual sale del árbol: su contenido ya está en la
         descripción fusionada del contenedor. -->
    <TextView
        android:id="@+id/uciPropertyTitle"
        android:importantForAccessibility="no" />
</LinearLayout>

<!-- Un icono decorativo. -->
<ImageView android:importantForAccessibility="no" />

<!-- Un botón: la descripción es LA ACCIÓN. El estado va aparte, en código,
     con ViewCompat.setStateDescription(). -->
<MaterialButton
    android:id="@+id/uciPropertyFavouriteButton"
    android:contentDescription="@string/uci_action_favourite_add"
    android:minWidth="@dimen/uci_touch_target_min"
    android:minHeight="@dimen/uci_touch_target_min" />
```

En código:

```kotlin
uciPropertyInfo.mergeChildrenForAccessibility(formatter.accessibilityDescription(property))
uciPropertyInfo.setAccessibilityClickLabel(getString(R.string.uci_detail_title))

favouriteButton.contentDescription = getString(
    if (isFavourite) R.string.uci_action_favourite_remove else R.string.uci_action_favourite_add,
)
ViewCompat.setStateDescription(
    favouriteButton,
    getString(if (isFavourite) R.string.uci_a11y_favourite_state_on else R.string.uci_a11y_favourite_state_off),
)
```

Por qué importa cada pieza: sin la fusión, TalkBack para en nueve nodos por fila y hay que deslizar
nueve veces para pasar un anuncio. Sin `setAccessibilityClickLabel`, el sistema anuncia "pulsa dos
veces para *activar*", que no dice nada. Sin `stateDescription`, el usuario oye lo que va a pasar
pero no lo que ya es verdad.

## Skeleton de carga

Todo lo que se carga tiene su skeleton, y el skeleton **imita la geometría de la fila real** (misma
tarjeta, misma altura de carrusel, mismo número de líneas), para que la transición no haga saltar
la lista. Eso es lo que hace que un skeleton valga la pena frente a un spinner.

```xml
<com.google.android.material.card.MaterialCardView
    style="@style/Widget.Uci.Card"
    android:importantForAccessibility="noHideDescendants">

    <com.ulisescervera.uci.core.ui.skeleton.SkeletonView
        android:layout_width="match_parent"
        android:layout_height="@dimen/uci_carousel_height"
        app:uciSkeletonShape="rectangle" />

    <com.ulisescervera.uci.core.ui.skeleton.SkeletonView
        android:layout_width="220dp"
        android:layout_height="@dimen/uci_skeleton_line_height_large"
        app:uciSkeletonShape="line" />
</com.google.android.material.card.MaterialCardView>
```

`noHideDescendants` en el contenedor: es el **contenedor de la lista** el que lleva
`contentDescription="@string/uci_a11y_loading"` y lo anuncia una vez. Recorrer ocho rectángulos
grises es estrictamente peor que no anunciar nada.

`SkeletonView` respeta el ajuste del sistema "quitar animaciones": con
`ANIMATOR_DURATION_SCALE = 0` se dibuja estático.

## Vistas en Compose

```kotlin
@Composable
fun <Nombre>Screen(
    state: <Feature>UiState,
    onIntent: (<Feature>Intent) -> Unit,
    modifier: Modifier = Modifier,
) { /* … */ }
```

- **Stateless**: estado dentro, intents fuera. Así cada rama es un `@Preview` y un test de una
  línea.
- Colores de `MaterialTheme.colorScheme`, nunca `Color(0xFF…)` inline. El acento de marca está en
  `UciBrand`.
- Fusión de semántica: `Modifier.semantics(mergeDescendants = true) { contentDescription = … }`.
- Nodos decorativos: `Modifier.clearAndSetSemantics { }` o `contentDescription = null`.
- Estado de un toggle: `Modifier.semantics { stateDescription = … }`.
- `items(key = { it.id })` en `LazyColumn`, o al eliminar un elemento se rebinda todo lo de debajo.
- `@Preview` por rama: cargando, vacío, con datos. Un composable que no se puede previsualizar es un
  composable que nadie refactorizará.

## Un `<merge>` reutilizable

Un componente que comparten tres pantallas se hace **vista personalizada**, no `<include>`. El
`<include>` comparte el XML pero deja a cada llamante recablear el listener de página y el contador;
una vista personalizada comparte el comportamiento y da una API de una llamada. Ver
`PropertyCarouselView`, que además documenta por qué recibe sus dependencias por parámetro en lugar
de por Hilt.

## Trampas

- **Coil 3**: `crossfade`, `placeholder`, `error`, `fallback` y `allowHardware` son funciones de
  extensión, no miembros del builder — impórtalas desde `coil3.request` o no compila. Carga
  siempre a través de `ImageView.loadPropertyImage` / `AsyncImage`, nunca con un `load {}` suelto.
- **`ViewPager2` dentro de `RecyclerView`**: `isSaveEnabled = false`, o el framework lanza
  *"Page can only be offset by a positive amount"* al restaurar estado.
- **Rebind completo de una fila**: sin *change payload*, marcar un favorito rebinda la fila y el
  carrusel salta a la foto 1.
- **`MapView`**: `release()` en `onViewRecycled` y `onDestroyView`, o quedan hilos de descarga de
  teselas vivos.
- **Mapa dentro de un pager**: sin multitouch y con una vista transparente encima que solo capture
  clics, o el mapa se come el arrastre horizontal.

## Checklist

- [ ] Sin dp, sp ni colores literales.
- [ ] 48 dp en todo lo pulsable.
- [ ] Contenedores de texto fusionados; decorativos excluidos.
- [ ] Botones con estado: acción en `contentDescription`, estado en `stateDescription`.
- [ ] Skeleton con la geometría de la fila real y silencioso para el lector de pantalla.
- [ ] Probado en claro **y** en oscuro.
- [ ] Probado a escala de fuente del 200 %.
- [ ] Strings en `values/` **y** en `values-en/`.
- [ ] `./gradlew lint` en verde.
