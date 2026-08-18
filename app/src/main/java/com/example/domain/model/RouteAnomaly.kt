package com.example.domain.model

data class RouteAnomaly(
    val id: String,
    val sessionId: String,
    val title: String,
    val description: String,
    val confidencePercentage: Int, // e.g. 82%
    val anomalyType: AnomalyType,
    val timestamp: Long = System.currentTimeMillis()
)

enum class AnomalyType {
    UNUSUAL_SPEED,
    UNUSUAL_ROUTE,
    UNUSUAL_TIME,
    UNUSUAL_DISTANCE,
    UNFAMILIAR_LOCATION
}
