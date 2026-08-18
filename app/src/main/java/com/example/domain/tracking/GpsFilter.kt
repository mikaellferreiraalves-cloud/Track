package com.example.domain.tracking

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GpsFilterConfig(
    val maxAccuracyMeters: Float = 40.0f,
    val minDistanceMeters: Double = 2.0,
    val minTimeSeconds: Long = 1L,
    val maxFeasibleSpeedMps: Double = 60.0 // ~216 km/h max realistic ground speed
)

object GpsFilter {

    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates the great-circle distance between two points using the Haversine formula.
     */
    fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Verifies if a location point meets validity constraints.
     */
    fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        if (lat < -90.0 || lat > 90.0) return false
        if (lon < -180.0 || lon > 180.0) return false
        // (0.0, 0.0) is null island off the coast of Africa, almost always a GPS initialization error
        if (lat == 0.0 && lon == 0.0) return false
        return true
    }

    /**
     * Checks if accuracy is acceptable.
     */
    fun isAccuracyAcceptable(accuracy: Float?, maxAccuracyMeters: Float = 40f): Boolean {
        if (accuracy == null) return false
        if (accuracy <= 0f) return false
        return accuracy <= maxAccuracyMeters
    }

    /**
     * Validates if a new coordinate is a valid movement and not an impossible jump or redundant duplicate.
     */
    fun shouldRecordPoint(
        prevLat: Double,
        prevLon: Double,
        prevTimestamp: Long,
        newLat: Double,
        newLon: Double,
        newAccuracy: Float?,
        newTimestamp: Long,
        config: GpsFilterConfig = GpsFilterConfig()
    ): FilterResult {
        if (!isValidCoordinate(newLat, newLon)) {
            return FilterResult.Rejected("Coordenada inválida")
        }

        if (!isAccuracyAcceptable(newAccuracy, config.maxAccuracyMeters)) {
            return FilterResult.Rejected("Precisão insuficiente: ${newAccuracy?.toInt()}m (máx: ${config.maxAccuracyMeters.toInt()}m)")
        }

        val distance = calculateDistanceMeters(prevLat, prevLon, newLat, newLon)
        val timeDiffSeconds = ((newTimestamp - prevTimestamp).coerceAtLeast(1)) / 1000.0

        // Duplicate/Stationary check: negligible distance in very short time
        if (distance < config.minDistanceMeters && timeDiffSeconds < config.minTimeSeconds) {
            return FilterResult.Rejected("Ponto duplicado ou parado ($distance m em $timeDiffSeconds s)")
        }

        // Impossible jump check
        val impliedSpeedMps = distance / timeDiffSeconds
        if (impliedSpeedMps > config.maxFeasibleSpeedMps) {
            return FilterResult.Rejected("Salto GPS impossível: ${(impliedSpeedMps * 3.6).toInt()} km/h")
        }

        return FilterResult.Accepted(distance, impliedSpeedMps)
    }
}

sealed class FilterResult {
    data class Accepted(val distanceMeters: Double, val speedMps: Double) : FilterResult()
    data class Rejected(val reason: String) : FilterResult()
}
