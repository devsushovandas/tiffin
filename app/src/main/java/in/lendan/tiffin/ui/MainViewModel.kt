package `in`.lendan.tiffin.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import `in`.lendan.tiffin.BuildConfig
import `in`.lendan.tiffin.TiffinApplication
import `in`.lendan.tiffin.data.SubscriptionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Activity-scoped. Records launches and asks the UI to show the automatic paywall on the third
 * launch. Survives rotation, so a pending request is not lost or repeated.
 */
class MainViewModel(
    private val store: SubscriptionStore,
    private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val _showAutoPaywall = MutableStateFlow(false)
    val showAutoPaywall: StateFlow<Boolean> = _showAutoPaywall.asStateFlow()

    /** Call only for a fresh start (savedInstanceState == null). */
    fun onFreshLaunch() {
        // Application scope, so a rotation or quick exit during startup can't cancel the write.
        applicationScope.launch {
            try {
                val record = store.recordFreshLaunch()
                if (BuildConfig.DEBUG) {
                    Log.d(
                        LOG_TAG,
                        "Fresh launch: launchCount=${record.launchCount}, paid=${record.paid}, " +
                            "autoPaywall=${record.showAutoPaywall}",
                    )
                }
                if (record.showAutoPaywall) _showAutoPaywall.value = true
            } catch (e: IOException) {
                Log.w(LOG_TAG, "Could not record launch", e)
            }
        }
    }

    /** Rotation or restoration after process death: not a launch. Debug log only. */
    fun onRecreated() {
        if (!BuildConfig.DEBUG) return
        applicationScope.launch {
            Log.d(
                LOG_TAG,
                "Recreated (rotation or process restore), not counted: " +
                    "launchCount=${store.launchCount.first()}, paid=${store.paid.first()}",
            )
        }
    }

    fun onAutoPaywallShown() {
        _showAutoPaywall.value = false
    }

    companion object {
        private const val LOG_TAG = "Tiffin"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TiffinApplication).container
                MainViewModel(container.subscriptionStore, container.applicationScope)
            }
        }
    }
}
