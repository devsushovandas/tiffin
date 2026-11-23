package `in`.lendan.tiffin

import android.content.Context
import `in`.lendan.tiffin.analytics.Analytics
import `in`.lendan.tiffin.analytics.LogcatAnalytics
import `in`.lendan.tiffin.data.KitchenRepository
import `in`.lendan.tiffin.data.SubscriptionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container. One instance lives in [TiffinApplication]. */
class AppContainer(context: Context) {
    /**
     * Process-wide scope for writes that must not be cancelled when a screen or Activity goes
     * away (for example the launch-count write if the user rotates during startup).
     */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val kitchenRepository: KitchenRepository = KitchenRepository(context.applicationContext)
    val subscriptionStore: SubscriptionStore = SubscriptionStore(context.applicationContext)
    val analytics: Analytics = LogcatAnalytics()
}
