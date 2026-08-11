package dev.fand1l.pixelfloat.overlay

import dev.fand1l.pixelfloat.data.settings.PillGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayGeometryTest {

    private fun cutout(widthPx: Int = 1080, density: Float = 3f) = CutoutInfo(
        windowWidthPx = widthPx,
        windowHeightPx = 2400,
        density = density,
        densityDpi = 480,
        rotation = 0,
        safeInsetTopPx = 100,
        boundingRects = emptyList(),
    )

    @Test
    fun `pill is centred when there is no horizontal offset`() {
        val layout = OverlayGeometry.compute(cutout(), PillGeometry(widthDp = 100f, offsetXDp = 0f))
        val centre = (layout.pill.left + layout.pill.right) / 2
        assertEquals(1080 / 2, centre)
    }

    @Test
    fun `horizontal offset moves the pill right by that many dp`() {
        val pill = PillGeometry(widthDp = 100f, offsetXDp = 20f)
        val offset = OverlayGeometry.compute(cutout(), pill)
        val centred = OverlayGeometry.compute(cutout(), pill.copy(offsetXDp = 0f))
        assertEquals(60, offset.pill.left - centred.pill.left) // 20dp at density 3
    }

    @Test
    fun `window is taller than the pill so the spring overshoot is not clipped`() {
        val layout = OverlayGeometry.compute(cutout(), PillGeometry(heightDp = 28f, offsetYDp = 10f))
        assertTrue(
            "window must leave headroom below the pill",
            layout.windowHeightPx > layout.pill.bottom,
        )
    }

    @Test
    fun `pill never leaves the display horizontally`() {
        val layout = OverlayGeometry.compute(
            cutout(),
            PillGeometry(widthDp = 100f, offsetXDp = 10_000f),
        )
        assertTrue(layout.pill.left >= 0)
        assertTrue(layout.pill.right <= 1080)
    }

    @Test
    fun `geometry scales with display density`() {
        val pill = PillGeometry(widthDp = 100f, heightDp = 30f)
        val low = OverlayGeometry.compute(cutout(density = 2f), pill)
        val high = OverlayGeometry.compute(cutout(density = 3f), pill)
        assertEquals(200, low.pill.width())
        assertEquals(300, high.pill.width())
    }
}
