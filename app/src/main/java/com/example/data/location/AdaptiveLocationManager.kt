package com.example.data.location

import com.example.domain.tracking.GpsFilter
import com.example.domain.tracking.GpsFilterConfig

enum class MovementState {
    STATIONARY,
    WALKING,
    RUNNING_OR_CYCLING,
    VEHICLE_FAST
}

class AdaptiveLocationManager(
    var filterConfig: GpsFilterConfig = GpsFilterConfig()
) {
    private var lastRecordedLat: Double? = null
    private var lastRecordedLon: Double? = null
    private var lastRecordedTimestamp: Long = 0L
    private var consecutiveStationaryCount: Int = 0

    fun determineMovementState(speedKmh: Float): MovementState {
        return when {
            speedKmh < 1.5f -> MovementState.STATIONARY
            speedKmh < 12.0f -> MovementState.WALKING
            speedKmh < 45.0f -> MovementState.RUNNING_OR_CYCLING
            else -> MovementState.VEHICLE_FAST
        }
    }

    /**
     * Determines optimal GPS polling interval in milliseconds based on movement state.
     */
    fun getOptimalIntervalMs(state: MovementState): Long {
        return when (state) {
            MovementState.STATIONARY -> 10000L // 10s battery-saving interval when not moving
            MovementState.WALKING -> 4000L    // 4s for walking
            MovementState.RUNNING_OR_CYCLING -> 2500L // 2.5s for cycling/running
            MovementState.VEHICLE_FAST -> 1200L      // 1.2s for high-speed tracking
        }
    }

    /**
     * Evaluates whether an incoming location fix should be recorded to database.
     */
    fun processLocationFix(
        latitude: Double,
        longitude: Double,
        accuracy: Float?,
        speed: Float?,
        timestamp: Long
    ): AdaptiveFixDecision {
        val prevLat = lastRecordedLat
        val prevLon = lastRecordedLon

        if (prevLat == null || prevLon == null) {
            // First point ever: check basic validity
            if (GpsFilter.isValidCoordinate(latitude, longitude) &&
                GpsFilter.isAccuracyAcceptable(accuracy, filterConfig.maxAccuracyMeters)
            ) {
                lastRecordedLat = latitude
                lastRecordedLon = longitude
                lastRecordedTimestamp = timestamp
                return AdaptiveFixDecision.Accept(
                    distanceMeters = 0.0,
                    speedMps = (speed ?: 0f).toDouble(),
                    movementState = determineMovementState((speed ?: 0f) * 3.6f)
                )
            } else {
                return AdaptiveFixDecision.Reject("Precisão inicial insuficiente (${accuracy?.toInt()}m)")
            }
        }

        val result = GpsFilter.shouldRecordPoint(
            prevLat = prevLat,
            prevLon = prevLon,
            prevTimestamp = lastRecordedTimestamp,
            newLat = latitude,
            newLon = longitude,
            newAccuracy = accuracy,
            newTimestamp = timestamp,
            config = filterConfig
        )

        return when (result) {
            is com.example.domain.tracking.FilterResult.Accepted -> {
                consecutiveStationaryCount = 0
                lastRecordedLat = latitude
                lastRecordedLon = longitude
                lastRecordedTimestamp = timestamp
                val speedKmh = ((speed ?: result.speedMps.toFloat()) * 3.6f)
                AdaptiveFixDecision.Accept(
                    distanceMeters = result.distanceMeters,
                    speedMps = result.speedMps,
                    movementState = determineMovementState(speedKmh)
                )
            }
            is com.example.domain.tracking.FilterResult.Rejected -> {
                consecutiveStationaryCount++
                AdaptiveFixDecision.Reject(result.reason)
            }
        }
    }

    fun reset() {
        lastRecordedLat = null
        lastRecordedLon = null
        lastRecordedTimestamp = 0L
        consecutiveStationaryCount = 0
    }
}

sealed class AdaptiveFixDecision {
    data class Accept(
        val distanceMeters: Double,
        val speedMps: Double,
        val movementState: MovementState
    ) : AdaptiveFixDecision()

    data class Reject(val reason: String) : AdaptiveFixDecision()
}
