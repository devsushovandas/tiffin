package `in`.lendan.tiffin.ui.kitchenlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import `in`.lendan.tiffin.TiffinApplication
import `in`.lendan.tiffin.data.KitchenRepository
import `in`.lendan.tiffin.data.SimulationMode
import `in`.lendan.tiffin.data.model.Kitchen
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface KitchenListUiState {
    data object Loading : KitchenListUiState
    data object Empty : KitchenListUiState
    data class Error(val message: String) : KitchenListUiState
    data class Success(val kitchens: List<Kitchen>) : KitchenListUiState
}

class KitchenListViewModel(
    private val repository: KitchenRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<KitchenListUiState>(KitchenListUiState.Loading)
    val uiState: StateFlow<KitchenListUiState> = _uiState.asStateFlow()

    private val _simulationMode = MutableStateFlow(repository.simulationMode)

    /** Current debug simulation mode, for the debug-only menu. Always NORMAL in release. */
    val simulationMode: StateFlow<SimulationMode> = _simulationMode.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() = load()

    fun setSimulationMode(mode: SimulationMode) {
        repository.simulationMode = mode
        _simulationMode.value = repository.simulationMode
        load()
    }

    private fun load() {
        loadJob?.cancel()
        _uiState.value = KitchenListUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = repository.getKitchens().fold(
                onSuccess = { kitchens ->
                    if (kitchens.isEmpty()) KitchenListUiState.Empty
                    else KitchenListUiState.Success(kitchens)
                },
                onFailure = { KitchenListUiState.Error(LOAD_ERROR_MESSAGE) },
            )
        }
    }

    companion object {
        private const val LOAD_ERROR_MESSAGE = "We couldn't load kitchens right now."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TiffinApplication
                KitchenListViewModel(app.container.kitchenRepository)
            }
        }
    }
}
