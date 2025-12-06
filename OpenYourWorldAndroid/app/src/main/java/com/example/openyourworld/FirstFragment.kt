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

// Default position, for example, New York City
private const val DEFAULT_LATITUDE_ON_MAP_VIEW = 40.7128
private const val DEFAULT_LONGITUDE_ON_MAP_VIEW = -74.0060
private const val DEFAULT_MAT_VIEW_ZOOM_LEVEL = 17.0

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {
    private var TAG: String = javaClass.simpleName
    private var _binding: FragmentFirstBinding? = null
    private lateinit var map: MapView

    // overlay that darkens everything except visited spots
    private lateinit var penumbraOverlay: PenumbraRevealOverlay

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val handler = android.os.Handler()
    private val locationLogger = object : Runnable {
        override fun run() {
            val lat = LocationTrackingService.GlobalVariables.latitude
            val lon = LocationTrackingService.GlobalVariables.longitude

            Log.d(TAG, "Live location: lat=$lat, lon=$lon")

            handler.postDelayed(this, 1000)   // repeat every second
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

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
        Log.e(TAG, "FirstFragment onViewCreated")

        // Load/initialize the osmdroid configuration
        Configuration.getInstance().load(requireContext().applicationContext, PreferenceManager.getDefaultSharedPreferences(requireContext().applicationContext))

        // Inflate and create the map
        map = view.findViewById(R.id.osmmap)
        map.setTileSource(TileSourceFactory.MAPNIK)

        // attach the gray “hide all” overlay
        penumbraOverlay = PenumbraRevealOverlay()
        map.overlays.add(penumbraOverlay)

        // Find the ComposeView and set the content
        val composeView = view.findViewById<ComposeView>(R.id.compose_view)
        composeView.setContent {
            // Get location updates here
            Log.d(TAG, "Getting location updates started")
            //Todo: continiously update location (location.latitude and location.longitude) each 1 second in the scheduleWork
            LocationTrackingService.scheduleWork(requireContext().applicationContext)
            Log.d(TAG, "Getting location updates ended")
        }

        // Default position
        setPositionMarker(DEFAULT_LATITUDE_ON_MAP_VIEW, DEFAULT_LONGITUDE_ON_MAP_VIEW,
            DEFAULT_MAT_VIEW_ZOOM_LEVEL
        )

        // Set current position
        binding.buttonCurrentPosition.setOnClickListener {
            LocationTrackingService.GlobalVariables.latitude?.let { it1 -> LocationTrackingService.GlobalVariables.longitude?.let { it2 ->
                setPositionMarker(it1,
                    it2, DEFAULT_MAT_VIEW_ZOOM_LEVEL)
            } }

        // Set test position
//        binding.buttonCurrentPosition.setOnClickListener {
//            59.436960.let { it1 -> 24.745710.let { it2 ->
//                setPositionMarker(it1,
//                    it2, DEFAULT_MAT_VIEW_ZOOM_LEVEL)
//            } }

            // Mark area for two positions from the DB on map
            val radiusInMeters = 5.0
            val latitude = LocationTrackingService.GlobalVariables.latitude
            val longitude = LocationTrackingService.GlobalVariables.longitude

            Log.d(TAG, "Latitude = ${latitude}, Longitude = ${longitude}")

            // Draw current position
            if (latitude != 0.0 && longitude != 0.0) {
                drawPoint(map, latitude, longitude, radiusInMeters)
            }

            // Draw test route
//            drawTestRoute(radiusInMeters)  // reveal areas
        }

        binding.buttonNextFragment.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }
    }

    private fun drawPoint(map: MapView, lat: Double, lon: Double, radiusInMeters: Double) {
        val point = GeoPoint(lat, lon)
        // Reveal transparent area at each position
        if (::penumbraOverlay.isInitialized) {
            penumbraOverlay.addVisitedArea(point, radiusInMeters)
        }
        map.invalidate() // refresh after batch updates
    }

    private fun drawTestRoute(radiusInMeters: Double) {
        val coordinates = arrayOf(
            // Start: right-south (SE) corner of the square
            Pair(59.436960, 24.745710),
            Pair(59.436964, 24.745706),
            Pair(59.436969, 24.745703),

            // Brief pause (checking phone)
            Pair(59.436969, 24.745703),
            Pair(59.436968, 24.745704),

            // Begin diagonal toward the center (lat ↑ north, lon ↓ west)
            Pair(59.436980, 24.745690),
            Pair(59.436992, 24.745675),
            Pair(59.437005, 24.745660),
            Pair(59.437018, 24.745646),
            Pair(59.437032, 24.745632),
            Pair(59.437045, 24.745617),

            // Gentle wobble as avoiding people
            Pair(59.437053, 24.745622),
            Pair(59.437061, 24.745610),
            Pair(59.437070, 24.745598),
            Pair(59.437079, 24.745586),
            Pair(59.437088, 24.745574),

            // Slight slow-down (street vendor)
            Pair(59.437088, 24.745574),
            Pair(59.437089, 24.745573),

            // Resume drift toward square center
            Pair(59.437100, 24.745560),
            Pair(59.437113, 24.745545),
            Pair(59.437126, 24.745530),
            Pair(59.437139, 24.745515),
            Pair(59.437151, 24.745502),
            Pair(59.437163, 24.745488),

            // Soft right-left weave
            Pair(59.437171, 24.745494),
            Pair(59.437179, 24.745481),
            Pair(59.437188, 24.745470),
            Pair(59.437197, 24.745458),
            Pair(59.437206, 24.745446),

            // Approaching center of the square
            Pair(59.437214, 24.745432),
            Pair(59.437222, 24.745418),
            Pair(59.437230, 24.745405),
            Pair(59.437238, 24.745392),

            // Brief pause to look around (repeat)
            Pair(59.437238, 24.745392),
            Pair(59.437238, 24.745392),

            // Cross the center with mild jitter
            Pair(59.437247, 24.745378),
            Pair(59.437256, 24.745364),
            Pair(59.437265, 24.745350),
            Pair(59.437274, 24.745336),
            Pair(59.437283, 24.745322),

            // Small sidestep (someone passes)
            Pair(59.437284, 24.745330),
            Pair(59.437289, 24.745318),
            Pair(59.437295, 24.745306),

            // Continue NW
            Pair(59.437304, 24.745292),
            Pair(59.437313, 24.745278),
            Pair(59.437322, 24.745265),
            Pair(59.437331, 24.745252),
            Pair(59.437339, 24.745238),

            // Another micro-pause
            Pair(59.437339, 24.745238),
            Pair(59.437340, 24.745237),

            // Gentle turn left (more west)
            Pair(59.437347, 24.745220),
            Pair(59.437355, 24.745204),
            Pair(59.437362, 24.745188),
            Pair(59.437369, 24.745172),
            Pair(59.437375, 24.745156),

            // Subtle zig-zag as approaching the NW side
            Pair(59.437381, 24.745150),
            Pair(59.437388, 24.745134),
            Pair(59.437395, 24.745120),
            Pair(59.437401, 24.745106),
            Pair(59.437406, 24.745094),

            // Last stretch toward the left-north (NW) corner
            Pair(59.437412, 24.745080),
            Pair(59.437418, 24.745066),
            Pair(59.437424, 24.745054),
            Pair(59.437430, 24.745040),
            Pair(59.437435, 24.745028),

            // Arrive near the NW corner; tiny settle
            Pair(59.437438, 24.745020),
            Pair(59.437438, 24.745020),
            Pair(59.437439, 24.745019)
        )

        // Loop through all coordinates
        for ((lat, lon) in coordinates) {
            val point = GeoPoint(lat, lon)
            // Reveal transparent area at each position
            if (::penumbraOverlay.isInitialized) {
                penumbraOverlay.addVisitedArea(point, radiusInMeters)
            }
        }
        map.invalidate() // refresh after batch updates
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Set the map's center to a specific coordinate
    private fun setPositionMarker(latitude: Double, longitude: Double, zoomLevel: Double) {
        val geoPoint = GeoPoint(latitude, longitude)
        val marker = Marker(map)
        marker.position = geoPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "You are here"
        map.overlays.add(marker)

        val mapController = map.controller as MapController
        mapController.setZoom(zoomLevel.toInt())
        mapController.setCenter(geoPoint)

        map.invalidate() // Refresh the map
    }
}

/**
 * Overlay that darkens the entire map with a gray veil and reveals visited areas
 * with a soft (penumbra) edge.
 */
class PenumbraRevealOverlay : Overlay() {

    // Store visited areas as (center, radiusMeters)
    private val visitedAreas = mutableListOf<Pair<GeoPoint, Double>>()

    // Paints reused each frame
    private val veilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        // overall gray veiling color (semi-transparent)
        color = Color.argb(180, 30, 30, 30)
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val featherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        // xfermode to “subtract” alpha gradually (soft edge)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    fun addVisitedArea(center: GeoPoint, radiusMeters: Double) {
        visitedAreas += center to radiusMeters
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return

        // Save a new layer so CLEAR/DST_OUT work as expected on hardware canvas
        val layerBounds = RectF(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
        val checkpoint = canvas.saveLayer(layerBounds, null)

        // 1) Paint the full-screen gray veil
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), veilPaint)

        // 2) For each visited area, punch a transparent “window” with soft feather
        val projection = mapView.projection
        val tmpPoint = Point()

        for ((geo, radiusMeters) in visitedAreas) {
            // Convert center to screen px
            projection.toPixels(geo, tmpPoint)

            // Convert meters -> pixels at the current latitude/zoom
            val pxPerMeterEquator = projection.metersToEquatorPixels(1f).toDouble()
            val cosLat = kotlin.math.cos(Math.toRadians(geo.latitude))
            val pxPerMeterHere = pxPerMeterEquator / cosLat
            val radiusPx = (radiusMeters * pxPerMeterHere).toFloat()

            // 2a) Hard-clear a core area for full reveal
            val coreRadius = radiusPx * 0.7f
            canvas.drawCircle(tmpPoint.x.toFloat(), tmpPoint.y.toFloat(), coreRadius, clearPaint)

            // 2b) Draw a soft feather (transparent center -> veil alpha at the edge)
            val gradient = RadialGradient(
                tmpPoint.x.toFloat(),
                tmpPoint.y.toFloat(),
                radiusPx,
                intArrayOf(Color.BLACK, Color.TRANSPARENT), // used with DST_OUT to subtract alpha gradually
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            featherPaint.shader = gradient
            canvas.drawCircle(tmpPoint.x.toFloat(), tmpPoint.y.toFloat(), radiusPx, featherPaint)
            featherPaint.shader = null
        }

        // Restore layer (applies our compositing)
        canvas.restoreToCount(checkpoint)
    }
}
