package dev.fand1l.pixelfloat.data.settings

import kotlinx.serialization.Serializable

/**
 * Everything the user can configure, in one atomically-written value.
 *
 * Fields are added with defaults and unknown keys are ignored on read, so a settings file
 * written by an older build keeps loading — which matters because every CI build is
 * installed over the previous one.
 */
@Serializable
data class AppSettings(
    val overlayWindowType: OverlayWindowType = OverlayWindowType.ACCESSIBILITY_OVERLAY,
    val island: IslandGeometry = IslandGeometry(),
)

/**
 * Two pills flanking the camera cutout, with a transparent gap between them.
 *
 * The gap is a first-class parameter rather than a consequence of two independent pill
 * positions: it IS the cutout, it is what must stay clear, and on expand it is the thing
 * that collapses. Modelling it directly is what lets a single progress value drive the
 * whole merge later.
 */
@Serializable
data class IslandGeometry(
    /** Width of the transparent gap — must cover the cutout. */
    val gapDp: Float = 40f,
    /** Distance from the top edge of the display to the top of both pills. */
    val offsetYDp: Float = 10f,
    val heightDp: Float = 28f,
    val leftWidthDp: Float = 72f,
    val rightWidthDp: Float = 72f,
    val cornerDp: Float = 14f,
    /** Shifts the whole island horizontally, for a cutout that is not centred. */
    val centerOffsetXDp: Float = 0f,
    /** When false the right pill is not drawn, but its space stays reserved. */
    val rightPillVisible: Boolean = true,
)

/**
 * The window type is a setting rather than a constant so switching hosts is a toggle, not
 * a refactor. See docs/ARCHITECTURE.md section 1.1: TYPE_APPLICATION_OVERLAY sits at window
 * layer 11 — below the status bar (15) and the keyguard (17) — while
 * TYPE_ACCESSIBILITY_OVERLAY sits at 31.
 */
@Serializable
enum class OverlayWindowType {
    APPLICATION_OVERLAY,
    ACCESSIBILITY_OVERLAY,
}
