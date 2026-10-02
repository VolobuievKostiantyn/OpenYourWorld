package com.example.openyourworld

import android.app.Application
import android.util.Log
import org.maplibre.android.MapLibre

class OpenYourWorldApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize MapLibre Native SDK
        MapLibre.getInstance(this)

        Log.d("OpenYourWorldApp", "MapLibre initialized")
    }
}
