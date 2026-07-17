package com.rafaam11.businfo.ui.map

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.naver.maps.map.overlay.OverlayImage
import com.rafaam11.businfo.R
import kotlin.math.max
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BusMarkerRendererTest {
    private val renderer = BusMarkerRenderer()
    private val density = 2f

    @Test
    fun normalRoutesRenderAtExactDpSizeWithVisibleUnclippedContent() {
        listOf("급행8-1", "814", "555-7").forEach { routeNo ->
            val bitmap = renderer.render(routeNo, RoutePaletteResolver.resolve("1"), false, density)
            assertEquals((72 * density).roundToInt(), bitmap.width)
            assertEquals((34 * density).roundToInt(), bitmap.height)
            assertVisibleAndInsideEveryEdge(bitmap)
            assertNull(renderer.captionFallback(routeNo, density))
        }
    }

    @Test
    fun selectedRouteIsApproximatelyFifteenPercentLarger() {
        val bitmap = renderer.render("814", RoutePaletteResolver.resolve("3"), true, density)
        assertEquals((72 * density * 1.15f).roundToInt(), bitmap.width)
        assertEquals((34 * density * 1.15f).roundToInt(), bitmap.height)
        assertVisibleAndInsideEveryEdge(bitmap)
    }

    @Test
    fun overlongRouteIsReturnedUnabridgedForHorizontalCaptionFallback() {
        val routeNo = "급행-이름이-너무-긴-노선-123456789"
        renderer.render(routeNo, RoutePaletteResolver.resolve("1"), false, density)
        assertEquals(routeNo, renderer.captionFallback(routeNo, density))
    }

    @Test
    fun officialPaletteTextColorsRenderWithoutNumericContrastRejection() {
        (listOf("1", "2", "3", "4", "5", "6", "7") + listOf(null)).forEach { type ->
            val palette = RoutePaletteResolver.resolve(type)
            val bitmap = renderer.render("814", palette, false, density)
            assertTrue("type=$type", bitmap.width > 0 && bitmap.height > 0)
        }
    }

    @Test
    fun cacheReusesAnIdenticalKey() {
        val cache = BusMarkerIconCache(renderer)
        val palette = RoutePaletteResolver.resolve("1")
        val first = cache.icon("급행8-1", palette, false, density)
        assertSame(first, cache.icon("급행8-1", palette, false, density))
    }

    @Test
    fun cacheFallsBackWhenRenderingFails() {
        val palette = RoutePaletteResolver.resolve("1")
        val throwingRenderer = object : BusMarkerRenderer() {
            override fun render(
                routeNo: String,
                palette: RoutePalette,
                selected: Boolean,
                density: Float,
            ): Bitmap = error("font unavailable")
        }
        val fallback = BusMarkerIconCache(throwingRenderer).icon("814", palette, false, density)
        assertEquals(OverlayImage.fromResource(R.drawable.ic_bus_marker), fallback)
    }

    private fun assertVisibleAndInsideEveryEdge(bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue(pixels.count { Color.alpha(it) > 0 } > bitmap.width * bitmap.height / 4)
        val inset = max(1, (2 * density).roundToInt())
        assertTrue((0 until bitmap.height).any { y -> Color.alpha(pixels[y * bitmap.width + inset]) > 0 })
        assertTrue((0 until bitmap.height).any { y -> Color.alpha(pixels[y * bitmap.width + bitmap.width - 1 - inset]) > 0 })
        assertTrue((0 until bitmap.width).any { x -> Color.alpha(pixels[inset * bitmap.width + x]) > 0 })
        assertTrue((0 until bitmap.width).any { x ->
            Color.alpha(pixels[(bitmap.height - 1 - inset) * bitmap.width + x]) > 0
        })
    }
}
