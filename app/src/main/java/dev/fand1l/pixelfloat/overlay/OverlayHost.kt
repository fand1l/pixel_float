package dev.fand1l.pixelfloat.overlay

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager
import dev.fand1l.pixelfloat.data.settings.OverlayWindowType

/**
 * The one place that names a window type.
 *
 * A bare `Int` constant cannot express this: `createWindowContext(type)` and
 * `LayoutParams.type` must agree, and TYPE_ACCESSIBILITY_OVERLAY additionally requires a
 * context minted by the AccessibilityService itself — it wraps the result in a context
 * carrying that service connection's window token, and any other context throws
 * BadTokenException. So the type and the context factory travel together.
 */
sealed interface OverlayHost {

    val type: OverlayWindowType
    val windowType: Int
    val windowContext: Context
    val windowManager: WindowManager
    val display: Display

    /**
     * Window layer 31 — above the status bar (15), the notification shade and keyguard (17)
     * and the IME (13). The primary host.
     *
     * Must be constructed inside onServiceConnected and dropped in onUnbind: the window
     * token belongs to that specific service connection, so a cached instance goes stale.
     */
    class Accessibility(service: AccessibilityService) : OverlayHost {

        override val type: OverlayWindowType = OverlayWindowType.ACCESSIBILITY_OVERLAY

        override val windowType: Int = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

        override val display: Display =
            service.getSystemService(DisplayManager::class.java)
                .getDisplay(Display.DEFAULT_DISPLAY)

        override val windowContext: Context =
            service.createWindowContext(display, windowType, null)

        override val windowManager: WindowManager =
            windowContext.getSystemService(WindowManager::class.java)
    }

    /**
     * Window layer 11 — below the status bar, so the system clock and battery icons draw
     * ON TOP of the island, and the keyguard hides it entirely. Kept as the fallback for
     * when accessibility access is not granted, and as the control case for the stage 1b
     * measurement. Requires SYSTEM_ALERT_WINDOW.
     */
    class AppOverlay(application: Application) : OverlayHost {

        override val type: OverlayWindowType = OverlayWindowType.APPLICATION_OVERLAY

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
