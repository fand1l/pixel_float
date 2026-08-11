package dev.fand1l.pixelfloat.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import dev.fand1l.pixelfloat.graph
import dev.fand1l.pixelfloat.overlay.OverlayHost

/**
 * The primary window host. It reads no screen content and handles no events — it exists
 * because it is the ONLY component able to mint a window context carrying the token for
 * TYPE_ACCESSIBILITY_OVERLAY (window layer 31, above the status bar at 15 and the keyguard
 * at 17).
 *
 * The host is built fresh on every connection and dropped on unbind: the token belongs to
 * this service connection, so a cached context would throw BadTokenException after the
 * service is rebound — which happens on every APK update.
 */
class PixelFloatAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        graph.serviceState.setAccessibilityConnected(true)
        val host = runCatching { OverlayHost.Accessibility(this) }
            .onFailure { graph.debugLog.w(TAG, "could not create accessibility window context", it) }
            .getOrNull()
        graph.hosts.setAccessibilityHost(host)
        graph.debugLog.i(TAG, "connected (host=${if (host == null) "none" else "ready"})")
        graph.events.record("accessibility_connected", "")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty: this service exists for its window token, not for events.
    }

    override fun onInterrupt() {
        // Nothing to interrupt.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // The island cannot outlive the token that lets it exist.
        graph.overlay.onHostLost(this::class.java.simpleName)
        graph.hosts.setAccessibilityHost(null)
        graph.serviceState.setAccessibilityConnected(false)
        graph.debugLog.w(TAG, "unbound")
        graph.events.record("accessibility_unbound", "")
        return super.onUnbind(intent)
    }

    private companion object {
        const val TAG = "a11y"
    }
}
