package dev.fand1l.pixelfloat.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

/**
 * The single DataStore instance for the process.
 *
 * DataStore permits exactly one instance per file per process — creating a second one
 * (for example by also using the `by dataStore` context delegate) throws on the next
 * read. Everything goes through this class.
 */
class SettingsRepository(
    context: Context,
    scope: CoroutineScope,
) {

    private val store: DataStore<AppSettings> = DataStoreFactory.create(
        serializer = AppSettingsSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { AppSettings() },
        scope = scope,
        produceFile = { context.applicationContext.dataStoreFile("settings.json") },
    )

    /**
     * Cached for synchronous UI reads. NOTE: `Eagerly` starts the flow, it does not make
     * the first file read synchronous — for the first few milliseconds after process
     * start this is still the default value. Anything on a notification hot path must use
     * [awaitLoaded] instead of `.value`.
     */
    val settings: StateFlow<AppSettings> = store.data
        .catch { emit(AppSettings()) }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun awaitLoaded(): AppSettings = store.data.first()

    /**
     * Always the in-lambda form: DataStore only guarantees atomicity against a concurrent
     * writer for `updateData { it.copy(...) }`, not for read-then-write-a-snapshot.
     */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.updateData(transform)
    }
}
