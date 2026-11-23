package `in`.lendan.tiffin.analytics

import android.util.Log

interface Analytics {
    fun track(event: String, properties: Map<String, Any?> = emptyMap())
}

class LogcatAnalytics : Analytics {
    override fun track(event: String, properties: Map<String, Any?>) {
        Log.i(TAG, if (properties.isEmpty()) event else "$event $properties")
    }

    private companion object {
        const val TAG = "TiffinAnalytics"
    }
}
