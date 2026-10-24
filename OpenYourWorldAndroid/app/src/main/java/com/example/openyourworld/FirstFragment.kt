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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.openyourworld.databinding.FragmentFirstBinding
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private const val DEFAULT_ZOOM = 17.0
private const val POINT_RADIUS_METERS = 100.0

class FirstFragment : Fragment() {

    private val TAG = FirstFragment::class.java.simpleName

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private lateinit var mapView: MapView
    private lateinit var penumbraOverlay: PenumbraOverlayView

    private var mapLibreMap: MapLibreMap? = null
    private var currentMarker: Marker? = null

    private lateinit var dbHelper: LocationDatabaseHelper

    private var isFirstFix = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        Log.d(TAG, "onCreateView")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "onViewCreated")

        mapView = binding.mapView
        penumbraOverlay = binding.penumbraOverlay

        dbHelper = LocationDatabaseHelper(requireContext())

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            Log.d(TAG, "MapLibre MapReady")
            mapLibreMap = map

            map.setStyle(Style.Builder().fromUri(MapStyleConfig.getStyleUrl(requireContext()))) { style ->
                Log.d(TAG, "MapLibre Style loaded: ${style.url}")

                // Attach overlay to map camera
                penumbraOverlay.attachMap(map)

                // Load saved historical points from DB
                loadHistoricalLocations()

                // Initial position if available
                val lat = LocationTrackingService.latitude
                val lon = LocationTrackingService.longitude
                if (lat != 0.0 && lon != 0.0) {
                    setPositionMarker(lat, lon, DEFAULT_ZOOM)
                } else {
                    Log.d(TAG, "Waiting for first GPS fix...")
                }
            }
        }

        // Start Service if permissions are already granted
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(requireContext(), LocationTrackingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireContext().startForegroundService(intent)
            } else {
                requireContext().startService(intent)
            }
        }

        // Current position button
        binding.buttonCurrentPosition.setOnClickListener {
            val lat = LocationTrackingService.latitude
            val lon = LocationTrackingService.longitude

            Log.d(TAG, "Button press — live lat=$lat lon=$lon")

            if (lat != 0.0 && lon != 0.0) {
                drawPoint(lat, lon, POINT_RADIUS_METERS, isCurrent = true)
                val currentZoom = mapLibreMap?.cameraPosition?.zoom ?: DEFAULT_ZOOM
                setPositionMarker(lat, lon, currentZoom)
            } else {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(requireContext())
                        .lastLocation
                        .addOnSuccessListener { location ->
                            if (location != null) {
                                val lastLat = location.latitude
                                val lastLon = location.longitude
                                LocationTrackingService.latitude = lastLat
                                LocationTrackingService.longitude = lastLon
                                drawPoint(lastLat, lastLon, POINT_RADIUS_METERS, isCurrent = true)
                                val currentZoom = mapLibreMap?.cameraPosition?.zoom ?: DEFAULT_ZOOM
                                setPositionMarker(lastLat, lastLon, currentZoom)
                            }
                        }
                }
            }
        }

        // Next fragment
        binding.buttonNextFragment.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }
    }

    private fun loadHistoricalLocations() {
        val savedLocations = dbHelper.getAllLocations()
        penumbraOverlay.clear()
        for (loc in savedLocations) {
            penumbraOverlay.addVisitedArea(LatLng(loc.latitude, loc.longitude), POINT_RADIUS_METERS, isCurrent = false)
        }
        penumbraOverlay.invalidate()
        mapView.invalidate()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        mapView.onResume()

        // Register receivers
        val filter = IntentFilter("LOCATION_UPDATED")
        ContextCompat.registerReceiver(
            requireContext(),
            locationReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        ContextCompat.registerReceiver(
            requireContext(),
            clearMapReceiver,
            IntentFilter("CLEAR_MAP"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Reload locations if map is ready (with a slight delay to allow MapLibre surface & projection to re-initialize after screen-on)
        if (mapLibreMap != null) {
            view?.postDelayed({
                loadHistoricalLocations()
            }, 300)
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
        mapView.onStart()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        try {
            requireContext().unregisterReceiver(locationReceiver)
            requireContext().unregisterReceiver(clearMapReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receivers", e)
        }
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
        mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView")
        mapView.onDestroy()
        _binding = null
    }

    private var lastDrawnLatLng: LatLng? = null

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val lat = intent?.getDoubleExtra("lat", 0.0) ?: return
            val lon = intent.getDoubleExtra("lon", 0.0)

            if (lat != 0.0 && lon != 0.0) {
                if (isFirstFix) {
                    setPositionMarker(lat, lon, DEFAULT_ZOOM)
                    isFirstFix = false
                } else {
                    updateMarkerOnly(lat, lon)
                }

                val shouldDrawPoint = if (lastDrawnLatLng == null) {
                    true
                } else {
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(
                        lastDrawnLatLng!!.latitude, lastDrawnLatLng!!.longitude,
                        lat, lon, results
                    )
                    results[0] > 3f // meters
                }

                if (shouldDrawPoint) {
                    lastDrawnLatLng = LatLng(lat, lon)
                    Log.d(TAG, "New position drawn via broadcast lat=$lat lon=$lon")
                    drawPoint(lat, lon, POINT_RADIUS_METERS, isCurrent = false)
                }
            }
        }
    }

    private fun updateMarkerOnly(lat: Double, lon: Double) {
        val latLng = LatLng(lat, lon)
        if (currentMarker != null) {
            currentMarker?.position = latLng
        } else {
            val zoom = mapLibreMap?.cameraPosition?.zoom ?: DEFAULT_ZOOM
            setPositionMarker(lat, lon, zoom)
        }
    }

    private fun drawPoint(lat: Double, lon: Double, radiusMeters: Double, isCurrent: Boolean = false) {
        penumbraOverlay.addVisitedArea(LatLng(lat, lon), radiusMeters, isCurrent)
        penumbraOverlay.invalidate()
    }

    private fun setPositionMarker(latitude: Double, longitude: Double, zoom: Double) {
        val latLng = LatLng(latitude, longitude)
        val map = mapLibreMap ?: return

        currentMarker?.let { map.removeMarker(it) }

        val markerOptions = MarkerOptions()
            .position(latLng)
            .title("You are here")

        currentMarker = map.addMarker(markerOptions)

        map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
        mapView.invalidate()
    }

    private val clearMapReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "CLEAR_MAP received")

            dbHelper.clearLocations()
            penumbraOverlay.clear()

            currentMarker?.let { mapLibreMap?.removeMarker(it) }
            currentMarker = null

            penumbraOverlay.invalidate()
            mapView.invalidate()
        }
    }
}
