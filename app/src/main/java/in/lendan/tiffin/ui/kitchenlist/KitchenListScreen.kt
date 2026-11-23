package `in`.lendan.tiffin.ui.kitchenlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.lendan.tiffin.BuildConfig
import `in`.lendan.tiffin.R
import `in`.lendan.tiffin.data.SimulationMode
import `in`.lendan.tiffin.data.model.Kitchen
import `in`.lendan.tiffin.ui.components.DietIndicator
import `in`.lendan.tiffin.ui.components.LoadingState
import `in`.lendan.tiffin.ui.components.MessageState
import `in`.lendan.tiffin.ui.components.Rating
import `in`.lendan.tiffin.ui.theme.TiffinTheme

@Composable
fun KitchenListScreen(
    onKitchenClick: (kitchenId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KitchenListViewModel = viewModel(factory = KitchenListViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val simulationMode by viewModel.simulationMode.collectAsStateWithLifecycle()
    KitchenListContent(
        uiState = uiState,
        onRetry = viewModel::retry,
        onKitchenClick = onKitchenClick,
        showDebugMenu = BuildConfig.DEBUG,
        simulationMode = simulationMode,
        onSimulationModeSelected = viewModel::setSimulationMode,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KitchenListContent(
    uiState: KitchenListUiState,
    onRetry: () -> Unit,
    onKitchenClick: (String) -> Unit,
    showDebugMenu: Boolean,
    simulationMode: SimulationMode,
    onSimulationModeSelected: (SimulationMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.kitchen_list_title)) },
                actions = {
                    if (showDebugMenu) {
                        SimulationMenu(
                            current = simulationMode,
                            onSelected = onSimulationModeSelected,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when (uiState) {
            KitchenListUiState.Loading -> LoadingState(
                description = stringResource(R.string.kitchen_list_loading),
                modifier = Modifier.padding(innerPadding),
            )
            KitchenListUiState.Empty -> MessageState(
                title = stringResource(R.string.kitchen_list_empty_title),
                body = stringResource(R.string.kitchen_list_empty_body),
                modifier = Modifier.padding(innerPadding),
            )
            is KitchenListUiState.Error -> MessageState(
                title = stringResource(R.string.error_title),
                body = uiState.message,
                actionLabel = stringResource(R.string.retry),
                onAction = onRetry,
                modifier = Modifier.padding(innerPadding),
            )
            is KitchenListUiState.Success -> KitchenList(
                kitchens = uiState.kitchens,
                onKitchenClick = onKitchenClick,
                contentPadding = innerPadding,
            )
        }
    }
}

@Composable
private fun SimulationMenu(
    current: SimulationMode,
    onSelected: (SimulationMode) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.debug_menu),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SimulationMode.entries.forEach { mode ->
                val label = when (mode) {
                    SimulationMode.NORMAL -> R.string.simulate_normal
                    SimulationMode.EMPTY -> R.string.simulate_empty
                    SimulationMode.ERROR -> R.string.simulate_error
                }
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        expanded = false
                        onSelected(mode)
                    },
                    trailingIcon = {
                        if (mode == current) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun KitchenList(
    kitchens: List<Kitchen>,
    onKitchenClick: (String) -> Unit,
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
        items(items = kitchens, key = { it.id }) { kitchen ->
            KitchenRow(kitchen = kitchen, onClick = { onKitchenClick(kitchen.id) })
        }
    }
}

@Composable
private fun KitchenRow(
    kitchen: Kitchen,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = kitchen.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = kitchen.cuisine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DietIndicator(isVeg = kitchen.isVeg)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.price_per_tiffin, kitchen.pricePerTiffin),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Rating(rating = kitchen.rating)
        }
    }
}

private val previewKitchens = listOf(
    Kitchen("k01", "Annapurna Rasoi", "North Indian", 90, true, 4.5, emptyList()),
    Kitchen("k08", "Nawabi Handi", "Hyderabadi", 180, false, 4.8, emptyList()),
)

@Preview(showBackground = true)
@Composable
private fun KitchenListSuccessPreview() {
    TiffinTheme(dynamicColor = false) {
        KitchenListContent(
            uiState = KitchenListUiState.Success(previewKitchens),
            onRetry = {},
            onKitchenClick = {},
            showDebugMenu = true,
            simulationMode = SimulationMode.NORMAL,
            onSimulationModeSelected = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun KitchenListErrorPreview() {
    TiffinTheme(dynamicColor = false) {
        KitchenListContent(
            uiState = KitchenListUiState.Error("We couldn't load kitchens right now."),
            onRetry = {},
            onKitchenClick = {},
            showDebugMenu = false,
            simulationMode = SimulationMode.NORMAL,
            onSimulationModeSelected = {},
        )
    }
}
