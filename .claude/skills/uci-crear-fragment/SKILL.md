---
name: uci-crear-fragment
description: Crea un fragment de UCI (Ulises-Cervera-Idealista) -- con vistas XML y ViewBinding, o con Compose -- y lo conecta al grafo de navegación. Úsala al añadir una pantalla.
---

# Crear un fragment

## Primera decisión: XML o Compose

En UCI conviven los dos y la regla es explícita:

| Pantalla | Tecnología | Motivo |
|---|---|---|
| Listado, detalle, hoja de borrados | XML + ViewBinding | Lo pide el enunciado, y el detalle necesita un `RecyclerView` vertical multi-tipo |
| Visor de imágenes, favoritos | Compose | Lo pide el enunciado |

Para una pantalla nueva: si comparte componentes con el listado o el detalle (carrusel XML, filas
de datos), XML. Si es autónoma, Compose.

En ambos casos el fragment es **tonto**: traduce toques en intents, pinta un estado y ejecuta
efectos. Nada más. Si te ves escribiendo `if (list.isEmpty())` en un fragment, esa decisión
pertenece a `UiState.surface`.

## Variante A — fragment XML

```kotlin
@AndroidEntryPoint
class <Feature>Fragment : ViewBindingFragment<UciFragment<Feature>Binding>(
    UciFragment<Feature>Binding::inflate,
) {

    private val viewModel: <Feature>ViewModel by viewModels()

    @Inject lateinit var propertyFormatter: PropertyFormatter
    @Inject lateinit var errorFormatter: UciErrorFormatter

    private var listAdapter: <Feature>Adapter? = null

    override fun onBindingCreated(binding: UciFragment<Feature>Binding, savedInstanceState: Bundle?) {
        setUpList()
        observeState()
        observeEffects()
        viewModel.dispatch(<Feature>Intent.ScreenStarted)
    }

    private fun observeState() {
        viewModel.state.collectWhileStarted(viewLifecycleOwner) { render(it) }
    }

    private fun render(state: <Feature>UiState) = with(binding) {
        listAdapter?.submitList(state.items)
        // Un único `when` sobre `surface`: exactamente una superficie visible.
        uciSkeleton.isVisible(state.surface == Surface.Skeleton)
        uciList.isVisible(state.surface == Surface.Content)
        uciPlaceholder.isVisible(state.surface in setOf(Surface.Empty, Surface.Error))
    }

    private fun observeEffects() {
        viewModel.effects.collectWhileStarted(viewLifecycleOwner) { effect ->
            when (effect) {
                is <Feature>Effect.OpenDetail -> findNavController().navigate(/* … */)
                is <Feature>Effect.ShowError -> Snackbar
                    .make(binding.root, errorFormatter.message(effect.error), Snackbar.LENGTH_LONG)
                    .show()
            }
        }
    }

    override fun onBindingDestroyed(binding: UciFragment<Feature>Binding) {
        // Rompe la cadena adapter → RecyclerView → vista destruida.
        binding.uciList.adapter = null
        listAdapter = null
    }
}
```

Reglas:

- **Hereda de `ViewBindingFragment`.** Un `Fragment` sobrevive a su vista; guardar el binding en un
  campo normal es la fuga más común de Android. La clase base lo anula en `onDestroyView`.
- **`collectWhileStarted`, no `launchWhenStarted`.** El segundo solo suspende al colector, así que
  un `Flow` que emite en background acumula trabajo y lo suelta de golpe al volver.
- **`viewLifecycleOwner`, nunca `this`.**
- Los formatters se inyectan por campo y se pasan al adapter por constructor, para que el adapter
  sea testeable sin DI.
- Si el click necesita una `View` (transición compartida), **navega directamente** en lugar de pasar
  por un efecto: cuando el efecto se recoge, la vista puede estar ya reciclada y una transición
  compartida con una vista desanclada no hace nada silenciosamente.

## Variante B — fragment Compose

```kotlin
@AndroidEntryPoint
class <Feature>Fragment : ComposeFragment() {

    private val viewModel: <Feature>ViewModel by viewModels()

    @Inject lateinit var errorFormatter: UciErrorFormatter

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsStateWithLifecycle()
        <Feature>Screen(state = state, onIntent = viewModel::dispatch)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.effects.collectWhileStarted(viewLifecycleOwner) { effect -> /* … */ }
    }
}
```

`ComposeFragment` ya aplica `UciTheme`, provee `LocalPropertyFormatter` y fija
`DisposeOnViewTreeLifecycleDestroyed` — la estrategia por defecto destruye la composición al
desanclar la vista, que es lo que pasa en cada navegación, perdiendo todo el `remember` incluida la
posición de scroll.

El composable de pantalla es **stateless**: recibe estado, emite intents. Así cada rama es un
`@Preview` y un test de una línea.

## Registro en el grafo

```xml
<fragment
    android:id="@+id/uci_destination_<pantalla>"
    android:name="com.ulisescervera.uci.feature.<feature>.<Feature>Fragment"
    android:label="@string/uci_<pantalla>_title"
    tools:layout="@layout/uci_fragment_<pantalla>">

    <argument android:name="propertyId" app:argType="string" />

    <action
        android:id="@+id/uci_action_<origen>_to_<destino>"
        app:destination="@id/uci_destination_<destino>" />

    <deepLink app:uri="uci://<recurso>/{propertyId}" />
</fragment>
```

- Los ids de los destinos de pestaña **deben coincidir** con los ids de
  `menu/uci_bottom_navigation.xml`: así `setupWithNavController` los empareja y da back stack por
  pestaña.
- Los deeplinks se declaran **solo aquí**; `<nav-graph>` en el manifest los expande a
  `intent-filter`s en tiempo de build. Declararlos dos veces es cómo el manifest y el grafo se
  desincronizan.
- Una hoja inferior es un `<dialog>`, no un `<fragment>`: Navigation solo mantiene visible el
  destino anterior para destinos de tipo dialog.
- Si la pantalla no es de primer nivel, añade su id a `MainActivity.TOP_LEVEL_DESTINATIONS`… o
  precisamente **no** lo añadas, para que la tab bar se oculte.

## Checklist antes de terminar

- [ ] `@AndroidEntryPoint` puesto.
- [ ] El adapter se anula en `onDestroyView`.
- [ ] `render` tiene exactamente un `when` sobre `surface`.
- [ ] Ninguna decisión de negocio en el fragment.
- [ ] Los efectos se consumen con `collectWhileStarted`.
- [ ] Un `MapView` o un carrusel se liberan (`release()` / `mapConfigurator.release`).
- [ ] Checklist de accesibilidad de `CLAUDE.md` cubierta.
- [ ] Test de ViewModel escrito; test de UI si la pantalla tiene lógica de renderizado propia.
