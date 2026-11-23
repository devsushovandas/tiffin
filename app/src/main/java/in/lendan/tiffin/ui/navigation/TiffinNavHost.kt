package `in`.lendan.tiffin.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import `in`.lendan.tiffin.ui.kitchendetail.KitchenDetailScreen
import `in`.lendan.tiffin.ui.kitchenlist.KitchenListScreen
import `in`.lendan.tiffin.ui.paywall.PaywallScreen

@Composable
fun TiffinNavHost(
    showAutoPaywall: Boolean,
    onAutoPaywallShown: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    // Pops only the paywall, and does nothing if it is already gone, so it is safe to call
    // twice (close tap racing a finished payment, or a repeat after recreation).
    val closePaywall: () -> Unit = { navController.popBackStack(Routes.PAYWALL, inclusive = true) }

    NavHost(
        navController = navController,
        startDestination = Routes.LIST,
        modifier = modifier,
    ) {
        composable(Routes.LIST) {
            KitchenListScreen(
                onKitchenClick = { kitchenId ->
                    navController.navigate(Routes.detail(kitchenId)) { launchSingleTop = true }
                },
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument(Routes.ARG_KITCHEN_ID) { type = NavType.StringType }),
        ) {
            KitchenDetailScreen(
                onBack = { navController.navigateUp() },
                onSubscribe = { kitchenId ->
                    navController.navigate(Routes.paywall(Routes.SOURCE_SUBSCRIBE_TAP, kitchenId)) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(
            route = Routes.PAYWALL,
            arguments = listOf(
                navArgument(Routes.ARG_SOURCE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(Routes.ARG_KITCHEN_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            PaywallScreen(onClose = closePaywall)
        }
    }

    // Third-launch paywall. The decision (count == 3, not paid, not shown before) was made and
    // saved atomically in SubscriptionStore; this only performs the navigation once.
    LaunchedEffect(showAutoPaywall) {
        if (showAutoPaywall) {
            navController.navigate(Routes.paywall(Routes.SOURCE_THIRD_LAUNCH, kitchenId = null)) {
                launchSingleTop = true
            }
            onAutoPaywallShown()
        }
    }
}
