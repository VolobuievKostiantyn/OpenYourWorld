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
import org.maplibre.android.maps.MapLibreMap
import java.util.Collections

class PenumbraOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class VisitedArea(
        val latLng: LatLng,
        val radiusMeters: Double,
        var isCurrent: Boolean = false
    )

    private val visitedAreas = Collections.synchronizedList(mutableListOf<VisitedArea>())

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

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(220, 76, 175, 80) // Green visited location dot
    }

    private val dotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }

    private val boundsRect = RectF()

    fun attachMap(map: MapLibreMap) {
        this.mapLibreMap = map
        map.addOnCameraIdleListener {
            invalidate()
        }
        postInvalidate()
        invalidate()
    }

    fun addVisitedArea(center: LatLng, radiusMeters: Double, isCurrent: Boolean = false) {
        synchronized(visitedAreas) {
            if (isCurrent) {
                for (area in visitedAreas) {
                    area.isCurrent = false
                }
            }
            visitedAreas.add(VisitedArea(center, radiusMeters, isCurrent))
        }
        postInvalidate()
        invalidate()
    }

    fun clear() {
        visitedAreas.clear()
        postInvalidate()
        invalidate()
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
        val targetLat = map.cameraPosition.target?.latitude ?: 0.0
        val metersPerPixel = projection.getMetersPerPixelAtLatitude(targetLat)

        synchronized(visitedAreas) {
            for (area in visitedAreas) {
                val latLng = area.latLng
                val radiusMeters = area.radiusMeters
                val pixelPoint: PointF = projection.toScreenLocation(latLng)

                val radiusPx = if (metersPerPixel > 0) {
                    (radiusMeters / metersPerPixel).toFloat()
                } else {
                    12f
                }

                val effectiveRadiusPx = radiusPx.coerceAtLeast(12f)

                // Screen pixel bounds check with buffer
                if (pixelPoint.x < -effectiveRadiusPx || pixelPoint.x > widthF + effectiveRadiusPx ||
                    pixelPoint.y < -effectiveRadiusPx || pixelPoint.y > heightF + effectiveRadiusPx) {
                    continue
                }

                // Erase center clear hole in fog
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

                // Draw visible dot at the visited location center only if it is the current point
                if (area.isCurrent) {
                    val dotRadiusPx = (effectiveRadiusPx * 0.25f).coerceIn(6f, 16f)
                    canvas.drawCircle(pixelPoint.x, pixelPoint.y, dotRadiusPx, dotPaint)
                    canvas.drawCircle(pixelPoint.x, pixelPoint.y, dotRadiusPx, dotStrokePaint)
                }
            }
        }

        canvas.restoreToCount(checkpoint)
    }
}
