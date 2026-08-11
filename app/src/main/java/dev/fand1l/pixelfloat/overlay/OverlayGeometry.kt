package dev.fand1l.pixelfloat.overlay

import dev.fand1l.pixelfloat.data.settings.IslandGeometry
import kotlin.math.roundToInt

/** Where the window sits and where the two pills sit inside it, in window-local pixels. */
data class IslandLayout(
    val windowHeightPx: Int,
    val left: PillRect,
    val right: PillRect,
    val cornerRadiusPx: Float,
    val rightPillVisible: Boolean,
) {
    /** The transparent gap between the pills — the part that must stay clear and must pass touches. */
    val gap: PillRect get() = PillRect(left.right, left.top, right.left, right.bottom)
}

/**
 * A pure function of (settings, display metrics) — no Android types at all, so it is unit
 * testable and the in-app calibration preview can share it verbatim, which is what makes
 * the preview's coordinate space provably identical to the overlay's.
 */
object OverlayGeometry {

    /**
     * The spring at dampingRatio 0.55 overshoots its target by exp(-πζ/√(1-ζ²)) = 12.6 %.
     * A window sized exactly to the pills would clip the entrance bounce — invisible in a
     * @Preview, obvious on device.
     */
    const val OVERSHOOT_HEADROOM = 0.18f

    /** Breathing room between the cutout edge and a pill, when seeding from the cutout. */
    const val SEED_MARGIN_DP = 6f

    fun compute(cutout: CutoutInfo, island: IslandGeometry): IslandLayout {
        val density = cutout.density
        fun px(dp: Float) = (dp * density).roundToInt()

        val heightPx = px(island.heightDp).coerceAtLeast(1)
        val topPx = px(island.offsetYDp).coerceAtLeast(0)
        val gapPx = px(island.gapDp).coerceAtLeast(0)
        val centreX = cutout.windowWidthPx / 2 + px(island.centerOffsetXDp)

        val leftRight = centreX - gapPx / 2
        val leftLeft = leftRight - px(island.leftWidthDp).coerceAtLeast(1)
        val rightLeft = leftRight + gapPx
        val rightRight = rightLeft + px(island.rightWidthDp).coerceAtLeast(1)

        val headroomPx = (heightPx * OVERSHOOT_HEADROOM).roundToInt()

        return IslandLayout(
            windowHeightPx = topPx + heightPx + headroomPx,
            left = PillRect(leftLeft, topPx, leftRight, topPx + heightPx),
            right = PillRect(rightLeft, topPx, rightRight, topPx + heightPx),
            cornerRadiusPx = island.cornerDp * density,
            rightPillVisible = island.rightPillVisible,
        )
    }

    /**
     * A starting suggestion derived from what the display reports about its cutout. From
     * here it is manual — the owner drags. Falls back to the status-bar-ish defaults when
     * the platform reports no cutout (an emulator, or landscape on a Pixel 9 Pro, where
     * the punch-hole moves to a side edge and the top bounding rect comes back empty).
     */
    fun seedFrom(cutout: CutoutInfo, current: IslandGeometry): IslandGeometry {
        val rect = cutout.activeRect ?: return current
        val density = cutout.density
        fun dp(px: Int) = px / density

        val cutoutCentreX = rect.centerX
        val screenCentreX = cutout.windowWidthPx / 2

        return current.copy(
            gapDp = dp(rect.width) + 2 * SEED_MARGIN_DP,
            offsetYDp = dp(rect.top),
            heightDp = dp(rect.height).coerceAtLeast(16f),
            centerOffsetXDp = dp(cutoutCentreX - screenCentreX),
        )
    }
}
