package `in`.lendan.tiffin.ui.kitchendetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.lendan.tiffin.R
import `in`.lendan.tiffin.data.SubscriptionStatus
import `in`.lendan.tiffin.data.model.Kitchen
import `in`.lendan.tiffin.data.model.MenuDay
import `in`.lendan.tiffin.ui.components.DietIndicator
import `in`.lendan.tiffin.ui.components.LoadingState
import `in`.lendan.tiffin.ui.components.MessageState
import `in`.lendan.tiffin.ui.components.Rating
import `in`.lendan.tiffin.ui.format.DateFormatting
import `in`.lendan.tiffin.ui.theme.TiffinTheme

@Composable
fun KitchenDetailScreen(
    onBack: () -> Unit,
    onSubscribe: (kitchenId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KitchenDetailViewModel = viewModel(factory = KitchenDetailViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val subscriptionStatus by viewModel.subscriptionStatus.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    KitchenDetailContent(
        uiState = uiState,
        subscriptionStatus = subscriptionStatus,
        onBack = onBack,
        onRetry = viewModel::retry,
        onSubscribe = {
            // Only count a tap while this screen is resumed. A second tap during the transition
            // to the paywall (screen no longer resumed) is ignored, so it is neither tracked nor
            // navigated twice.
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                viewModel.onSubscribeTapped()
                onSubscribe(viewModel.kitchenId)
            }
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KitchenDetailContent(
    uiState: KitchenDetailUiState,
    subscriptionStatus: SubscriptionStatus,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSubscribe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    val title = (uiState as? KitchenDetailUiState.Success)?.kitchen?.name
                        ?: stringResource(R.string.kitchen_detail_title)
                    Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (uiState is KitchenDetailUiState.Success) {
                SubscriptionBar(status = subscriptionStatus, onSubscribe = onSubscribe)
            }
        },
    ) { innerPadding ->
        when (uiState) {
            KitchenDetailUiState.Loading -> LoadingState(
                description = stringResource(R.string.kitchen_detail_loading),
                modifier = Modifier.padding(innerPadding),
            )
            is KitchenDetailUiState.Error -> MessageState(
                title = stringResource(R.string.error_title),
                body = uiState.message,
                actionLabel = stringResource(R.string.retry),
                onAction = onRetry,
                modifier = Modifier.padding(innerPadding),
            )
            is KitchenDetailUiState.Success -> KitchenDetailBody(
                kitchen = uiState.kitchen,
                contentPadding = innerPadding,
            )
        }
    }
}

/** Subscribe button while not paid; a status line once paid; nothing until the status is known. */
@Composable
private fun SubscriptionBar(status: SubscriptionStatus, onSubscribe: () -> Unit) {
    if (status == SubscriptionStatus.Unknown) return
    Surface(tonalElevation = 3.dp, shadowElevation = 3.dp) {
        val barModifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
        when (status) {
            SubscriptionStatus.Unknown -> Unit
            SubscriptionStatus.NotSubscribed -> Button(onClick = onSubscribe, modifier = barModifier) {
                Text(stringResource(R.string.subscribe))
            }
            is SubscriptionStatus.Trial -> {
                val chargeAt = remember(status.chargeAtMillis) {
                    DateFormatting.dateTime(status.chargeAtMillis)
                }
                StatusLine(stringResource(R.string.status_trial_active, chargeAt), barModifier)
            }
            is SubscriptionStatus.Active -> {
                val since = remember(status.sinceMillis) { DateFormatting.date(status.sinceMillis) }
                StatusLine(stringResource(R.string.status_plan_active, since), barModifier)
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun KitchenDetailBody(
    kitchen: Kitchen,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { KitchenHeader(kitchen) }
        item(key = "menu-title") {
            Text(
                text = stringResource(R.string.weekly_menu),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .semantics { heading() },
            )
        }
        items(items = kitchen.weeklyMenu, key = { "day-${it.day}" }) { menuDay ->
            MenuDayCard(menuDay)
        }
    }
}

@Composable
private fun KitchenHeader(kitchen: Kitchen, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = kitchen.name,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = kitchen.cuisine,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Rating(rating = kitchen.rating)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DietIndicator(isVeg = kitchen.isVeg)
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.price_per_tiffin, kitchen.pricePerTiffin),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
    }
}

@Composable
private fun MenuDayCard(menuDay: MenuDay, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = menuDay.day,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(4.dp))
            menuDay.dishes.forEach { dish ->
                Text(
                    text = "• $dish",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun KitchenDetailPreview() {
    TiffinTheme(dynamicColor = false) {
        KitchenDetailContent(
            uiState = KitchenDetailUiState.Success(
                Kitchen(
                    id = "k01",
                    name = "Annapurna Rasoi",
                    cuisine = "North Indian",
                    pricePerTiffin = 90,
                    isVeg = true,
                    rating = 4.5,
                    weeklyMenu = listOf(
                        MenuDay("Monday", listOf("Dal Tadka", "Jeera Rice", "Aloo Gobi")),
                        MenuDay("Tuesday", listOf("Rajma", "Steamed Rice", "Bhindi Fry")),
                    ),
                ),
            ),
            subscriptionStatus = SubscriptionStatus.NotSubscribed,
            onBack = {},
            onRetry = {},
            onSubscribe = {},
        )
    }
}
