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

        // Local tile cache
        config.osmdroidBasePath = File(context.cacheDir, "osmdroid")
        config.osmdroidTileCache =
            File(config.osmdroidBasePath, "tiles")

        Log.d(
            "OpenYourWorldApp",
            "OSMDroid User-Agent = ${config.userAgentValue}"
        )
    }
}
