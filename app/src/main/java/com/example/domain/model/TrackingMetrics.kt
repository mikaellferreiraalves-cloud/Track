package com.example.domain.model

data class TrackingMetrics(
    val state: TrackingState = TrackingState.IDLE,
    val sessionId: String? = null,
    val distanceMeters: Double = 0.0,
    val currentSpeedKmh: Float = 0f,
    val averageSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val elapsedTimeMs: Long = 0L,
    val pointCount: Int = 0,
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val currentAltitude: Double? = null,
    val currentAccuracy: Float? = null,
    val errorMessage: String? = null
)
