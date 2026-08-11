package dev.fand1l.pixelfloat.data.db

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Writes go through a process-lifetime scope, never a service-scoped one, so a record
 * survives the writing component being unbound (which happens on every APK update).
 */
class EventRepository(
    private val dao: OverlayEventDao,
    private val scope: CoroutineScope,
) {

    fun observeRecent(limit: Int = 200): Flow<List<OverlayEventEntity>> = dao.observeRecent(limit)

    fun observeCount(): Flow<Int> = dao.observeCount()

    fun record(kind: String, detail: String) {
        scope.launch {
            runCatching {
                dao.insert(
                    OverlayEventEntity(
                        timestamp = System.currentTimeMillis(),
                        kind = kind,
                        detail = detail,
                    )
                )
                dao.trimTo(KEEP)
            }
        }
    }

    fun clear() {
        scope.launch { runCatching { dao.clear() } }
    }

    private companion object {
        const val KEEP = 500
    }
}
