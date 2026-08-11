package dev.fand1l.pixelfloat

import android.app.Application
import android.content.Context
import androidx.room.Room
import dev.fand1l.pixelfloat.core.CrashRecorder
import dev.fand1l.pixelfloat.core.DebugLog
import dev.fand1l.pixelfloat.data.db.EventRepository
import dev.fand1l.pixelfloat.data.db.PixelFloatDatabase
import dev.fand1l.pixelfloat.data.settings.SettingsRepository
import dev.fand1l.pixelfloat.overlay.OverlayWindowController
import dev.fand1l.pixelfloat.service.ServiceConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-written dependency graph, deliberately not Hilt.
 *
 * Hilt works on a NotificationListenerService, but its Gradle plugin has broken on every
 * AGP 9 milestone, and a build failure this project cannot reproduce locally costs far more
 * than the handful of constructor calls below. See docs/ARCHITECTURE.md section 1.2.
 */
class PixelFloatGraph(private val application: Application) {

    /** Process-lifetime scope. CPU-bound work only — file I/O gets Dispatchers.IO. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** DataStore does file writes, fsync and atomic renames; it must not share the CPU pool. */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val debugLog = DebugLog()

    val crashRecorder = CrashRecorder(application)

    val serviceState = ServiceConnectionState()

    val database: PixelFloatDatabase by lazy {
        Room.databaseBuilder(application, PixelFloatDatabase::class.java, "pixelfloat.db")
            // History is disposable; hand-authoring migrations from a phone is the single
            // worst-value task in this project.
            .fallbackToDestructiveMigration(dropAllTables = true)
            // A plain dispatcher, not ioScope.coroutineContext: Room rejects a context
            // that carries a Job.
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    val events: EventRepository by lazy { EventRepository(database.overlayEventDao(), ioScope) }

    val settings: SettingsRepository by lazy { SettingsRepository(application, ioScope) }

    val overlay: OverlayWindowController by lazy {
        OverlayWindowController(application, settings, events, debugLog)
    }

    fun start() {
        crashRecorder.install()
        // Log lines also land in Room, so a crash or a reboot does not erase the evidence.
        debugLog.sink = { line -> events.record("log", "${line.tag}: ${line.message}") }
        debugLog.i(TAG, "process started")
    }

    private companion object {
        const val TAG = "app"
    }
}

/** Every component reaches the graph the same way. */
val Context.graph: PixelFloatGraph
    get() = (applicationContext as PixelFloatApp).graph
