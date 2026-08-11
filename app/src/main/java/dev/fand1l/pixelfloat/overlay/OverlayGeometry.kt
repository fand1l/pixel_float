package dev.fand1l.pixelfloat.overlay

import dev.fand1l.pixelfloat.data.settings.PillGeometry
import kotlin.math.roundToInt

/** Where the window sits and where the pill sits inside it, in window-local pixels. */
data class IslandLayout(
    val windowHeightPx: Int,
    val pill: PillRect,
    val cornerRadiusPx: Float,
)

/**
 * A pure function of (settings, display metrics) — no Android types at all, so it is unit
 * testable and the in-app calibration preview can share it verbatim, which is what makes
 * the preview's coordinate space provably identical to the overlay's.
 */
object OverlayGeometry {

    /**
     * The spring at dampingRatio 0.55 overshoots its target by exp(-πζ/√(1-ζ²)) = 12.6 %.
     * A window sized exactly to the pill would clip the entrance bounce — invisible in a
     * @Preview, obvious on device. Stage 1a has no animation yet, but the headroom is part
     * of the geometry, not of the animation, so it lives here from the start.
     */
    const val OVERSHOOT_HEADROOM = 0.18f

    fun compute(cutout: CutoutInfo, pill: PillGeometry): IslandLayout {
        val density = cutout.density
        val widthPx = (pill.widthDp * density).roundToInt().coerceAtLeast(1)
        val heightPx = (pill.heightDp * density).roundToInt().coerceAtLeast(1)
        val topPx = (pill.offsetYDp * density).roundToInt().coerceAtLeast(0)

        val centreX = cutout.windowWidthPx / 2 + (pill.offsetXDp * density).roundToInt()
        val left = (centreX - widthPx / 2)
            .coerceIn(0, (cutout.windowWidthPx - widthPx).coerceAtLeast(0))

        val headroomPx = (heightPx * OVERSHOOT_HEADROOM).roundToInt()

        return IslandLayout(
            windowHeightPx = topPx + heightPx + headroomPx,
            pill = PillRect(left, topPx, left + widthPx, topPx + heightPx),
            cornerRadiusPx = pill.cornerDp * density,
        )
    }
}
