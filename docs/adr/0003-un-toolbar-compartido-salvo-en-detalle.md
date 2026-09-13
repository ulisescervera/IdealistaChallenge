# ADR 0003 — Un toolbar compartido en la Activity, salvo en el detalle

- **Estado:** aceptada
- **Fecha:** 2026-09-13
- **Contexto del enunciado:** *"la navigation bar no se ve en emulador ni en dispositivo real.
  Incluyela en mainactivity, debe actualizarse el título según el fragment visible. Al navegar al
  detalle debe aparecer el botón de navegar atrás. Cuando se esté en el listado aparecerá arriba a
  la derecha un botón con un icono de mapa."*

## Decisión

`MainActivity` pasa a poseer un único `MaterialToolbar` (`uciToolbar`), sincronizado con el grafo
de navegación vía `NavigationUI` (`setupWithNavController` + `AppBarConfiguration`): el título lo
da el `android:label` de cada destino, la flecha de "arriba" aparece sola en cualquier destino que
no sea top-level.

Se muestra en exactamente dos destinos —listado y favoritos— y se oculta en el resto. El listado
gana un `MenuProvider` propio con el icono de mapa. Favoritos pierde su `TopAppBar` de Compose (el
título ya lo da la Activity) pero conserva el contador de favoritos como texto dentro del
contenido, no en una barra.

**El detalle queda fuera de este toolbar a propósito.** Conserva el suyo, transparente, tal y como
estaba.

## Razonamiento

### Por qué no se unificó también el detalle

El toolbar del detalle no es una barra de título con una flecha: es una pieza del mecanismo de
transición. Flota transparente sobre la galería a pantalla completa precisamente para que la
transición compartida desde la tarjeta del listado no quede recortada por una barra opaca —así lo
dice el propio comentario del layout, y así lo confirma que ese toolbar nunca tuvo `background`
sólido ni apareció en la lista de "trampas conocidas" por accidente: es la pieza que hace que
`MaterialContainerTransform` funcione sin costuras.

Meter el detalle en el toolbar compartido obligaba a elegir entre dos precios, ninguno razonable:

1. Que el toolbar de la Activity supiera ser unas veces opaco y otras transparente según el
   destino, es decir, que la Activity empezara a conocer el aspecto visual concreto que cada
   pantalla necesita —justo la clase de acoplamiento que el propio `MainActivity` dice evitar en su
   comentario de cabecera ("Every piece of business logic lives in a fragment's ViewModel").
2. O renunciar al efecto transparente y aceptar que la galería del detalle deje de dibujarse a
   borde de pantalla, perdiendo parte del motivo de ser de la transición compartida.

Ocultar `uciToolbar` mientras el detalle está en pantalla, y no tocar el suyo, cuesta una entrada
en un `Set` y dos líneas de layout (`ConstraintLayout` colapsa el ancla de `uciNavHostFragment`
sola cuando el toolbar está `GONE`, así que el fragmento recupera la pantalla completa sin código
adicional). El requisito del enunciado —"al navegar al detalle debe aparecer el botón de navegar
atrás"— ya estaba resuelto por ese toolbar propio antes de este cambio; no había nada que arreglar
ahí, solo que no romperlo.

Por el mismo motivo el visor de imágenes (pantalla de Compose, inmersiva, sin cromo por diseño)
tampoco se engancha a este toolbar.

### Por qué el contador de favoritos no viaja al toolbar de la Activity

La primera versión de este cambio sí lo hacía: una interfaz `ToolbarSubtitleHost` que
`FavouritesFragment` implementaba para empujar el "N favoritos" al `subtitle` nativo del
`Toolbar`. Se descartó por dos razones que solo aparecieron al mirar el test existente,
`FavouritesScreenTest`: ese contador ya estaba probado dentro del propio árbol de Compose
(`onNodeWithText("2 favoritos")`), sin Activity ni Fragment de por medio. Moverlo a la Activity
habría exigido reescribir ese test como una prueba instrumentada distinta, y a cambio de nada: un
`Text` normal dentro del contenido consigue el mismo resultado visual sin acoplar
`FavouritesFragment` a `MainActivity`. La opción más simple ganó porque la más "correcta" en
apariencia no compraba nada.

### Por qué el botón de mapa no abre nada todavía

No existe ninguna pantalla de "todos los inmuebles en un mapa" en la app —el único mapa hoy es el
del detalle, con un pin de una única ubicación—. Construirla no estaba en el alcance de esta
petición, y añadir un icono que finge una función que no existe sería peor que decir claramente que
todavía no hace nada. El botón consume el toque (`true`) y no dispatcha ningún intent; el porqué
está comentado en `PropertyListFragment.MapMenuProvider` y en la propia cadena del string
`uci_list_action_map`.

## Consecuencias

**A favor**

- Un único sitio (`MainActivity`) decide título y flecha de retroceso para listado y favoritos, en
  vez de que cada pantalla las gestione a mano.
- `FavouritesScreen` pierde `Scaffold`/`TopAppBar` y la anotación `@OptIn(ExperimentalMaterial3Api)`
  que solo existía por eso.
- El detalle no pierde nada: su transición compartida, la más frágil del proyecto, no se ha tocado.

**En contra, y asumido**

- Dos conceptos de "top-level" que hoy coinciden exactamente
  (`TOP_LEVEL_DESTINATIONS`/`DESTINATIONS_WITH_TOOLBAR`) pero que no tienen por qué seguir
  coincidiendo si algún día se añade una pestaña sin toolbar propio, o una pantalla con toolbar pero
  sin bottom nav. Se mantienen como dos `val` distintos (uno reasignado al otro) precisamente para
  que ese día futuro solo obligue a separarlos, no a descubrir el acoplamiento por sorpresa.
- El botón de mapa es, a día de hoy, un botón que no hace nada. Aceptado explícitamente para esta
  iteración; construir la pantalla de mapa es trabajo futuro, no de este cambio.

## Alternativas descartadas

**Un solo toolbar para las cinco pantallas, incluido el detalle.** Habría exigido que la Activity
supiera cuándo ser transparente, lo que documenta la sección "Por qué no se unificó también el
detalle".

**`ToolbarSubtitleHost` para el contador de favoritos.** Descartada por el motivo del test: no
compraba nada que un `Text` dentro de Compose no diera ya, y sí añadía una dependencia
Fragment→Activity nueva en el proyecto.

**Dejar el botón de mapa sin visibilidad hasta tener la pantalla real.** Habría sido más honesto en
sentido estricto, pero es exactamente lo contrario de lo que pidió el enunciado
("solo el botón, sin comportamiento").
