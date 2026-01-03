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
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteListenableWorker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class LocationTrackingService(context: Context, params: WorkerParameters)
    : CoroutineWorker(context, params) {

    private val TAG = "LocationTrackingService"

    private val locationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(applicationContext)

    object GlobalVariables {
        var latitude: Double = 0.0
        var longitude: Double = 0.0
    }

    // MAIN LOOP — fetch GPS every 5 seconds
    override suspend fun doWork(): Result {
        Log.i(TAG, "Worker started")

        if (!hasLocationPermission()) {
            Log.e(TAG, "Permission missing!")
            return Result.failure()
        }

        // Continuous 5-second updates
        while (!isStopped) {
            val location = getCurrentLocationSuspend()

            if (location != null) {
                GlobalVariables.latitude = location.latitude
                GlobalVariables.longitude = location.longitude

                //Todo: add the location to DB using LocationDatabaseHelper.kt

                Log.d(TAG, "Updated location: ${location.latitude}, ${location.longitude}")

                val db = LocationDatabaseHelper(applicationContext)
                db.insertLocation(location.latitude, location.longitude)
            }

            delay(5000) // <-- update every 5 seconds
        }

        return Result.success()
    }

    private fun hasLocationPermission(): Boolean {
        val ctx = applicationContext
        return ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocationSuspend() =
        suspendCancellableCoroutine<android.location.Location?> { cont ->
            val token = CancellationTokenSource()
            locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }

    // --- UI & WorkManager setup ---
    @Composable
    fun BgLocationAccessScreen() {
        val context = LocalContext.current
        val workManager = WorkManager.getInstance(context)

        Button(onClick = {
            enqueuePeriodicWork(context)
        }) {
            Text("Start Background Location")
        }
    }

    private fun enqueuePeriodicWork(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "LocationTrackingService",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<LocationTrackingService>(
                15, TimeUnit.MINUTES
            ).build()
        )
    }

    companion object {
        private const val LOCATION_UPDATE_INTERVAL = 5L

        fun scheduleWork(context: Context) {
            val componentName = ComponentName(context.packageName, LocationTrackingService::class.java.name)
            val data = Data.Builder()
                .putString(RemoteListenableWorker.ARGUMENT_PACKAGE_NAME, componentName.packageName)
                .putString(RemoteListenableWorker.ARGUMENT_CLASS_NAME, componentName.className)
                .build()

            val request = OneTimeWorkRequest.Builder(LocationTrackingService::class.java)
                .setInputData(data)
                .setInitialDelay(LOCATION_UPDATE_INTERVAL, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
