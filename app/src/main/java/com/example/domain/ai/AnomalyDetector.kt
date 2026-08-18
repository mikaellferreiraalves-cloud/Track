package com.example.domain.ai

import com.example.data.database.TrackingSession
import com.example.domain.model.AnomalyType
import com.example.domain.model.RouteAnomaly
import java.util.Calendar
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

object AnomalyDetector {

    /**
     * Checks if a target session deviates significantly from historical sessions.
     * Generates a list of detected anomalies with statistical confidence scores.
     */
    fun detectAnomalies(
        targetSession: TrackingSession,
        historicalSessions: List<TrackingSession>
    ): List<RouteAnomaly> {
        val otherSessions = historicalSessions.filter { it.id != targetSession.id && it.distanceMeters > 50 }
        if (otherSessions.size < 3) {
            return emptyList()
        }

        val anomalies = mutableListOf<RouteAnomaly>()

        // 1. Average Speed Anomaly Check (Z-score test)
        val speeds = otherSessions.mapNotNull { it.averageSpeed }.filter { it > 0 }
        if (speeds.size >= 3 && targetSession.averageSpeed != null && targetSession.averageSpeed > 0) {
            val meanSpeed = speeds.average()
            val variance = speeds.map { (it - meanSpeed) * (it - meanSpeed) }.average()
            val stdDev = sqrt(variance).coerceAtLeast(0.5)
            val zScore = (targetSession.averageSpeed - meanSpeed) / stdDev

            if (abs(zScore) >= 2.0) {
                val isFaster = zScore > 0
                val confidence = ((abs(zScore) / 4.0).coerceIn(0.5, 0.95) * 100).toInt()
                val speedKmh = targetSession.averageSpeed * 3.6
                val meanSpeedKmh = meanSpeed * 3.6

                anomalies.add(
                    RouteAnomaly(
                        id = UUID.randomUUID().toString(),
                        sessionId = targetSession.id,
                        title = if (isFaster) "Velocidade média muito acima do padrão" else "Velocidade média incomumente baixa",
                        description = String.format(
                            "Velocidade registrada (%.1f km/h) desviou significativamente da média habitual (%.1f km/h).",
                            speedKmh, meanSpeedKmh
                        ),
                        confidencePercentage = confidence,
                        anomalyType = AnomalyType.UNUSUAL_SPEED,
                        timestamp = targetSession.startTime
                    )
                )
            }
        }

        // 2. Departure Time Anomaly Check
        val targetCal = Calendar.getInstance().apply { timeInMillis = targetSession.startTime }
        val targetHour = targetCal.get(Calendar.HOUR_OF_DAY)
        val historicalHours = otherSessions.map {
            Calendar.getInstance().apply { timeInMillis = it.startTime }.get(Calendar.HOUR_OF_DAY)
        }

        val hourCounts = historicalHours.count { abs(it - targetHour) <= 1 || abs(it - targetHour) >= 23 }
        if (hourCounts == 0 && otherSessions.size >= 5) {
            anomalies.add(
                RouteAnomaly(
                    id = UUID.randomUUID().toString(),
                    sessionId = targetSession.id,
                    title = "Horário de deslocamento atípico",
                    description = "Início às $targetHour:00 difere dos horários comuns registrados anteriormente.",
                    confidencePercentage = 78,
                    anomalyType = AnomalyType.UNUSUAL_TIME,
                    timestamp = targetSession.startTime
                )
            )
        }

        // 3. Distance Anomaly Check
        val distances = otherSessions.map { it.distanceMeters }
        val meanDistance = distances.average()
        val distVariance = distances.map { (it - meanDistance) * (it - meanDistance) }.average()
        val distStdDev = sqrt(distVariance).coerceAtLeast(100.0)
        val distZ = (targetSession.distanceMeters - meanDistance) / distStdDev

        if (abs(distZ) >= 2.2) {
            val conf = ((abs(distZ) / 4.5).coerceIn(0.6, 0.92) * 100).toInt()
            val distKm = targetSession.distanceMeters / 1000.0
            val meanDistKm = meanDistance / 1000.0
            anomalies.add(
                RouteAnomaly(
                    id = UUID.randomUUID().toString(),
                    sessionId = targetSession.id,
                    title = "Distância percorrida incomum",
                    description = String.format(
                        "Extensão do trajeto (%.1f km) divergiu da média padrão (%.1f km).",
                        distKm, meanDistKm
                    ),
                    confidencePercentage = conf,
                    anomalyType = AnomalyType.UNUSUAL_DISTANCE,
                    timestamp = targetSession.startTime
                )
            )
        }

        return anomalies
    }
}
