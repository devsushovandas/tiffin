package `in`.lendan.tiffin.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** One DataStore per process, as DataStore requires. */
private val Context.subscriptionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "subscription",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Paid flag, purchase time and promised charge time read from one snapshot, so they are always
 * consistent. [chargeTimeMillis] is 0 if never stored.
 */
data class SubscriptionInfo(
    val paid: Boolean,
    val purchaseTimeMillis: Long,
    val chargeTimeMillis: Long,
)

/** Result of recording a fresh launch. */
data class LaunchRecord(
    val launchCount: Int,
    val paid: Boolean,
    val showAutoPaywall: Boolean,
)

/**
 * Persists launch count and subscription state across rotation, process death and force-stop.
 * DataStore does its file I/O off the main thread, so all of this is safe to call from the UI.
 */
class SubscriptionStore(context: Context) {

    private val dataStore = context.applicationContext.subscriptionDataStore

    private val preferences: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val launchCount: Flow<Int> = preferences.map { it[Keys.LAUNCH_COUNT] ?: 0 }.distinctUntilChanged()
    val paid: Flow<Boolean> = preferences.map { it[Keys.PAID] ?: false }.distinctUntilChanged()
    val purchaseTimeMillis: Flow<Long> = preferences.map { it[Keys.PURCHASE_TIME_MILLIS] ?: 0L }.distinctUntilChanged()
    val chargeTimeMillis: Flow<Long> = preferences.map { it[Keys.CHARGE_TIME_MILLIS] ?: 0L }.distinctUntilChanged()
    val autoPaywallShown: Flow<Boolean> = preferences.map { it[Keys.AUTO_PAYWALL_SHOWN] ?: false }.distinctUntilChanged()

    val subscription: Flow<SubscriptionInfo> = preferences.map {
        SubscriptionInfo(
            paid = it[Keys.PAID] ?: false,
            purchaseTimeMillis = it[Keys.PURCHASE_TIME_MILLIS] ?: 0L,
            chargeTimeMillis = it[Keys.CHARGE_TIME_MILLIS] ?: 0L,
        )
    }.distinctUntilChanged()

    /**
     * Increments the launch count and decides the automatic paywall in one transaction:
     * it is shown on exactly the [AUTO_PAYWALL_LAUNCH]th launch, only if not paid and not shown
     * before, and [autoPaywallShown] is set in the same write so it can never show twice.
     */
    suspend fun recordFreshLaunch(): LaunchRecord {
        var record = LaunchRecord(launchCount = 0, paid = false, showAutoPaywall = false)
        dataStore.edit { prefs ->
            val count = (prefs[Keys.LAUNCH_COUNT] ?: 0) + 1
            val paid = prefs[Keys.PAID] ?: false
            val alreadyShown = prefs[Keys.AUTO_PAYWALL_SHOWN] ?: false
            val show = count == AUTO_PAYWALL_LAUNCH && !paid && !alreadyShown
            prefs[Keys.LAUNCH_COUNT] = count
            if (show) prefs[Keys.AUTO_PAYWALL_SHOWN] = true
            record = LaunchRecord(launchCount = count, paid = paid, showAutoPaywall = show)
        }
        return record
    }

    /**
     * Records a purchase. [chargeTimeMillis] is the ₹249 charge time the paywall promised.
     * Paid, purchase time and charge time are written together in one transaction.
     */
    suspend fun markPaid(purchaseTimeMillis: Long, chargeTimeMillis: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.PAID] = true
            prefs[Keys.PURCHASE_TIME_MILLIS] = purchaseTimeMillis
            prefs[Keys.CHARGE_TIME_MILLIS] = chargeTimeMillis
        }
    }

    suspend fun setAutoPaywallShown(shown: Boolean) {
        dataStore.edit { it[Keys.AUTO_PAYWALL_SHOWN] = shown }
    }

    companion object {
        const val AUTO_PAYWALL_LAUNCH = 3
    }

    private object Keys {
        val LAUNCH_COUNT = intPreferencesKey("launch_count")
        val PAID = booleanPreferencesKey("paid")
        val PURCHASE_TIME_MILLIS = longPreferencesKey("purchase_time_millis")
        val CHARGE_TIME_MILLIS = longPreferencesKey("charge_time_millis")
        val AUTO_PAYWALL_SHOWN = booleanPreferencesKey("auto_paywall_shown")
    }
}
