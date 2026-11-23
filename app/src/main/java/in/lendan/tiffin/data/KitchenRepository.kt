package `in`.lendan.tiffin.data

import android.content.Context
import `in`.lendan.tiffin.BuildConfig
import `in`.lendan.tiffin.data.model.Kitchen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException

/** Debug-only switch for showing the empty and error states. Always [NORMAL] in release builds. */
enum class SimulationMode { NORMAL, EMPTY, ERROR }

class KitchenRepository(
    context: Context,
    private val assetName: String = "kitchens.json",
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    private val cacheMutex = Mutex()
    private var cache: List<Kitchen>? = null

    @Volatile
    private var debugMode: SimulationMode = SimulationMode.NORMAL

    /** Ignored in release builds: reads always return [SimulationMode.NORMAL] there. */
    var simulationMode: SimulationMode
        get() = if (BuildConfig.DEBUG) debugMode else SimulationMode.NORMAL
        set(value) {
            if (BuildConfig.DEBUG) debugMode = value
        }

    suspend fun getKitchens(): Result<List<Kitchen>> = when (simulationMode) {
        SimulationMode.ERROR -> Result.failure(IOException("Simulated error"))
        SimulationMode.EMPTY -> Result.success(emptyList())
        SimulationMode.NORMAL -> loadKitchens()
    }

    suspend fun getKitchen(id: String): Result<Kitchen> =
        getKitchens().mapCatching { kitchens ->
            kitchens.firstOrNull { it.id == id }
                ?: throw NoSuchElementException("No kitchen with id '$id'")
        }

    private suspend fun loadKitchens(): Result<List<Kitchen>> = withContext(ioDispatcher) {
        try {
            val kitchens = cacheMutex.withLock {
                cache ?: parseAsset().also { cache = it }
            }
            Result.success(kitchens)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseAsset(): List<Kitchen> {
        val text = appContext.assets.open(assetName).bufferedReader().use { it.readText() }
        return json.decodeFromString<List<Kitchen>>(text)
    }
}
