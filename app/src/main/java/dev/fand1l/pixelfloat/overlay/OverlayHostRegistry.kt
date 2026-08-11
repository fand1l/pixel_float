package dev.fand1l.pixelfloat.overlay

import android.app.Application
import android.provider.Settings
import dev.fand1l.pixelfloat.core.DebugLog
import dev.fand1l.pixelfloat.data.settings.OverlayWindowType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Decides which host actually draws the island.
 *
 * Both services can be bound at once — that is the normal steady state — so something has
 * to arbitrate between the user's preference and what is currently available. Without a
 * named arbiter, "the island silently stopped working" and "the island silently degraded"
 * are the same event.
 */
class OverlayHostRegistry(
    private val application: Application,
    private val log: DebugLog,
) {

    sealed interface Resolution {
        /** [fallback] is true when the preferred host was unavailable. */
        data class Ready(val host: OverlayHost, val fallback: Boolean) : Resolution
        data class Unavailable(val reason: String) : Resolution
    }

    private val _accessibilityHost = MutableStateFlow<OverlayHost?>(null)

    /** Non-null only while the accessibility service is connected. */
    val accessibilityHost: StateFlow<OverlayHost?> = _accessibilityHost.asStateFlow()

    fun setAccessibilityHost(host: OverlayHost?) {
        _accessibilityHost.value = host
        log.i(TAG, if (host == null) "accessibility host cleared" else "accessibility host registered")
    }

    fun canUseAppOverlay(): Boolean = Settings.canDrawOverlays(application)

    fun availableTypes(): Set<OverlayWindowType> = buildSet {
        if (_accessibilityHost.value != null) add(OverlayWindowType.ACCESSIBILITY_OVERLAY)
        if (canUseAppOverlay()) add(OverlayWindowType.APPLICATION_OVERLAY)
    }

    /**
     * A fresh host per call for the app-overlay path (cheap), and the live registered
     * instance for accessibility — never a cached one, because its window token dies with
     * the service connection.
     */
    fun resolve(preferred: OverlayWindowType): Resolution {
        val accessibility = _accessibilityHost.value
        val appOverlayAllowed = canUseAppOverlay()

        return when (preferred) {
            OverlayWindowType.ACCESSIBILITY_OVERLAY -> when {
                accessibility != null -> Resolution.Ready(accessibility, fallback = false)
                appOverlayAllowed -> Resolution.Ready(
                    OverlayHost.AppOverlay(application),
                    fallback = true,
                )
                else -> Resolution.Unavailable(
                    "accessibility service is not connected and SYSTEM_ALERT_WINDOW is not granted"
                )
            }

            OverlayWindowType.APPLICATION_OVERLAY -> when {
                appOverlayAllowed -> Resolution.Ready(
                    OverlayHost.AppOverlay(application),
                    fallback = false,
                )
                accessibility != null -> Resolution.Ready(accessibility, fallback = true)
                else -> Resolution.Unavailable("SYSTEM_ALERT_WINDOW is not granted")
            }
        }
    }

    private companion object {
        const val TAG = "hosts"
    }
}
