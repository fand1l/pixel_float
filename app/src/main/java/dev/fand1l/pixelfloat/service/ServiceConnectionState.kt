package dev.fand1l.pixelfloat.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether each platform service is currently bound.
 *
 * This is deliberately visible in the UI: both services are unbound by the system on every
 * APK update, and the owner installs a new APK on every build. A silent no-op is the worst
 * possible failure mode here, so the app has to be able to say "I am not connected".
 */
class ServiceConnectionState {

    private val _listenerConnected = MutableStateFlow(false)
    val listenerConnected: StateFlow<Boolean> = _listenerConnected.asStateFlow()

    private val _accessibilityConnected = MutableStateFlow(false)
    val accessibilityConnected: StateFlow<Boolean> = _accessibilityConnected.asStateFlow()

    fun setListenerConnected(connected: Boolean) {
        _listenerConnected.value = connected
    }

    fun setAccessibilityConnected(connected: Boolean) {
        _accessibilityConnected.value = connected
    }
}
