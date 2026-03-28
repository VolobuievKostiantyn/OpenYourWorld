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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.openyourworld.databinding.FragmentFirstBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay

private const val DEFAULT_ZOOM = 17.0
private const val POINT_RADIUS_METERS = 4.0

class FirstFragment : Fragment() {

    private val TAG = FirstFragment::class.java.simpleName

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private lateinit var map: MapView
    private lateinit var penumbraOverlay: PenumbraRevealOverlay

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var dbHelper: LocationDatabaseHelper

    private var isFirstFix = true

    private val locationLogger = object : Runnable {
        override fun run() {
            val lat = LocationTrackingService.latitude
            val lon = LocationTrackingService.longitude

            // Update map for each position
            Log.d(TAG, "Live location: lat=$lat, lon=$lon")

            if (lat != 0.0 && lon != 0.0) {
                // Save to DB
                dbHelper.insertLocation(lat, lon)

                // Draw on map
                drawPoint(map, lat, lon, POINT_RADIUS_METERS)
            }

            handler.postDelayed(this, 1000) // update every 1 second
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        Log.d(TAG, "onCreateView")
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        
        // Don't clear here if we want to keep state, but if we do, invalidate
        // penumbraOverlay.clear() 
        
        map.invalidate()

        ContextCompat.registerReceiver(
            requireContext(),
            locationReceiver,
            IntentFilter("LOCATION_UPDATED"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        ContextCompat.registerReceiver(
            requireContext(),
            clearMapReceiver,
            IntentFilter("CLEAR_MAP"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val savedLocations = dbHelper.getAllLocations()
        for (loc in savedLocations) {
            drawPoint(map, loc.latitude, loc.longitude, POINT_RADIUS_METERS)
        }
        map.invalidate()
        //handler.post(locationLogger)
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        requireContext().unregisterReceiver(locationReceiver)
        requireContext().unregisterReceiver(clearMapReceiver)
        //handler.removeCallbacks(locationLogger)
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "onViewCreated")

        // OSMDroid setup
        Configuration.getInstance().load(requireContext(), PreferenceManager.getDefaultSharedPreferences(requireContext()))
        map = view.findViewById(R.id.osmmap)
        map.setTileSource(TileSourceFactory.MAPNIK)

        dbHelper = LocationDatabaseHelper(requireContext())

        penumbraOverlay = PenumbraRevealOverlay()
        map.overlays.add(penumbraOverlay)
        // Set a default zoom immediately so the map isn't zoomed out to the world
        map.controller.setZoom(DEFAULT_ZOOM)

        val intent = Intent(requireContext(), LocationTrackingService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent)
        } else {
            requireContext().startService(intent)
        }

        // Initial position
        val lat = LocationTrackingService.latitude
        val lon = LocationTrackingService.longitude
        if (lat != 0.0 && lon != 0.0) {
            setPositionMarker(lat, lon, DEFAULT_ZOOM)
        } else {
            // If first install, we just wait for the first broadcast
            // but the zoom is already set by step 1 above.
            Log.d(TAG, "Waiting for first GPS fix...")
        }

        // Draw on map all previously visited places
        val savedLocations = dbHelper.getAllLocations()
        for (loc in savedLocations) {
            drawPoint(map, loc.latitude, loc.longitude, POINT_RADIUS_METERS)
        }

        // Current position button
        binding.buttonCurrentPosition.setOnClickListener {
            // Todo: add the code below to the onCreate - start draw on map once app is opened
            val lat = LocationTrackingService.latitude
            val lon = LocationTrackingService.longitude

            Log.d(TAG, "Button press — live lat=$lat lon=$lon")

            if (lat != 0.0 && lon != 0.0) {
                val currentZoom = if (map.zoomLevelDouble > 1.0) map.zoomLevelDouble else DEFAULT_ZOOM
                setPositionMarker(lat, lon, currentZoom)
            }
        }

        // Next fragment
        binding.buttonNextFragment.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }
    }

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val lat = intent?.getDoubleExtra("lat", 0.0) ?: return
            val lon = intent.getDoubleExtra("lon", 0.0)

            if (lat != 0.0 && lon != 0.0) {
                Log.d(TAG, "New position drawn via broadcast lat=$lat lon=$lon")
                drawPoint(map, lat, lon, POINT_RADIUS_METERS)

                // If this is the first fix since the app opened, center the map
                if (isFirstFix) {
                    setPositionMarker(lat, lon, DEFAULT_ZOOM)
                    isFirstFix = false // Don't snap/jump the camera anymore after this
                }
            }
        }
    }

    private fun drawPoint(map: MapView, lat: Double, lon: Double, radiusMeters: Double) {
        penumbraOverlay.addVisitedArea(GeoPoint(lat, lon), radiusMeters)
        map.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView")
        _binding = null
    }

    private fun setPositionMarker(latitude: Double, longitude: Double, zoom: Double) {
        val geoPoint = GeoPoint(latitude, longitude)

        // Remove existing markers to avoid stacking multiple "You are here" icons
        val markersToRemove = map.overlays.filterIsInstance<Marker>()
        map.overlays.removeAll(markersToRemove)

        val marker = Marker(map)
        marker.position = geoPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "You are here"
        map.overlays.add(marker)

        map.controller.setZoom(zoom)
        map.controller.setCenter(geoPoint)

        map.invalidate()
    }

    private val clearMapReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "CLEAR_MAP received")

            // clear DB
            dbHelper.clearLocations()

            // clear overlay
            penumbraOverlay.clear()

            // remove markers but keep the overlay
            val it = map.overlays.iterator()
            while (it.hasNext()) {
                val overlay = it.next()
                if (overlay is Marker) {
                    it.remove()
                }
            }

            map.invalidate()
        }
    }
}


/**********************
 * PENUMBRA OVERLAY
 **********************/
class PenumbraRevealOverlay : Overlay() {

    private val visitedAreas = mutableListOf<Pair<GeoPoint, Double>>()

    private val veilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(180, 30, 30, 30)
    }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val featherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    fun addVisitedArea(center: GeoPoint, radiusMeters: Double) {
        visitedAreas += center to radiusMeters
    }

    fun clear() {
        visitedAreas.clear()
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return

        val bounds = RectF(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
        val checkpoint = canvas.saveLayer(bounds, null)

        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), veilPaint)

        val projection = mapView.projection
        val tmpPoint = Point()

        for ((geo, radiusMeters) in visitedAreas) {
            projection.toPixels(geo, tmpPoint)

            val pxPerM = projection.metersToPixels(1f).toDouble()
            val radiusPx = (radiusMeters * pxPerM).toFloat()

            canvas.drawCircle(tmpPoint.x.toFloat(), tmpPoint.y.toFloat(), radiusPx * 0.7f, clearPaint)

            val gradient = RadialGradient(
                tmpPoint.x.toFloat(),
                tmpPoint.y.toFloat(),
                radiusPx,
                intArrayOf(Color.BLACK, Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )

            featherPaint.shader = gradient
            canvas.drawCircle(tmpPoint.x.toFloat(), tmpPoint.y.toFloat(), radiusPx, featherPaint)
            featherPaint.shader = null
        }

        canvas.restoreToCount(checkpoint)
    }
}
