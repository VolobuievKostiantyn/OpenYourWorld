package com.example.openyourworld

import android.app.Application
import android.util.Log
import androidx.preference.PreferenceManager
import org.osmdroid.config.Configuration
import java.io.File

class OpenYourWorldApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("OpenYourWorldApp", "Initializing OSMDroid Configuration")
        
        // OSMDroid configuration must be done as early as possible
        val ctx = applicationContext
        val osmConfig = Configuration.getInstance()
        
        // Load preferences FIRST so they don't overwrite custom programming later
        osmConfig.load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
        
        // FORCE overwrite User-Agent and paths AFTER loading from preferences
        osmConfig.userAgentValue = "OpenYourWorldExplorationAppUniqueCustomName102/1.0 (Android; contact: dev@example.com)"
        osmConfig.osmdroidBasePath = ctx.cacheDir
        osmConfig.osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
        
        Log.d("OpenYourWorldApp", "User-Agent explicitly configured to: ${osmConfig.userAgentValue}")
    }
}
