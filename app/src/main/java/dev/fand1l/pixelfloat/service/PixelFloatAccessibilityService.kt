package dev.fand1l.pixelfloat.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import dev.fand1l.pixelfloat.graph

/**
 * The future primary window host. It reads no screen content and handles no events — its
 * only purpose is that it is the ONLY component able to mint a window context carrying the
 * token for TYPE_ACCESSIBILITY_OVERLAY (window layer 31, above the status bar at 15 and the
 * keyguard at 17).
 *
 * Stage 1a only reports whether it is bound; the OverlayHost.Accessibility implementation
 * lands in stage 1b, once the app-overlay path has been measured on device.
 */
class PixelFloatAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        graph.serviceState.setAccessibilityConnected(true)
        graph.debugLog.i(TAG, "accessibility service connected")
        graph.events.record("accessibility_connected", "")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty: this service exists for its window token, not for events.
    }

    override fun onInterrupt() {
        // Nothing to interrupt.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        graph.serviceState.setAccessibilityConnected(false)
        graph.debugLog.w(TAG, "accessibility service unbound")
        graph.events.record("accessibility_unbound", "")
        return super.onUnbind(intent)
    }

    private companion object {
        const val TAG = "a11y"
    }
}
