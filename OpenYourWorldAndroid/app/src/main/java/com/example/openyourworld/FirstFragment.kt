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

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
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

private const val DEFAULT_LATITUDE = 40.7128
private const val DEFAULT_LONGITUDE = -74.0060
private const val DEFAULT_ZOOM = 17.0

class FirstFragment : Fragment() {

    private val TAG = "FirstFragment"

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private lateinit var map: MapView
    private lateinit var penumbraOverlay: PenumbraRevealOverlay

    private val handler = Handler(Looper.getMainLooper())

    private val locationLogger = object : Runnable {
        override fun run() {
            val lat = LocationTrackingService.GlobalVariables.latitude
            val lon = LocationTrackingService.GlobalVariables.longitude

            // Todo: find out why Live location: lat=0.0, lon=0.0

            Log.d(TAG, "Live location: lat=$lat, lon=$lon")

            handler.postDelayed(this, 1000) // update every second
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        handler.post(locationLogger)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(locationLogger)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // OSMDroid setup
        Configuration.getInstance().load(requireContext(), PreferenceManager.getDefaultSharedPreferences(requireContext()))
        map = view.findViewById(R.id.osmmap)
        map.setTileSource(TileSourceFactory.MAPNIK)

        penumbraOverlay = PenumbraRevealOverlay()
        map.overlays.add(penumbraOverlay)

        // Compose view starts Worker
        view.findViewById<ComposeView>(R.id.compose_view).setContent {
            LocationTrackingService.scheduleWork(requireContext().applicationContext)
        }

        // Initial position
        setPositionMarker(DEFAULT_LATITUDE, DEFAULT_LONGITUDE, DEFAULT_ZOOM)

        // CURRENT POSITION BUTTON
        binding.buttonCurrentPosition.setOnClickListener {
            val lat = LocationTrackingService.GlobalVariables.latitude
            val lon = LocationTrackingService.GlobalVariables.longitude

            Log.d(TAG, "Button press — live lat=$lat lon=$lon")

            if (lat != 0.0 && lon != 0.0) {
                setPositionMarker(lat, lon, DEFAULT_ZOOM)
                drawPoint(map, lat, lon, 5.0)
            }
        }

        // Next fragment
        binding.buttonNextFragment.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }
    }

    private fun drawPoint(map: MapView, lat: Double, lon: Double, radiusMeters: Double) {
        penumbraOverlay.addVisitedArea(GeoPoint(lat, lon), radiusMeters)
        map.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setPositionMarker(latitude: Double, longitude: Double, zoom: Double) {
        val geoPoint = GeoPoint(latitude, longitude)
        val marker = Marker(map)
        marker.position = geoPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "You are here"
        map.overlays.add(marker)

        (map.controller as MapController).apply {
            setZoom(zoom.toInt())
            setCenter(geoPoint)
        }

        map.invalidate()
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
