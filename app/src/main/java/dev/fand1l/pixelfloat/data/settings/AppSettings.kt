package dev.fand1l.pixelfloat.data.settings

import kotlinx.serialization.Serializable

/**
 * Everything the user can configure, in one atomically-written value.
 *
 * Stage 1a only needs the window type and the pill geometry; the rest of the model
 * (whitelist, duration, colours, gestures, orientation profiles) lands with the stages
 * that use it. Fields are added with defaults, so old settings files keep loading.
 */
@Serializable
data class AppSettings(
    val overlayWindowType: OverlayWindowType = OverlayWindowType.APPLICATION_OVERLAY,
    val pill: PillGeometry = PillGeometry(),
)

@Serializable
data class PillGeometry(
    val widthDp: Float = 96f,
    val heightDp: Float = 28f,
    /** Horizontal offset from the horizontal centre of the display, positive = right. */
    val offsetXDp: Float = 0f,
    /** Distance from the top edge of the display. */
    val offsetYDp: Float = 10f,
    val cornerDp: Float = 14f,
)

/**
 * The window type is a setting rather than a constant so switching hosts is a toggle,
 * not a refactor. See docs/ARCHITECTURE.md section 1.1 — TYPE_APPLICATION_OVERLAY sits
 * at window layer 11, below the status bar (15) and the keyguard (17), while
 * TYPE_ACCESSIBILITY_OVERLAY sits at 31.
 */
@Serializable
enum class OverlayWindowType {
    APPLICATION_OVERLAY,
    ACCESSIBILITY_OVERLAY,
}
