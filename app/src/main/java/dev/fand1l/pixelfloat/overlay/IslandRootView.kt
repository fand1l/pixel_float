package dev.fand1l.pixelfloat.overlay

import android.content.Context
import android.widget.FrameLayout

/**
 * The actual window root. It exists as a real class (rather than adding the ComposeView
 * directly) for two reasons that both arrive later:
 *
 *  - onWindowVisibilityChanged is the ONLY signal that another app suppressed our overlay
 *    via HIDE_OVERLAY_WINDOWS; there is no callback for it.
 *  - it is the attachment point for the ViewTree owners and for the back-dispatcher owner
 *    that inline reply needs.
 */
class IslandRootView(context: Context) : FrameLayout(context) {

    var onWindowVisibility: ((Int) -> Unit)? = null

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        onWindowVisibility?.invoke(visibility)
    }
}
