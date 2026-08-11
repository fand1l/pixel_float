package dev.fand1l.pixelfloat.overlay

import android.app.Application
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager

/**
 * The one place that names a window type.
 *
 * A bare `Int` constant cannot express this: `createWindowContext(type)` and
 * `LayoutParams.type` must agree, and TYPE_ACCESSIBILITY_OVERLAY additionally requires a
 * context minted by the AccessibilityService itself (it carries the service connection's
 * window token — anything else throws BadTokenException). So the type and the context
 * factory travel together.
 *
 * Stage 1a ships [AppOverlay] only. [dev.fand1l.pixelfloat.service.PixelFloatAccessibilityService]
 * will add the accessibility implementation in stage 1b; nothing outside this file changes.
 */
sealed interface OverlayHost {

    val windowType: Int
    val windowContext: Context
    val windowManager: WindowManager
    val display: Display

    /**
     * TYPE_APPLICATION_OVERLAY, window layer 11 — below the status bar (15), the
     * notification shade and keyguard (17) and the IME (13). Requires SYSTEM_ALERT_WINDOW.
     */
    class AppOverlay(application: Application) : OverlayHost {

        override val windowType: Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        override val display: Display =
            application.getSystemService(DisplayManager::class.java)
                .getDisplay(Display.DEFAULT_DISPLAY)

        // The two-argument createWindowContext(type, options) throws
        // UnsupportedOperationException outside an activity; only this overload works.
        override val windowContext: Context =
            application.createWindowContext(display, windowType, null)

        override val windowManager: WindowManager =
            windowContext.getSystemService(WindowManager::class.java)
    }
}
