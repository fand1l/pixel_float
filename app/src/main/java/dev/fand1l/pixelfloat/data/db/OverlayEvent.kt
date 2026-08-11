package dev.fand1l.pixelfloat.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Diagnostics that outlive the process.
 *
 * In stage 1a this table is also what proves the Room + KSP toolchain works at runtime
 * and not merely at compile time. The notification history table (stage 6) is a separate
 * entity — this one is deliberately not reused for it.
 */
@Entity(tableName = "overlay_event")
data class OverlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val kind: String,
    val detail: String,
)

@Dao
interface OverlayEventDao {

    @Query("SELECT * FROM overlay_event ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<OverlayEventEntity>>

    @Query("SELECT COUNT(*) FROM overlay_event")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(event: OverlayEventEntity): Long

    /** Keeps the table bounded without WorkManager: called right after every insert. */
    @Query(
        "DELETE FROM overlay_event WHERE id NOT IN " +
            "(SELECT id FROM overlay_event ORDER BY id DESC LIMIT :keep)"
    )
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM overlay_event")
    suspend fun clear()
}
