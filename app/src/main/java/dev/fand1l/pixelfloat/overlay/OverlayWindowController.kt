package dev.fand1l.pixelfloat.overlay

import android.app.Application
import android.graphics.Region
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
import dev.fand1l.pixelfloat.data.settings.PillGeometry
import dev.fand1l.pixelfloat.data.settings.SettingsRepository
import dev.fand1l.pixelfloat.overlay.ui.IslandPill
import dev.fand1l.pixelfloat.theme.IslandTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The only class in the codebase that calls addView / updateViewLayout / removeViewImmediate.
 * Everything here runs on the main thread.
 *
 * Stage 1a scope: one window, one static pill, no notifications, no springs.
 */
@MainThread
class OverlayWindowController(
    private val application: Application,
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

    /**
     * Frames the composition has actually produced since the window was added.
     * If this stays 0 while the window is up, the lifecycle never reached STARTED —
     * the failure mode that produces a blank window with no exception anywhere.
     */
    private val _framesSinceShow = MutableStateFlow(0)
    val framesSinceShow: StateFlow<Int> = _framesSinceShow.asStateFlow()

    private val _cutout = MutableStateFlow<CutoutInfo?>(null)
    val cutout: StateFlow<CutoutInfo?> = _cutout.asStateFlow()

    private val _windowVisibility = MutableStateFlow<Int?>(null)
    val windowVisibility: StateFlow<Int?> = _windowVisibility.asStateFlow()

    fun toggle() {
        if (_isShown.value) hide() else show()
    }

    fun show() = onMain {
        if (root != null) {
            log.i(TAG, "show ignored: window already attached")
            return@onMain
        }
        if (!Settings.canDrawOverlays(application)) {
            log.w(TAG, "show refused: SYSTEM_ALERT_WINDOW not granted")
            return@onMain
        }

        val newHost = OverlayHost.AppOverlay(application)
        val cutoutInfo = CutoutGeometryProvider.read(newHost)
        val layout = OverlayGeometry.compute(cutoutInfo, settings.settings.value.pill)
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
                    IslandPill(
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
            log.w(TAG, "addView failed", error)
            events.record("window_error", "addView: ${error?.javaClass?.simpleName}: ${error?.message}")
            viewOwner.onDestroy()
            return@onMain
        }

        host = newHost
        root = rootView
        owner = viewOwner
        params = layoutParams
        viewOwner.onResume()
        _isShown.value = true

        log.i(TAG, "window added: ${layout.pill}, height ${layout.windowHeightPx}px")
        events.record("window_shown", layout.pill.toString())

        // The collapsed pill rect is known before anything animates, so the touchable
        // region is applied immediately rather than after a settle — otherwise the window
        // would swallow the whole top strip for the duration of the entrance.
        rootView.post { applyTouchableRegion(rootView, layout) }

        // Live geometry: moving a slider in the app moves the pill without a rebuild.
        showScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope ->
            scope.launch {
                settings.settings
                    .map { it.pill }
                    .drop(1)
                    .collect { pill -> applyGeometry(pill) }
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
        _windowVisibility.value = null
        log.i(TAG, "window removed")
        events.record("window_hidden", "")
    }

    private fun applyGeometry(pill: PillGeometry) = onMain {
        val currentHost = host ?: return@onMain
        val rootView = root ?: return@onMain
        val currentParams = params ?: return@onMain

        val cutoutInfo = CutoutGeometryProvider.read(currentHost)
        val layout = OverlayGeometry.compute(cutoutInfo, pill)
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
     * Touch pass-through. Only the pill rect is touchable; everything else in the window —
     * in particular the area beside the pill — must reach the app underneath and must let
     * the status-bar swipe open the shade.
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
        val region = Region(layout.pill.left, layout.pill.top, layout.pill.right, layout.pill.bottom)
        runCatching { surfaceControl.setTouchableRegion(region) }
            .onSuccess { log.i(TAG, "touchable region = ${layout.pill}") }
            .onFailure { log.w(TAG, "setTouchableRegion failed", it) }
    }

    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }

    private companion object {
        const val TAG = "overlay"
    }
}
