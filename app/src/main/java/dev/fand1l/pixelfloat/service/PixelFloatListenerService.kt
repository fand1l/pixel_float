package dev.fand1l.pixelfloat.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.fand1l.pixelfloat.graph

/**
 * Stage 1a: declared and bound, but does nothing with notifications yet — the filter chain,
 * mapper, deduper and queue arrive in stage 2. Being bound this early is still useful: it
 * proves the manifest wiring and shows in the debug screen whether the grant survived the
 * last APK install.
 *
 * There is deliberately no foreground service and no persistent notification: a
 * NotificationListenerService is a bound service that the system keeps alive itself.
 */
class PixelFloatListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        graph.serviceState.setListenerConnected(true)
        graph.debugLog.i(TAG, "listener connected")
        graph.events.record("listener_connected", "")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        graph.serviceState.setListenerConnected(false)
        graph.debugLog.w(TAG, "listener disconnected")
        graph.events.record("listener_disconnected", "")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Stage 2.
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Stage 2.
    }

    private companion object {
        const val TAG = "listener"
    }
}
