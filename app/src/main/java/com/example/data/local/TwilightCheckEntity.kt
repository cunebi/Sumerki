package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "twilight_checks")
data class TwilightCheckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val dateLabel: String,
    val locationName: String,
    val latitude: Double?,
    val longitude: Double?,
    val attemptsUsed: Int,
    val maxAttempts: Int,
    val success: Boolean,
    val alarmSet: Boolean,
    val twilightTime: String?,
    val twilightStage: String?,
    val astronomicalTime: String? = null,
    val nauticalTime: String? = null,
    val civilTime: String? = null,
    val sunriseTime: String? = null,
    val message: String,
    val isScheduledMidnight: Boolean
)
