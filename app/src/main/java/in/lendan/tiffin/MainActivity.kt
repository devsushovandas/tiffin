package `in`.lendan.tiffin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.lendan.tiffin.ui.MainViewModel
import `in`.lendan.tiffin.ui.navigation.TiffinNavHost
import `in`.lendan.tiffin.ui.theme.TiffinTheme

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A launch is a fresh start: savedInstanceState is null. Rotation and restoration after
        // process death both deliver a non-null bundle, so they are not counted.
        if (savedInstanceState == null) mainViewModel.onFreshLaunch() else mainViewModel.onRecreated()

        enableEdgeToEdge()
        setContent {
            TiffinTheme {
                val showAutoPaywall by mainViewModel.showAutoPaywall.collectAsStateWithLifecycle()
                TiffinNavHost(
                    showAutoPaywall = showAutoPaywall,
                    onAutoPaywallShown = mainViewModel::onAutoPaywallShown,
                )
            }
        }
    }
}
