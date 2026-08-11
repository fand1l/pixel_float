package dev.fand1l.pixelfloat.overlay

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * The lifecycle a Service does not have.
 *
 * A ComposeView added through WindowManager crashes without ViewTreeLifecycleOwner and
 * ViewTreeSavedStateRegistryOwner, and — less obviously — stopping at CREATED produces a
 * composed but completely blank, frozen window with no exception and no log line, because
 * Compose's frame clock starts paused and only resumes on ON_START. Hence [onResume].
 *
 * One instance per shown window. A registry that reached DESTROYED cannot move back up,
 * and performRestore must not run twice.
 */
class OverlayViewOwner : LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    /** Call before the view is added to the window. */
    fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    /** Call after addView. STARTED is the functional minimum; RESUMED is what we want. */
    fun onResume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    /** Call after the view has been removed. Skipping this leaks one composition per show. */
    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}
