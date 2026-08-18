package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "geofence_areas")
data class GeofenceArea(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = 200.0,
    val isEnabled: Boolean = true,
    val isInside: Boolean = false,
    val lastTriggerTime: Long? = null
)
