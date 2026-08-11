package dev.fand1l.pixelfloat.overlay

import dev.fand1l.pixelfloat.data.settings.IslandGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayGeometryTest {

    private fun cutout(
        widthPx: Int = 1080,
        density: Float = 3f,
        rects: List<PillRect> = emptyList(),
    ) = CutoutInfo(
        windowWidthPx = widthPx,
        windowHeightPx = 2400,
        density = density,
        densityDpi = 480,
        rotation = 0,
        safeInsetTopPx = 100,
        boundingRects = rects,
    )

    @Test
    fun `the gap is centred on the display and has the configured width`() {
        val layout = OverlayGeometry.compute(cutout(), IslandGeometry(gapDp = 40f))
        assertEquals(120, layout.gap.width) // 40dp at density 3
        assertEquals(1080 / 2, layout.gap.centerX)
    }

    @Test
    fun `the pills sit either side of the gap and never overlap it`() {
        val layout = OverlayGeometry.compute(cutout(), IslandGeometry())
        assertEquals(layout.left.right, layout.gap.left)
        assertEquals(layout.right.left, layout.gap.right)
        assertTrue(layout.left.right <= layout.right.left)
    }

    @Test
    fun `the pills can have different widths`() {
        val layout = OverlayGeometry.compute(
            cutout(),
            IslandGeometry(leftWidthDp = 60f, rightWidthDp = 100f),
        )
        assertEquals(180, layout.left.width)
        assertEquals(300, layout.right.width)
    }

    @Test
    fun `a horizontal shift moves the whole island, gap included`() {
        val island = IslandGeometry(centerOffsetXDp = 10f)
        val shifted = OverlayGeometry.compute(cutout(), island)
        val centred = OverlayGeometry.compute(cutout(), island.copy(centerOffsetXDp = 0f))
        assertEquals(30, shifted.left.left - centred.left.left)
        assertEquals(30, shifted.right.left - centred.right.left)
        assertEquals(30, shifted.gap.left - centred.gap.left)
    }

    @Test
    fun `the window is taller than the pills so the spring overshoot is not clipped`() {
        val layout = OverlayGeometry.compute(cutout(), IslandGeometry(heightDp = 28f, offsetYDp = 10f))
        assertTrue(
            "window must leave headroom below the pills",
            layout.windowHeightPx > layout.left.bottom,
        )
    }

    @Test
    fun `geometry scales with display density`() {
        val island = IslandGeometry(leftWidthDp = 100f)
        assertEquals(200, OverlayGeometry.compute(cutout(density = 2f), island).left.width)
        assertEquals(300, OverlayGeometry.compute(cutout(density = 3f), island).left.width)
    }

    @Test
    fun `seeding puts the gap over the reported cutout with a margin either side`() {
        val cutoutRect = PillRect(left = 500, top = 30, right = 580, bottom = 110)
        val seeded = OverlayGeometry.seedFrom(
            cutout(rects = listOf(cutoutRect)),
            IslandGeometry(),
        )
        val layout = OverlayGeometry.compute(cutout(rects = listOf(cutoutRect)), seeded)

        assertTrue("gap must start left of the cutout", layout.gap.left <= cutoutRect.left)
        assertTrue("gap must end right of the cutout", layout.gap.right >= cutoutRect.right)
        assertEquals(cutoutRect.top, layout.left.top)
    }

    @Test
    fun `seeding is a no-op when the platform reports no cutout`() {
        val current = IslandGeometry(gapDp = 44f)
        assertEquals(current, OverlayGeometry.seedFrom(cutout(), current))
    }

    /**
     * Guards the reason this layer uses PillRect instead of android.graphics.Rect: under a
     * local unit test the platform Rect leaves its fields at 0 and its methods throw, so a
     * geometry built on it is silently wrong exactly where it is being tested.
     */
    @Test
    fun `geometry uses no platform types`() {
        val layout = OverlayGeometry.compute(cutout(), IslandGeometry())
        assertEquals(PillRect::class.java, layout.left.javaClass)
        assertEquals(PillRect::class.java, layout.gap.javaClass)
    }
}
