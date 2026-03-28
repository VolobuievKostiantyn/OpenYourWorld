/*
* Copyright 2023 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.example.openyourworld

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationTrackingService : Service() {

    private val TAG = LocationTrackingService::class.java.simpleName

    companion object GlobalVariables {
        @Volatile var latitude: Double = 0.0
        @Volatile var longitude: Double = 0.0
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var callback: LocationCallback
    private lateinit var dbHelper: LocationDatabaseHelper
//    private var lastSavedLocation: Location? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Calling this here prevents the "ForegroundServiceDidNotStartInTimeException"
        startForegroundServiceInternal()
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        dbHelper = LocationDatabaseHelper(applicationContext)

        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return

                // Todo: Only saves meaningful movement and reduces database writes.
//                if (lastSavedLocation == null || loc.distanceTo(lastSavedLocation!!) > 5f) {
//                    Thread { dbHelper.insertLocation(loc.latitude, loc.longitude) }.start()
//                    lastSavedLocation = loc
//                }

                latitude = loc.latitude
                longitude = loc.longitude

                val lat = loc.latitude
                val lon = loc.longitude

                // Notify UI immediately for real-time drawing
                val intent = Intent("LOCATION_UPDATED")
                intent.putExtra("lat", lat)
                intent.putExtra("lon", lon)
                intent.setPackage(packageName)
                sendBroadcast(intent)

                // Save to database in background and notify UI
                Thread {
                    Log.d(TAG, "Inserting location and broadcasting: lat=$lat lon=$lon")
                    dbHelper.insertLocation(lat, lon)
                }.start()
            }
        }

        startForegroundServiceInternal()
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        ).build()

        fusedClient.requestLocationUpdates(request, callback, mainLooper)
    }

    private fun startForegroundServiceInternal() {
        val channelId = "location_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Tracking location")
            .setContentText("Location updates active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        fusedClient.removeLocationUpdates(callback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
