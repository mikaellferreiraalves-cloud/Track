package com.example.domain.tracking

import com.example.data.database.GeofenceArea

data class GeofenceTransition(
    val geofence: GeofenceArea,
    val transitionType: TransitionType,
    val distanceMeters: Double
)

enum class TransitionType {
    ENTERED,
    EXITED
}

object GeofenceManager {

    /**
     * Evaluates current location against a list of geofences.
     * Returns transitions (ENTERED or EXITED) that occurred.
     */
    fun evaluateTransitions(
        latitude: Double,
        longitude: Double,
        geofences: List<GeofenceArea>
    ): List<GeofenceTransition> {
        val transitions = mutableListOf<GeofenceTransition>()

        for (geo in geofences) {
            if (!geo.isEnabled) continue

            val distance = GpsFilter.calculateDistanceMeters(
                geo.latitude,
                geo.longitude,
                latitude,
                longitude
            )
            val currentlyInside = distance <= geo.radiusMeters

            if (currentlyInside != geo.isInside) {
                val transitionType = if (currentlyInside) TransitionType.ENTERED else TransitionType.EXITED
                transitions.add(
                    GeofenceTransition(
                        geofence = geo.copy(isInside = currentlyInside, lastTriggerTime = System.currentTimeMillis()),
                        transitionType = transitionType,
                        distanceMeters = distance
                    )
                )
            }
        }

        return transitions
    }
}
