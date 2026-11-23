package `in`.lendan.tiffin.ui.paywall

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
import `in`.lendan.tiffin.data.SubscriptionPlan
import `in`.lendan.tiffin.data.SubscriptionStore
import `in`.lendan.tiffin.ui.navigation.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface PaywallUiState {
    /** Paid status not read yet: render nothing, so a paid user never sees the paywall. */
    data object Loading : PaywallUiState

    data class Ready(
        val chargeAtMillis: Long,
        val isPaying: Boolean,
        val paymentFailed: Boolean,
    ) : PaywallUiState

    /** The user is paid (already, or just now). The screen closes itself. */
    data object Done : PaywallUiState
}

class PaywallViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val store: SubscriptionStore,
    private val analytics: Analytics,
    private val applicationScope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    /** Nav arguments: how the paywall was opened, and from which kitchen (if any). */
    private val source: String? = savedStateHandle[Routes.ARG_SOURCE]
    private val kitchenId: String? = savedStateHandle[Routes.ARG_KITCHEN_ID]

    /**
     * When the ₹249 charge happens: computed once when the paywall opens. Kept in
     * SavedStateHandle so it stays the same after rotation and process death.
     */
    val chargeAtMillis: Long = savedStateHandle.get<Long>(KEY_CHARGE_AT)
        ?: (now() + SubscriptionPlan.TRIAL_DURATION_MILLIS).also {
            savedStateHandle[KEY_CHARGE_AT] = it
        }

    private val isPaying = MutableStateFlow(false)
    private val paymentFailed = MutableStateFlow(false)
    private var dismissTracked = false

    val uiState: StateFlow<PaywallUiState> =
        combine(store.paid, isPaying, paymentFailed) { paid, paying, failed ->
            if (paid) {
                PaywallUiState.Done
            } else {
                PaywallUiState.Ready(chargeAtMillis, isPaying = paying, paymentFailed = failed)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PaywallUiState.Loading,
        )

    /**
     * The paywall content became visible. The screen may call this again after rotation or
     * process restore; the flag lives in SavedStateHandle, so it is tracked once per paywall.
     */
    fun onShown() {
        if (savedStateHandle.get<Boolean>(KEY_SHOWN_TRACKED) == true) return
        savedStateHandle[KEY_SHOWN_TRACKED] = true
        viewModelScope.launch {
            analytics.track(
                "paywall_shown",
                mapOf("source" to source, "launchCount" to store.launchCount.first()),
            )
        }
    }

    /** Close button or system back, without paying. Guarded so a racing double close counts once. */
    fun onDismissed() {
        if (dismissTracked) return
        dismissTracked = true
        analytics.track("paywall_dismissed", mapOf("source" to source))
    }

    /**
     * Fake payment. Ignores repeat taps while a payment is in progress. The write runs in the
     * application scope so leaving the screen mid-write cannot cancel it. Success shows up as
     * paid = true from the store, which moves the state to [PaywallUiState.Done].
     */
    fun pay() {
        if (!isPaying.compareAndSet(expect = false, update = true)) return
        paymentFailed.value = false
        applicationScope.launch {
            try {
                store.markPaid(purchaseTimeMillis = now(), chargeTimeMillis = chargeAtMillis)
            } catch (e: IOException) {
                paymentFailed.value = true
                isPaying.value = false
                return@launch
            }
            analytics.track(
                "purchase_completed",
                buildMap {
                    put("source", source)
                    if (kitchenId != null) put("kitchenId", kitchenId)
                },
            )
        }
    }

    companion object {
        private const val KEY_CHARGE_AT = "chargeAtMillis"
        private const val KEY_SHOWN_TRACKED = "paywallShownTracked"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TiffinApplication).container
                PaywallViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    store = container.subscriptionStore,
                    analytics = container.analytics,
                    applicationScope = container.applicationScope,
                )
            }
        }
    }
}
