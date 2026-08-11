package dev.fand1l.pixelfloat.overlay

import android.view.Surface

/**
 * What the display tells us about itself, read fresh on every show and every rotation.
 * Platform rectangles are converted to [PillRect] at the boundary so everything downstream
 * stays plain Kotlin and unit testable.
 */
data class CutoutInfo(
    val windowWidthPx: Int,
    val windowHeightPx: Int,
    val density: Float,
    val densityDpi: Int,
    val rotation: Int,
    val safeInsetTopPx: Int,
    val boundingRects: List<PillRect>,
) {
    val isLandscape: Boolean
        get() = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270

    /**
     * The cutout rect for the CURRENT rotation. getBoundingRectTop() returns an empty rect
     * in landscape on a Pixel 9 Pro, because the punch-hole moves to a side edge — seeding
     * a landscape profile from it would pin the island at 0,0.
     */
    val activeRect: PillRect?
        get() = boundingRects.firstOrNull { !it.isEmpty }

    fun describe(): String = buildString {
        append("${windowWidthPx}x$windowHeightPx px, density $density (${densityDpi}dpi), ")
        append("rotation $rotation, safeInsetTop $safeInsetTopPx px\n")
        if (boundingRects.isEmpty()) {
            append("boundingRects: none")
        } else {
            boundingRects.forEachIndexed { index, rect ->
                append("rect[$index] = $rect")
                if (index != boundingRects.lastIndex) append('\n')
            }
        }
    }
}

object CutoutGeometryProvider {

    /**
     * Read from the window context, not from an Activity: currentWindowMetrics works with
     * no attached view at all, which is what lets the coordinator decide things before the
     * window exists.
     */
    fun read(host: OverlayHost): CutoutInfo {
        val metrics = host.windowManager.currentWindowMetrics
        val bounds = metrics.bounds
        val cutout = metrics.windowInsets.displayCutout
        val resources = host.windowContext.resources

        return CutoutInfo(
            windowWidthPx = bounds.width(),
            windowHeightPx = bounds.height(),
            density = resources.displayMetrics.density,
            densityDpi = resources.configuration.densityDpi,
            rotation = host.display.rotation,
            safeInsetTopPx = cutout?.safeInsetTop ?: 0,
            boundingRects = cutout?.boundingRects.orEmpty().map {
                PillRect(it.left, it.top, it.right, it.bottom)
            },
        )
    }
}
