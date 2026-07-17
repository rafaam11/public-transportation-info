package com.rafaam11.businfo.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.LruCache
import com.naver.maps.map.overlay.OverlayImage
import com.rafaam11.businfo.R
import kotlin.math.roundToInt

open class BusMarkerRenderer {
    open fun render(
        routeNo: String,
        palette: RoutePalette,
        selected: Boolean,
        density: Float,
    ): Bitmap {
        require(density > 0f) { "density must be positive" }

        val selectedScale = if (selected) SELECTED_SCALE else 1f
        val width = (WIDTH_DP * density * selectedScale).roundToInt()
        val height = (HEIGHT_DP * density * selectedScale).roundToInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(selectedScale, selectedScale)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = SHADOW_COLOR
        canvas.drawRoundRect(
            rect(density, SHADOW_LEFT_DP, SHADOW_TOP_DP, SHADOW_RIGHT_DP, SHADOW_BOTTOM_DP),
            BODY_RADIUS_DP * density,
            BODY_RADIUS_DP * density,
            paint,
        )

        val body = rect(density, BODY_LEFT_DP, BODY_TOP_DP, BODY_RIGHT_DP, BODY_BOTTOM_DP)
        if (selected) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = SELECTED_STROKE_DP * density
            paint.color = Color.WHITE
            canvas.drawRoundRect(body, BODY_RADIUS_DP * density, BODY_RADIUS_DP * density, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = palette.bodyColor
        canvas.drawRoundRect(body, BODY_RADIUS_DP * density, BODY_RADIUS_DP * density, paint)

        paint.color = WINDOW_COLOR
        canvas.drawRoundRect(
            rect(density, WINDOW_LEFT_DP, WINDOW_TOP_DP, WINDOW_RIGHT_DP, WINDOW_BOTTOM_DP),
            WINDOW_RADIUS_DP * density,
            WINDOW_RADIUS_DP * density,
            paint,
        )
        paint.color = WINDOW_DIVIDER_COLOR
        paint.strokeWidth = WINDOW_DIVIDER_DP * density
        listOf(DIVIDER_ONE_DP, DIVIDER_TWO_DP, DIVIDER_THREE_DP).forEach { xDp ->
            canvas.drawLine(
                xDp * density,
                WINDOW_TOP_DP * density,
                xDp * density,
                WINDOW_BOTTOM_DP * density,
                paint,
            )
        }

        paint.color = WHEEL_COLOR
        canvas.drawCircle(FRONT_WHEEL_X_DP * density, WHEEL_Y_DP * density, WHEEL_RADIUS_DP * density, paint)
        canvas.drawCircle(REAR_WHEEL_X_DP * density, WHEEL_Y_DP * density, WHEEL_RADIUS_DP * density, paint)

        configureRoutePaint(paint, routeNo, density)
        val textY = TEXT_BASELINE_DP * density - (paint.ascent() + paint.descent()) / 2f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = TEXT_OUTLINE_DP * density
        paint.color = if (Color.luminance(palette.textColor) > LIGHT_TEXT_LUMINANCE) {
            Color.BLACK
        } else {
            Color.WHITE
        }
        canvas.drawText(routeNo, WIDTH_DP * density / 2f, textY, paint)
        paint.style = Paint.Style.FILL
        paint.color = palette.textColor
        canvas.drawText(routeNo, WIDTH_DP * density / 2f, textY, paint)
        return bitmap
    }

    fun captionFallback(routeNo: String, density: Float): String? {
        require(density > 0f) { "density must be positive" }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        configureRoutePaint(paint, routeNo, density)
        return routeNo.takeIf { paint.measureText(it) > maxTextWidth(density) }
    }

    private fun configureRoutePaint(paint: Paint, routeNo: String, density: Float) {
        var textSize = INITIAL_TEXT_SIZE_DP * density
        val maxTextWidth = maxTextWidth(density)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = textSize
        while (paint.measureText(routeNo) > maxTextWidth && textSize > MIN_TEXT_SIZE_DP * density) {
            textSize -= density
            paint.textSize = textSize
        }
    }

    private fun maxTextWidth(density: Float) = (WIDTH_DP - TEXT_HORIZONTAL_INSET_DP) * density

    private fun rect(density: Float, left: Float, top: Float, right: Float, bottom: Float) =
        RectF(left * density, top * density, right * density, bottom * density)

    private companion object {
        const val WIDTH_DP = 72f
        const val HEIGHT_DP = 34f
        const val SELECTED_SCALE = 1.15f
        const val SELECTED_STROKE_DP = 2f
        const val SHADOW_LEFT_DP = 1f
        const val SHADOW_TOP_DP = 5f
        const val SHADOW_RIGHT_DP = 71f
        const val SHADOW_BOTTOM_DP = 31f
        const val BODY_LEFT_DP = 2f
        const val BODY_TOP_DP = 2f
        const val BODY_RIGHT_DP = 70f
        const val BODY_BOTTOM_DP = 28f
        const val BODY_RADIUS_DP = 6f
        const val WINDOW_LEFT_DP = 7f
        const val WINDOW_TOP_DP = 6f
        const val WINDOW_RIGHT_DP = 65f
        const val WINDOW_BOTTOM_DP = 15f
        const val WINDOW_RADIUS_DP = 2f
        const val WINDOW_DIVIDER_DP = 1f
        const val DIVIDER_ONE_DP = 21f
        const val DIVIDER_TWO_DP = 36f
        const val DIVIDER_THREE_DP = 51f
        const val FRONT_WHEEL_X_DP = 16f
        const val REAR_WHEEL_X_DP = 56f
        const val WHEEL_Y_DP = 28f
        const val WHEEL_RADIUS_DP = 4f
        const val INITIAL_TEXT_SIZE_DP = 15f
        const val MIN_TEXT_SIZE_DP = 8f
        const val TEXT_HORIZONTAL_INSET_DP = 20f
        const val TEXT_BASELINE_DP = 20f
        const val TEXT_OUTLINE_DP = 1.25f
        const val LIGHT_TEXT_LUMINANCE = 0.5f
        val SHADOW_COLOR = Color.argb(90, 0, 0, 0)
        val WINDOW_COLOR = Color.rgb(231, 242, 246)
        val WINDOW_DIVIDER_COLOR = Color.rgb(184, 207, 216)
        val WHEEL_COLOR = Color.rgb(32, 34, 37)
    }
}

data class BusMarkerKey(
    val routeNo: String,
    val bodyColor: Int,
    val textColor: Int,
    val selected: Boolean,
    val densityDpi: Int,
)

class BusMarkerIconCache(
    private val renderer: BusMarkerRenderer = BusMarkerRenderer(),
) {
    private val icons = object : LruCache<BusMarkerKey, OverlayImage>(CACHE_SIZE) {}

    fun icon(
        routeNo: String,
        palette: RoutePalette,
        selected: Boolean,
        density: Float,
    ): OverlayImage {
        val key = BusMarkerKey(
            routeNo = routeNo,
            bodyColor = palette.bodyColor,
            textColor = palette.textColor,
            selected = selected,
            densityDpi = (density * DENSITY_DEFAULT_DPI).roundToInt(),
        )
        icons.get(key)?.let { return it }
        val icon = runCatching {
            OverlayImage.fromBitmap(renderer.render(routeNo, palette, selected, density))
        }.getOrElse {
            OverlayImage.fromResource(R.drawable.ic_bus_marker)
        }
        icons.put(key, icon)
        return icon
    }

    fun evictAll() = icons.evictAll()

    private companion object {
        const val CACHE_SIZE = 32
        const val DENSITY_DEFAULT_DPI = 160
    }
}
