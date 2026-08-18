package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracking_sessions")
data class TrackingSession(
    @PrimaryKey
    val id: String,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val distanceMeters: Double = 0.0,
    val averageSpeed: Double? = null,
    val maxSpeed: Double? = null,
    val pointCount: Int = 0,
    val title: String? = null
)
