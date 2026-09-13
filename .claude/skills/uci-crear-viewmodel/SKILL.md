---
name: uci-crear-viewmodel
description: Crea un ViewModel de UCI (Ulises-Cervera-Idealista) con su contrato MVI completo -- intents, estado y efectos -- y su test. Úsala al añadir una pantalla nueva o al añadir comportamiento a una existente.
---

# Crear un ViewModel y su contrato MVI

## La forma

Cada feature tiene dos ficheros:

```
app/src/main/kotlin/com/ulisescervera/uci/feature/<feature>/<Feature>Contract.kt
app/src/main/kotlin/com/ulisescervera/uci/feature/<feature>/<Feature>ViewModel.kt
app/src/test/kotlin/com/ulisescervera/uci/feature/<feature>/<Feature>ViewModelTest.kt
```

El contrato va todo junto en un fichero porque se lee junto: los tres tipos son las tres caras de
la misma pantalla.

## 1. El contrato

```kotlin
sealed interface <Feature>Intent : UiIntent {
    data object ScreenStarted : <Feature>Intent
    data class ItemClicked(val id: String, val imageIndex: Int) : <Feature>Intent
    data object RefreshRequested : <Feature>Intent
    data object RetryRequested : <Feature>Intent
}

data class <Feature>UiState(
    val isInitialLoading: Boolean = true,
    val items: List<Property> = emptyList(),
    val error: UciError? = null,
) : UiState {

    /**
     * Derivado, no almacenado: así "cargando y con error a la vez" no es
     * representable, y las reglas de precedencia viven aquí -- testeables sin
     * vista -- en lugar de reimplementadas en el fragment.
     */
    val surface: Surface
        get() = when {
            isInitialLoading -> Surface.Skeleton
            items.isNotEmpty() -> Surface.Content
            error != null -> Surface.Error
            else -> Surface.Empty
        }

    enum class Surface { Skeleton, Content, Empty, Error }
}

sealed interface <Feature>Effect : UiEffect {
    data class OpenDetail(val id: String) : <Feature>Effect
    data class ShowError(val error: UciError) : <Feature>Effect
}
```

Decisiones que no se negocian:

- **Un solo objeto de estado.** Varios `LiveData` permiten combinaciones imposibles.
- **`surface` derivado.** Fíjate en el orden: contenido cacheado **gana** a un error. Fallar un
  refresco con cuatro inmuebles ya en pantalla es un snackbar, no una pantalla de error — es el
  beneficio de tener un repositorio *offline-first* y hay que cobrarlo.
- **Los efectos no van en el estado.** Un "mostrar snackbar" como campo de estado se vuelve a
  emitir en cada rotación y el usuario ve el mismo mensaje otra vez.
- **Los intents transportan tipos de presentación** (índice de imagen, posición). Por eso viven en
  `:app`; ver `docs/adr/0001`.

## 2. El ViewModel

```kotlin
@HiltViewModel
class <Feature>ViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,          // solo si hay argumentos de navegación
    private val observeItems: ObserveXUseCase,
    private val refreshItems: RefreshXUseCase,
) : UciViewModel<<Feature>UiState, <Feature>Intent, <Feature>Effect>(<Feature>UiState()) {

    private var hasRequestedInitialRefresh = false

    init {
        viewModelScope.launch {
            observeItems().collect { items ->
                // La primera emisión de Room termina el skeleton, aunque venga
                // vacía: una consulta terminada sin filas es "vacío", no
                // "cargando".
                reduce { copy(items = items, isInitialLoading = false) }
            }
        }
    }

    override fun onIntent(intent: <Feature>Intent) = when (intent) {
        <Feature>Intent.ScreenStarted -> refreshOnce()
        <Feature>Intent.RefreshRequested -> refresh(showSpinner = true)
        <Feature>Intent.RetryRequested -> retry()
        is <Feature>Intent.ItemClicked -> emitEffect(<Feature>Effect.OpenDetail(intent.id))
    }

    /**
     * `ScreenStarted` llega en cada `onViewCreated`: rotación, volver del
     * detalle, cambiar de pestaña. Refrescar siempre sería una llamada de red
     * por navegación.
     */
    private fun refreshOnce() {
        if (hasRequestedInitialRefresh) return
        hasRequestedInitialRefresh = true
        refresh(showSpinner = false)
    }

    /** El botón de reintentar limpia el error antes de volver a pedir. */
    private fun retry() {
        reduce { copy(error = null, isInitialLoading = items.isEmpty()) }
        refresh(showSpinner = false)
    }

    /**
     * @param showSpinner true en un *pull-to-refresh* (el usuario lo ha pedido
     *   y espera respuesta visible), false en la carga automática: el skeleton
     *   ya está diciendo "cargando" y dos indicadores a la vez parecen un bug.
     */
    private fun refresh(showSpinner: Boolean) {
        viewModelScope.launch {
            if (showSpinner) reduce { copy(isRefreshing = true) }
            val result = refreshItems()
            reduce {
                copy(
                    isRefreshing = false,
                    // El error solo vive en el estado mientras no haya nada que
                    // enseñar; con contenido cacheado es un efecto pasajero.
                    error = (result as? UciResult.Failure)?.error?.takeIf { items.isEmpty() },
                    isInitialLoading = false,
                )
            }
            result.onFailure { error ->
                if (currentState.items.isNotEmpty()) {
                    emitEffect(<Feature>Effect.ShowError(error))
                }
            }
        }
    }
}
```

