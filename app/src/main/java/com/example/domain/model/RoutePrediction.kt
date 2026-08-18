package com.example.domain.model

data class RoutePrediction(
    val probableDestination: String,
    val predictedDistanceMeters: Double,
    val estimatedDurationMinutes: Int,
    val probabilityPercentage: Int, // e.g. 68%
    val rationale: String,
    val typicalDepartureWindow: String? = null
)
