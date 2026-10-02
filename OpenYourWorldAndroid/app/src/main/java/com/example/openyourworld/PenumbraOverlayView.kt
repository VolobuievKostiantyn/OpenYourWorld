package com.example.openyourworld

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import java.util.Collections
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Manages the GTA-style Fog of War / Penumbra reveal overlay natively on MapLibre.
 * Uses a native MapLibre GeoJSON Source & Fill Layer so that revealed area holes
 * are rendered directly inside MapLibre's OpenGL pipeline. This ensures the fog
 * moves in 100% perfect synchronization with the background map during drag, pan,
 * and zoom gestures without any frame delay or lag.
 */
class PenumbraOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val SOURCE_ID = "penumbra-geojson-source"
        private const val LAYER_ID = "penumbra-fill-layer"

        // World bounding box outer ring
        private val WORLD_OUTER_RING = listOf(
            Point.fromLngLat(-180.0, -85.0),
            Point.fromLngLat(180.0, -85.0),
            Point.fromLngLat(180.0, 85.0),
            Point.fromLngLat(-180.0, 85.0),
            Point.fromLngLat(-180.0, -85.0)
        )
    }

    private val visitedAreas = Collections.synchronizedList(mutableListOf<Pair<LatLng, Double>>())
    private var mapLibreMap: MapLibreMap? = null
    private var geoJsonSource: GeoJsonSource? = null

    init {
        // Overlay view itself is invisible / non-interactive as MapLibre GL renders the layer directly
        visibility = GONE
    }

    fun attachMap(map: MapLibreMap) {
        this.mapLibreMap = map
        val style = map.style ?: return

        var source = style.getSourceAs<GeoJsonSource>(SOURCE_ID)
        if (source == null) {
            source = GeoJsonSource(SOURCE_ID)
            style.addSource(source)
        }
        geoJsonSource = source

        if (style.getLayer(LAYER_ID) == null) {
            val fillLayer = FillLayer(LAYER_ID, SOURCE_ID).apply {
                setProperties(
                    PropertyFactory.fillColor(Color.argb(180, 30, 30, 30)),
                    PropertyFactory.fillAntialias(true)
                )
            }
            style.addLayer(fillLayer)
        }

        updateLayerGeometry()
    }

    fun addVisitedArea(center: LatLng, radiusMeters: Double) {
        visitedAreas.add(Pair(center, radiusMeters))
        updateLayerGeometry()
    }

    fun clear() {
        visitedAreas.clear()
        updateLayerGeometry()
    }

    private fun updateLayerGeometry() {
        val map = mapLibreMap ?: return
        val style = map.style ?: return
        val source = geoJsonSource ?: style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return

        synchronized(visitedAreas) {
            if (visitedAreas.isEmpty()) {
                source.setGeoJson(FeatureCollection.fromFeatures(arrayOf()))
                return
            }

            val rings = ArrayList<List<Point>>(visitedAreas.size + 1)
            rings.add(WORLD_OUTER_RING)

            for ((center, radiusMeters) in visitedAreas) {
                rings.add(createCircleRing(center.latitude, center.longitude, radiusMeters))
            }

            val polygon = Polygon.fromLngLats(rings)
            source.setGeoJson(Feature.fromGeometry(polygon))
        }
    }

    private fun createCircleRing(centerLat: Double, centerLon: Double, radiusMeters: Double, steps: Int = 24): List<Point> {
        val ring = ArrayList<Point>(steps + 1)
        val latRad = Math.toRadians(centerLat)
        val lonRad = Math.toRadians(centerLon)
        val earthRadiusMeters = 6371008.8
        val d = radiusMeters / earthRadiusMeters

        for (i in 0 until steps) {
            val bearing = Math.toRadians((i * 360.0) / steps)
            val lat2 = asin(
                sin(latRad) * cos(d) + cos(latRad) * sin(d) * cos(bearing)
            )
            val lon2 = lonRad + atan2(
                sin(bearing) * sin(d) * cos(latRad),
                cos(d) - sin(latRad) * sin(lat2)
            )
            ring.add(Point.fromLngLat(Math.toDegrees(lon2), Math.toDegrees(lat2)))
        }
        ring.add(ring[0])
        return ring
    }
}
