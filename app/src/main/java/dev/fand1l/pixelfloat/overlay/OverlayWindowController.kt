package dev.fand1l.pixelfloat.overlay

import android.graphics.Rect
import android.graphics.Region
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.fand1l.pixelfloat.core.DebugLog
import dev.fand1l.pixelfloat.data.db.EventRepository
import dev.fand1l.pixelfloat.data.settings.AppSettings
import dev.fand1l.pixelfloat.data.settings.OverlayWindowType
import dev.fand1l.pixelfloat.data.settings.SettingsRepository
import dev.fand1l.pixelfloat.overlay.ui.IslandPills
import dev.fand1l.pixelfloat.theme.IslandTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The only class in the codebase that calls addView / updateViewLayout / removeViewImmediate.
 * Everything here runs on the main thread.
 *
 * Stage 1b scope: one window, two static pills flanking the cutout, either window type.
 * No notifications, no springs, no gestures beyond tap-to-hide.
 */
@MainThread
class OverlayWindowController(
    private val hosts: OverlayHostRegistry,
    private val settings: SettingsRepository,
    private val events: EventRepository,
    private val log: DebugLog,
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var host: OverlayHost? = null
    private var root: IslandRootView? = null
    private var owner: OverlayViewOwner? = null
    private var params: WindowManager.LayoutParams? = null
    private var showScope: CoroutineScope? = null

    /** Read by the composition; written here when the geometry changes. */
    private val layoutState = mutableStateOf<IslandLayout?>(null)

    private val _isShown = MutableStateFlow(false)
    val isShown: StateFlow<Boolean> = _isShown.asStateFlow()

    /** Which host is actually drawing right now, which may not be the preferred one. */
    private val _activeType = MutableStateFlow<OverlayWindowType?>(null)
    val activeType: StateFlow<OverlayWindowType?> = _activeType.asStateFlow()

    private val _usingFallbackHost = MutableStateFlow(false)
    val usingFallbackHost: StateFlow<Boolean> = _usingFallbackHost.asStateFlow()

    /**
     * Frames the composition has actually produced since the window was added. If this
     * stays 0 while the window is attached, the lifecycle never reached STARTED — the
     * failure mode that produces a blank window with no exception anywhere.
     */
    private val _framesSinceShow = MutableStateFlow(0)
    val framesSinceShow: StateFlow<Int> = _framesSinceShow.asStateFlow()

    private val _cutout = MutableStateFlow<CutoutInfo?>(null)
    val cutout: StateFlow<CutoutInfo?> = _cutout.asStateFlow()

    private val _windowVisibility = MutableStateFlow<Int?>(null)
    val windowVisibility: StateFlow<Int?> = _windowVisibility.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun toggle() {
        if (_isShown.value) hide() else show()
    }

    fun show() = onMain {
        if (root != null) {
            log.i(TAG, "show ignored: window already attached")
            return@onMain
        }

        val preferred = settings.settings.value.overlayWindowType
        val resolution = hosts.resolve(preferred)
        if (resolution is OverlayHostRegistry.Resolution.Unavailable) {
            log.w(TAG, "show refused: ${resolution.reason}")
            _lastError.value = resolution.reason
            return@onMain
        }
        val ready = resolution as OverlayHostRegistry.Resolution.Ready
        val newHost = ready.host
        if (ready.fallback) {
            log.w(TAG, "preferred host $preferred unavailable, falling back to ${newHost.type}")
        }

        val cutoutInfo = CutoutGeometryProvider.read(newHost)
        val layout = OverlayGeometry.compute(cutoutInfo, settings.settings.value.island)
        val layoutParams = OverlayLayoutParams.create(newHost, layout.windowHeightPx)

        _cutout.value = cutoutInfo
        layoutState.value = layout
        _framesSinceShow.value = 0

        val viewOwner = OverlayViewOwner().apply { onCreate() }
        val rootView = IslandRootView(newHost.windowContext).apply {
            onWindowVisibility = { visibility ->
                _windowVisibility.value = visibility
                if (visibility != View.VISIBLE) {
                    // No callback exists for HIDE_OVERLAY_WINDOWS; this is the only signal.
                    log.w(TAG, "window visibility = $visibility (suppressed by another app?)")
                }
            }
        }

        // The ViewTree owners MUST be set before addView: the composition is created
        // synchronously during attach, and a missing owner throws there.
        rootView.setViewTreeLifecycleOwner(viewOwner)
        rootView.setViewTreeSavedStateRegistryOwner(viewOwner)

        val composeView = ComposeView(newHost.windowContext).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(viewOwner.lifecycle)
            )
            setContent {
                IslandTheme {
                    IslandPills(
                        layout = layoutState.value,
                        onFrame = { _framesSinceShow.value += 1 },
                        onTap = { hide() },
                    )
                }
            }
        }
        rootView.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val added = runCatching { newHost.windowManager.addView(rootView, layoutParams) }
        if (added.isFailure) {
            val error = added.exceptionOrNull()
            log.w(TAG, "addView failed on ${newHost.type}", error)
            _lastError.value = "addView on ${newHost.type}: ${error?.javaClass?.simpleName}"
            events.record("window_error", "addView ${newHost.type}: ${error?.message}")
            viewOwner.onDestroy()
            return@onMain
        }

        host = newHost
        root = rootView
        owner = viewOwner
        params = layoutParams
        viewOwner.onResume()
        _isShown.value = true
        _activeType.value = newHost.type
        _usingFallbackHost.value = ready.fallback
        _lastError.value = null

        log.i(TAG, "window added on ${newHost.type}: L${layout.left} R${layout.right}")
        events.record("window_shown", "${newHost.type} L${layout.left} R${layout.right}")

        // The pill rects are known before anything animates, so the touchable region is
        // applied immediately rather than after a settle — otherwise the window would
        // swallow the whole top strip every time it appears.
        rootView.post { applyTouchableRegion(rootView, layout) }

        showScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope ->
            // Live geometry: moving a slider in the app moves the pills without a rebuild.
            scope.launch {
                settings.settings
                    .map { it.island }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { island -> applyGeometry(island) }
            }
            // Switching the window type needs a different window, not a relayout.
            scope.launch {
                settings.settings
                    .map { it.overlayWindowType }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { restart() }
            }
        }
    }

    fun hide() = onMain {
        val rootView = root ?: return@onMain
        showScope?.cancel()
        showScope = null

        runCatching { host?.windowManager?.removeViewImmediate(rootView) }
            .onFailure { log.w(TAG, "removeViewImmediate failed", it) }

        owner?.onDestroy()
        root = null
        owner = null
        host = null
        params = null
        layoutState.value = null
        _isShown.value = false
        _activeType.value = null
        _usingFallbackHost.value = false
        _windowVisibility.value = null
        log.i(TAG, "window removed")
        events.record("window_hidden", "")
    }

    /**
     * The host that owns our window token went away (the accessibility service was
     * unbound — which happens on every APK update). The window is already gone from the
     * user's point of view; drop our side of it rather than leaving a dead reference that
     * would throw on the next removeViewImmediate.
     */
    fun onHostLost(reason: String) = onMain {
        if (root == null) return@onMain
        log.w(TAG, "host lost ($reason) — dropping the window")
        hide()
    }

    private fun restart() = onMain {
        if (root == null) return@onMain
        log.i(TAG, "window type changed — recreating the window")
        hide()
        show()
    }

    private fun applyGeometry(island: dev.fand1l.pixelfloat.data.settings.IslandGeometry) = onMain {
        val currentHost = host ?: return@onMain
        val rootView = root ?: return@onMain
        val currentParams = params ?: return@onMain

        val cutoutInfo = CutoutGeometryProvider.read(currentHost)
        val layout = OverlayGeometry.compute(cutoutInfo, island)
        _cutout.value = cutoutInfo
        layoutState.value = layout

        if (currentParams.height != layout.windowHeightPx) {
            currentParams.height = layout.windowHeightPx
            runCatching { currentHost.windowManager.updateViewLayout(rootView, currentParams) }
                .onFailure { log.w(TAG, "updateViewLayout failed", it) }
        }
        rootView.post { applyTouchableRegion(rootView, layout) }
    }

    /**
     * Touch pass-through. Only the pills are touchable; the gap between them — the part
     * over the camera cutout — must reach the app underneath and must let the status-bar
     * swipe open the shade. This is the stage 1b measurement.
     *
     * AttachedSurfaceControl.setTouchableRegion is the public replacement for the old
     * ViewTreeObserver.InternalInsetsInfo trick, which is @hide and blocked at targetSdk 37.
     */
    private fun applyTouchableRegion(rootView: View, layout: IslandLayout) {
        val surfaceControl = rootView.rootSurfaceControl
        if (surfaceControl == null) {
            log.w(TAG, "rootSurfaceControl is null — touchable region NOT applied")
            return
        }
        val region = Region(layout.left.left, layout.left.top, layout.left.right, layout.left.bottom)
        if (layout.rightPillVisible) {
            region.union(
                Rect(layout.right.left, layout.right.top, layout.right.right, layout.right.bottom)
            )
        }
        runCatching { surfaceControl.setTouchableRegion(region) }
            .onSuccess { log.i(TAG, "touchable region = L${layout.left} R${layout.right} (gap ${layout.gap} excluded)") }
            .onFailure { log.w(TAG, "setTouchableRegion failed", it) }
    }

    /**
     * Reads what the display reports without adding a window: currentWindowMetrics works
     * from a window context with no attached view, which is also what will let the
     * coordinator decide whether to show at all before the window exists.
     */
    fun refreshCutout() = onMain {
        val resolution = hosts.resolve(settings.settings.value.overlayWindowType)
        if (resolution is OverlayHostRegistry.Resolution.Ready) {
            _cutout.value = CutoutGeometryProvider.read(resolution.host)
        }
    }

    /** Applied on the next show; the current window keeps its host until it is recreated. */
    fun settingsSnapshot(): AppSettings = settings.settings.value

    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }

    private companion object {
        const val TAG = "overlay"
    }
}
