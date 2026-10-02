package com.example.openyourworld

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import java.util.Collections

class PenumbraOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val visitedAreas = Collections.synchronizedList(mutableListOf<Pair<LatLng, Double>>())

    private var mapLibreMap: MapLibreMap? = null

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

    private val boundsRect = RectF()

    fun attachMap(map: MapLibreMap) {
        this.mapLibreMap = map
        map.addOnCameraMoveListener {
            invalidate()
        }
        map.addOnCameraIdleListener {
            invalidate()
        }
        invalidate()
    }

    fun addVisitedArea(center: LatLng, radiusMeters: Double) {
        visitedAreas.add(Pair(center, radiusMeters))
        postInvalidate()
    }

    fun clear() {
        visitedAreas.clear()
        postInvalidate()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val map = mapLibreMap ?: return
        if (visitedAreas.isEmpty()) return

        val widthF = width.toFloat()
        val heightF = height.toFloat()
        if (widthF <= 0f || heightF <= 0f) return

        boundsRect.set(0f, 0f, widthF, heightF)
        val checkpoint = canvas.saveLayer(boundsRect, null)

        // Draw dark fog veil
        canvas.drawRect(0f, 0f, widthF, heightF, veilPaint)

        val projection = map.projection

        // Get visible region bounds for fast viewport culling
        val visibleBounds: LatLngBounds? = try {
            projection.visibleRegion.latLngBounds
        } catch (_: Exception) {
            null
        }

        // Buffer factor to include points just outside screen edge
        val bufferedBounds = visibleBounds?.let {
            val latExpand = (it.latitudeSpan * 0.1).coerceAtLeast(0.001)
            val lonExpand = (it.longitudeSpan * 0.1).coerceAtLeast(0.001)
            LatLngBounds.from(
                (it.latitudeNorth + latExpand).coerceAtMost(85.0),
                (it.longitudeEast + lonExpand).coerceAtMost(180.0),
                (it.latitudeSouth - latExpand).coerceAtLeast(-85.0),
                (it.longitudeWest - lonExpand).coerceAtLeast(-180.0)
            )
        }

        val targetLat = map.cameraPosition.target?.latitude ?: 0.0
        val metersPerPixel = projection.getMetersPerPixelAtLatitude(targetLat)

        synchronized(visitedAreas) {
            for ((latLng, radiusMeters) in visitedAreas) {
                // Viewport check
                if (bufferedBounds != null && !bufferedBounds.contains(latLng)) {
                    continue
                }

                val pixelPoint: PointF = projection.toScreenLocation(latLng)

                val radiusPx = if (metersPerPixel > 0) {
                    (radiusMeters / metersPerPixel).toFloat()
                } else {
                    10f
                }

                // Minimum visible radius so revealed point is clear even at high zoom out
                val effectiveRadiusPx = radiusPx.coerceAtLeast(8f)

                // Draw center clear hole
                canvas.drawCircle(pixelPoint.x, pixelPoint.y, effectiveRadiusPx * 0.7f, clearPaint)

                // Draw feathered gradient edge
                val gradient = RadialGradient(
                    pixelPoint.x,
                    pixelPoint.y,
                    effectiveRadiusPx,
                    intArrayOf(Color.BLACK, Color.TRANSPARENT),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )

                featherPaint.shader = gradient
                canvas.drawCircle(pixelPoint.x, pixelPoint.y, effectiveRadiusPx, featherPaint)
                featherPaint.shader = null
            }
        }

        canvas.restoreToCount(checkpoint)
    }
}
