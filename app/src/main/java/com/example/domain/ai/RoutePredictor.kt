package com.example.domain.ai

import com.example.data.database.LocationPoint
import com.example.data.database.TrackingSession
import com.example.domain.model.RoutePrediction
import com.example.domain.tracking.GpsFilter
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object RoutePredictor {

    /**
     * Predicts probable next destination and route parameters based on historical sessions.
     */
    fun predictNextRoute(
        currentLatitude: Double?,
        currentLongitude: Double?,
        historicalSessions: List<TrackingSession>,
        allPoints: List<LocationPoint>
    ): RoutePrediction? {
        if (historicalSessions.size < 3) {
            return null
        }

        val currentCal = Calendar.getInstance()
        val currentHour = currentCal.get(Calendar.HOUR_OF_DAY)
        val currentDayOfWeek = currentCal.get(Calendar.DAY_OF_WEEK)

        val pointsBySession = allPoints.groupBy { it.sessionId }

        // Find sessions that started around this time (+/- 2 hours) or same day of week
        val candidateSessions = historicalSessions.filter { session ->
            val cal = Calendar.getInstance().apply { timeInMillis = session.startTime }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val day = cal.get(Calendar.DAY_OF_WEEK)
            val hourDiff = abs(hour - currentHour).let { if (it > 12) 24 - it else it }

            hourDiff <= 2 || day == currentDayOfWeek
        }

        if (candidateSessions.isEmpty()) {
            return null
        }

        // Check if start location matches
        var bestSession: TrackingSession? = null
        var bestMatchScore = 0

        for (session in candidateSessions) {
            var score = 1
            val pts = pointsBySession[session.id] ?: emptyList()
            if (pts.isNotEmpty() && currentLatitude != null && currentLongitude != null) {
                val startDist = GpsFilter.calculateDistanceMeters(
                    pts.first().latitude, pts.first().longitude,
                    currentLatitude, currentLongitude
                )
                if (startDist < 500.0) {
                    score += 3
                }
            }

            val cal = Calendar.getInstance().apply { timeInMillis = session.startTime }
            if (abs(cal.get(Calendar.HOUR_OF_DAY) - currentHour) <= 1) {
                score += 2
            }
            if (cal.get(Calendar.DAY_OF_WEEK) == currentDayOfWeek) {
                score += 2
            }

            if (score > bestMatchScore) {
                bestMatchScore = score
                bestSession = session
            }
        }

        val session = bestSession ?: candidateSessions.first()
        val probability = ((bestMatchScore.toFloat() / 8.0f).coerceIn(0.45f, 0.85f) * 100).roundToInt()

        val durationMinutes = if (session.endTime != null && session.endTime > session.startTime) {
            ((session.endTime - session.startTime) / 60000).toInt().coerceAtLeast(5)
        } else {
            ((session.distanceMeters / 1000.0) / 25.0 * 60.0).toInt().coerceAtLeast(10) // fallback at 25km/h
        }

        val destinationName = session.title ?: "Destino Habitual (${String.format(Locale.getDefault(), "%.1f km", session.distanceMeters / 1000.0)})"
        val nextHour = (currentHour + 1) % 24
        val timeWindow = String.format(Locale.getDefault(), "%02d:00 - %02d:00", currentHour, nextHour)

        return RoutePrediction(
            probableDestination = destinationName,
            predictedDistanceMeters = session.distanceMeters,
            estimatedDurationMinutes = durationMinutes,
            probabilityPercentage = probability,
            rationale = "Com base no dia da semana e horário habitual de deslocamento (${timeWindow}).",
            typicalDepartureWindow = timeWindow
        )
    }
}
