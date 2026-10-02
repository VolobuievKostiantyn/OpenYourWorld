package com.example.openyourworld

import android.content.Context

/**
 * Isolated configuration for MapLibre map styles and vector tile providers.
 */
object MapStyleConfig {
    /**
     * Default style URL.
     * Uses OpenFreeMap Liberty vector tile style.
     * OpenFreeMap provides open vector tiles without requiring an API key.
     */
    private const val DEFAULT_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

    /**
     * Fallback style URL if primary style is unavailable.
     */
    const val FALLBACK_STYLE_URL = "https://demotiles.maplibre.org/style.json"

    /**
     * Returns the configured MapLibre style URL.
     * Can be easily overridden or customized via BuildConfig / SharedPreferences / local.properties if needed.
     */
    fun getStyleUrl(context: Context? = null): String {
        return DEFAULT_STYLE_URL
    }
}
