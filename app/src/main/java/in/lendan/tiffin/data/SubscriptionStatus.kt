package `in`.lendan.tiffin.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest

object SubscriptionPlan {
    const val TRIAL_DURATION_MILLIS: Long = 24L * 60 * 60 * 1000
}

sealed interface SubscriptionStatus {
    /** DataStore has not been read yet. Show neither the Subscribe button nor a status. */
    data object Unknown : SubscriptionStatus
    data object NotSubscribed : SubscriptionStatus
    data class Trial(val chargeAtMillis: Long) : SubscriptionStatus
    data class Active(val sinceMillis: Long) : SubscriptionStatus
}

/**
 * Maps stored subscription data to a status. The trial ends at the charge time the paywall
 * promised (stored at purchase). While in trial it waits until then and emits
 * [SubscriptionStatus.Active], so an open screen switches over without a reload.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun SubscriptionStore.status(now: () -> Long = System::currentTimeMillis): Flow<SubscriptionStatus> =
    subscription.transformLatest { info ->
        if (!info.paid) {
            emit(SubscriptionStatus.NotSubscribed)
            return@transformLatest
        }
        // Purchases saved before the charge time was stored fall back to purchase + 24h.
        val trialEnd = info.chargeTimeMillis.takeIf { it > 0L }
            ?: (info.purchaseTimeMillis + SubscriptionPlan.TRIAL_DURATION_MILLIS)
        val remaining = trialEnd - now()
        if (remaining > 0) {
            emit(SubscriptionStatus.Trial(chargeAtMillis = trialEnd))
            delay(remaining)
        }
        emit(SubscriptionStatus.Active(sinceMillis = trialEnd))
    }
