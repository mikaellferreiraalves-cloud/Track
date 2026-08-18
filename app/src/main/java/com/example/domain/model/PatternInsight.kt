package com.example.domain.model

data class PatternInsight(
    val title: String,
    val description: String,
    val category: InsightCategory,
    val confidence: Float, // 0.0 to 1.0
    val count: Int = 0,
    val estimatedWeeklyFrequency: Int = 0
)

enum class InsightCategory {
    DEPARTURE_TIME,
    RECURRING_ROUTE,
    FREQUENT_SPOT,
    AVERAGE_SPEED,
    DISTANCE_HABIT
}
