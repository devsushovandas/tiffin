package `in`.lendan.tiffin.ui.paywall

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.lendan.tiffin.R
import `in`.lendan.tiffin.ui.format.DateFormatting
import `in`.lendan.tiffin.ui.theme.TiffinTheme

/**
 * [onClose] must be safe to call more than once (it can fire from the close button, from a
 * finished payment, and after recreation). Closing because the user is paid is not a dismissal,
 * so only the close button and system back report paywall_dismissed.
 */
@Composable
fun PaywallScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaywallViewModel = viewModel(factory = PaywallViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState is PaywallUiState.Done) {
        LaunchedEffect(Unit) { onClose() }
    }

    when (val state = uiState) {
        // Nothing is drawn until we know the user is not paid, so a paid user never sees a flash.
        PaywallUiState.Loading, PaywallUiState.Done -> Surface(modifier.fillMaxSize()) {}
        is PaywallUiState.Ready -> {
            // Re-runs after rotation/process restore; the ViewModel tracks it only once.
            LaunchedEffect(Unit) { viewModel.onShown() }

            val dismiss = {
                viewModel.onDismissed()
                onClose()
            }
            // System back dismisses like the close button. While a payment is being saved, back
            // is swallowed so the outcome is a purchase, never a half-dismissal.
            BackHandler { if (!state.isPaying) dismiss() }

            PaywallContent(
                state = state,
                onPay = viewModel::pay,
                onClose = dismiss,
                modifier = modifier,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaywallContent(
    state: PaywallUiState.Ready,
    onPay: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chargeAt = remember(state.chargeAtMillis) { DateFormatting.dateTime(state.chargeAtMillis) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = !state.isPaying) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 3.dp) {
                Button(
                    onClick = onPay,
                    enabled = !state.isPaying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (state.isPaying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.paywall_pay_button))
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.paywall_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.paywall_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.paywall_charge_now),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.paywall_charge_later, chargeAt),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (state.paymentFailed) {
                Text(
                    text = stringResource(R.string.paywall_payment_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PaywallPreview() {
    TiffinTheme(dynamicColor = false) {
        PaywallContent(
            state = PaywallUiState.Ready(
                chargeAtMillis = 1_795_515_000_000L,
                isPaying = false,
                paymentFailed = false,
            ),
            onPay = {},
            onClose = {},
        )
    }
}
