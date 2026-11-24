package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TwilightCheckDao {
    @Query("SELECT * FROM twilight_checks ORDER BY timestampMillis DESC LIMIT 100")
    fun observeAllChecks(): Flow<List<TwilightCheckEntity>>

    @Query("SELECT * FROM twilight_checks ORDER BY timestampMillis DESC LIMIT 1")
    fun observeLatestCheck(): Flow<TwilightCheckEntity?>

    @Query("SELECT * FROM twilight_checks WHERE alarmSet = 1 ORDER BY timestampMillis DESC LIMIT 1")
    suspend fun getLatestSetAlarmCheck(): TwilightCheckEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheck(entity: TwilightCheckEntity)

    @Query("DELETE FROM twilight_checks")
    suspend fun clearAll()
}
