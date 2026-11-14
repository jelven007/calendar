package com.tiandili.calendar.data.backup

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

// Room commits this intent alongside an import. DataStore can replay it after process death.
@Entity(tableName = "pending_preferences")
data class PendingPreferencesEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
)

@Dao
interface LocalDataDao {
    @Upsert
    suspend fun savePendingPreferences(preferences: PendingPreferencesEntity)

    @Query("SELECT * FROM pending_preferences WHERE id = 1")
    suspend fun getPendingPreferences(): PendingPreferencesEntity?

    @Query("DELETE FROM pending_preferences WHERE id = 1")
    suspend fun clearPendingPreferences()
}
