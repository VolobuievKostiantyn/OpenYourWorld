package com.example.openyourworld

import android.app.Application
import android.util.Log
import androidx.preference.PreferenceManager
import org.osmdroid.config.Configuration
import java.io.File

class OpenYourWorldApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val context = applicationContext
        val config = Configuration.getInstance()

        // Load osmdroid preferences first
        config.load(
            context,
            PreferenceManager.getDefaultSharedPreferences(context)
        )

        // Identify YOUR application to OpenStreetMap
        config.userAgentValue =
            "OpenYourWorldAndroid/1.0 (+https://github.com/VolobuievKostiantyn/OpenYourWorld)"

        // Persist the updated configuration so it doesn't get overwritten by old preferences
        config.save(context, PreferenceManager.getDefaultSharedPreferences(context))

        // Local tile cache
        config.osmdroidBasePath = File(context.cacheDir, "osmdroid")
        config.osmdroidTileCache =
            File(config.osmdroidBasePath, "tiles")

        // Clear existing tile cache to purge any previously cached 403 error responses
        val tileCacheDir = config.osmdroidTileCache
        if (tileCacheDir.exists()) {
            try {
                tileCacheDir.deleteRecursively()
                Log.d("OpenYourWorldApp", "Cleared osmdroid tile cache to remove old 403 responses")
            } catch (e: Exception) {
                Log.e("OpenYourWorldApp", "Failed to clear osmdroid tile cache", e)
            }
        }

        Log.d(
            "OpenYourWorldApp",
            "OSMDroid User-Agent = ${config.userAgentValue}"
        )
    }
}
