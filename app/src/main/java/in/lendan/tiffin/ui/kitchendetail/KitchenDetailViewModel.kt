package `in`.lendan.tiffin.ui.kitchendetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import `in`.lendan.tiffin.TiffinApplication
import `in`.lendan.tiffin.analytics.Analytics
import `in`.lendan.tiffin.data.KitchenRepository
import `in`.lendan.tiffin.data.SubscriptionStatus
import `in`.lendan.tiffin.data.SubscriptionStore
import `in`.lendan.tiffin.data.status
import `in`.lendan.tiffin.data.model.Kitchen
import `in`.lendan.tiffin.ui.navigation.Routes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface KitchenDetailUiState {
    data object Loading : KitchenDetailUiState
    data class Error(val message: String) : KitchenDetailUiState
    data class Success(val kitchen: Kitchen) : KitchenDetailUiState
}

/**
 * The kitchen id comes from the nav arguments in [SavedStateHandle], which Navigation restores
 * after process death, so the screen reloads the same kitchen when the app is recreated.
 */
class KitchenDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: KitchenRepository,
    subscriptionStore: SubscriptionStore,
    private val analytics: Analytics,
) : ViewModel() {

    val kitchenId: String = checkNotNull(savedStateHandle[Routes.ARG_KITCHEN_ID]) {
        "KitchenDetailViewModel requires a '${Routes.ARG_KITCHEN_ID}' argument"
    }

    private val _uiState = MutableStateFlow<KitchenDetailUiState>(KitchenDetailUiState.Loading)
    val uiState: StateFlow<KitchenDetailUiState> = _uiState.asStateFlow()

    /** Starts as Unknown so neither the Subscribe button nor a status flashes before DataStore is read. */
    val subscriptionStatus: StateFlow<SubscriptionStatus> = subscriptionStore.status().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SubscriptionStatus.Unknown,
    )

    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() = load()

    /** Called from the click handler only, so it fires once per real tap, never on recomposition. */
    fun onSubscribeTapped() {
        analytics.track("subscribe_tapped", mapOf("kitchenId" to kitchenId))
    }

    private fun load() {
        loadJob?.cancel()
        _uiState.value = KitchenDetailUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = repository.getKitchen(kitchenId).fold(
                onSuccess = { KitchenDetailUiState.Success(it) },
                onFailure = { KitchenDetailUiState.Error(LOAD_ERROR_MESSAGE) },
            )
        }
    }

    companion object {
        private const val LOAD_ERROR_MESSAGE = "We couldn't load this kitchen right now."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TiffinApplication).container
                KitchenDetailViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    repository = container.kitchenRepository,
                    subscriptionStore = container.subscriptionStore,
                    analytics = container.analytics,
                )
            }
        }
    }
}
