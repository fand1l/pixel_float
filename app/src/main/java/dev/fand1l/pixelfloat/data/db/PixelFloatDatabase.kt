package dev.fand1l.pixelfloat.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [OverlayEventEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PixelFloatDatabase : RoomDatabase() {
    abstract fun overlayEventDao(): OverlayEventDao
}