**Tres intents de recarga, no uno.** Son tres gestos distintos con tres comportamientos
distintos: el automático no enseña spinner, el *pull-to-refresh* sí, y el reintento además limpia
el error. Fundirlos en uno obliga a la vista a saber en cuál de los tres está.

Reglas:

- **`onIntent` es un `when` exhaustivo sin `else`.** Añadir un intent sin gestionarlo no compila.
- **Nunca actualices el estado de forma optimista con lo que acabas de escribir.** La escritura va a
  Room y el `Flow` de Room devuelve el estado nuevo en uno o dos frames. Adivinar arriesga que la
  UI discrepe de la base de datos.
- **Nada de `Context`, `View`, `@DrawableRes` ni `getString`.** Si necesitas texto, el estado lleva
  los datos y el formateo lo hace `PropertyFormatter` en la vista (o un `RowsFactory` inyectado).
- **Los argumentos de navegación se leen de `SavedStateHandle`**, con las claves como constantes en
  el `companion object` y un comentario diciendo que deben coincidir con el `<argument>` del grafo.
- **Guarda las operaciones de un solo disparo con el estado, no con un booleano suelto** cuando el
  estado ya lo expresa: `if (currentState.related != RelatedState.Idle) return`.

## 3. El test

Con casos de uso **reales** sobre fakes de repositorio. Un mock del caso de uso dejaría pasar un
ViewModel que llama al equivocado.

```kotlin
class <Feature>ViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakePropertyRepository()

    private fun viewModel() = <Feature>ViewModel(
        observeItems = ObserveXUseCase(repository),
        refreshItems = RefreshXUseCase(repository),
    )

    @Test
    fun `an empty first emission ends the skeleton and shows the empty state`() = runTest {
        val model = viewModel()

        repository.emitVisible(emptyList())

        assertThat(model.state.value.surface).isEqualTo(<Feature>UiState.Surface.Empty)
    }

    @Test
    fun `cached content wins over a refresh failure`() = runTest {
        val model = viewModel()
        repository.emitVisible(listOf(AppFixtures.property("1")))
        repository.refreshListResult = UciResult.Failure(UciError.Timeout)

        model.effects.test {
            model.dispatch(<Feature>Intent.RefreshRequested)

            assertThat(awaitItem()).isEqualTo(<Feature>Effect.ShowError(UciError.Timeout))
            assertThat(model.state.value.surface).isEqualTo(<Feature>UiState.Surface.Content)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

Cubre siempre:

- cada valor de `surface`, incluida la precedencia entre ellos;
- que la carga automática ocurre **una sola vez** por ViewModel;
- cada intent → el efecto o la escritura que produce;
- el fallo de cada escritura (efecto de error y ningún cambio de estado);
- si hay `SavedStateHandle`, que los argumentos se leen.

`MainDispatcherRule` es obligatorio: `viewModelScope` usa `Dispatchers.Main.immediate`, que no
existe fuera de Android.
