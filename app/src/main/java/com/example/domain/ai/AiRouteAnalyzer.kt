package com.example.domain.ai

import com.example.data.database.LocationPoint
import com.example.data.database.TrackingSession
import com.example.domain.model.InsightCategory
import com.example.domain.model.PatternInsight
import com.example.domain.tracking.GpsFilter
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

object AiRouteAnalyzer {

    /**
     * Analyzes historical tracking sessions and location points to extract recurring patterns.
     */
    fun analyzeSessions(
        sessions: List<TrackingSession>,
        allPoints: List<LocationPoint>
    ): List<PatternInsight> {
        if (sessions.isEmpty()) {
            return emptyList()
        }

        val insights = mutableListOf<PatternInsight>()

        // 1. Departure Time Clustering
        val hourFrequencies = IntArray(24)
        for (session in sessions) {
            val cal = Calendar.getInstance().apply { timeInMillis = session.startTime }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            hourFrequencies[hour]++
        }

        var peakHour = 0
        var maxCount = 0
        for (i in hourFrequencies.indices) {
            if (hourFrequencies[i] > maxCount) {
                maxCount = hourFrequencies[i]
                peakHour = i
            }
        }

        if (maxCount >= 2) {
            val confidence = (maxCount.toFloat() / sessions.size.toFloat()).coerceIn(0.4f, 0.95f)
            val nextHour = (peakHour + 1) % 24
            val formattedRange = String.format(Locale.getDefault(), "%02d:00 e %02d:00", peakHour, nextHour)
            val weeklyEstimate = ((maxCount.toFloat() / sessions.size.toFloat()) * 5).roundToInt().coerceAtLeast(1)

            insights.add(
                PatternInsight(
                    title = "Horário frequente de saída",
                    description = "Você costuma iniciar deslocamentos entre $formattedRange.",
                    category = InsightCategory.DEPARTURE_TIME,
                    confidence = confidence,
                    count = maxCount,
                    estimatedWeeklyFrequency = weeklyEstimate
                )
            )
        }

        // 2. Average Speeds and Distance Habit
        val validSessions = sessions.filter { it.distanceMeters > 50 }
        if (validSessions.isNotEmpty()) {
            val avgDistanceKm = validSessions.map { it.distanceMeters / 1000.0 }.average()
            val avgSpeedList = validSessions.mapNotNull { it.averageSpeed }.filter { it > 0 }

            if (avgSpeedList.isNotEmpty()) {
                val overallAvgSpeed = avgSpeedList.average()
                val speedConfidence = (validSessions.size.toFloat() / (validSessions.size + 3)).coerceIn(0.5f, 0.90f)
                val formattedSpeed = String.format(Locale.getDefault(), "%.1f km/h", overallAvgSpeed * 3.6)

                insights.add(
                    PatternInsight(
                        title = "Velocidade média habitual",
                        description = "Sua velocidade média consolidada em trajetos é de aproximadamente $formattedSpeed.",
                        category = InsightCategory.AVERAGE_SPEED,
                        confidence = speedConfidence,
                        count = validSessions.size
                    )
                )
            }

            val distanceConfidence = (validSessions.size.toFloat() / (validSessions.size + 2)).coerceIn(0.5f, 0.92f)
            val formattedDist = String.format(Locale.getDefault(), "%.1f km", avgDistanceKm)
            insights.add(
                PatternInsight(
                    title = "Extensão média de trajeto",
                    description = "A distância média percorrida por trajeto gravado é de $formattedDist.",
                    category = InsightCategory.DISTANCE_HABIT,
                    confidence = distanceConfidence,
                    count = validSessions.size
                )
            )
        }

        // 3. Recurring Route Detection (clustering start & end points)
        val routeClusters = detectRouteClusters(sessions, allPoints)
        for (cluster in routeClusters) {
            insights.add(cluster)
        }

        return insights
    }

    private fun detectRouteClusters(
        sessions: List<TrackingSession>,
        allPoints: List<LocationPoint>
    ): List<PatternInsight> {
        val pointsBySession = allPoints.groupBy { it.sessionId }
        val clusterList = mutableListOf<PatternInsight>()

        val sessionEndpoints = mutableListOf<Triple<TrackingSession, LocationPoint, LocationPoint>>()
        for (session in sessions) {
            val pts = pointsBySession[session.id] ?: emptyList()
            if (pts.size >= 2) {
                sessionEndpoints.add(Triple(session, pts.first(), pts.last()))
            }
        }

        if (sessionEndpoints.size < 2) return emptyList()

        // Cluster by start and end similarity (< 400 meters)
        val matchedGroups = mutableListOf<MutableList<TrackingSession>>()
        val visited = BooleanArray(sessionEndpoints.size)

        for (i in sessionEndpoints.indices) {
            if (visited[i]) continue
            val group = mutableListOf(sessionEndpoints[i].first)
            visited[i] = true

            for (j in (i + 1) until sessionEndpoints.size) {
                if (visited[j]) continue
                val startDist = GpsFilter.calculateDistanceMeters(
                    sessionEndpoints[i].second.latitude, sessionEndpoints[i].second.longitude,
                    sessionEndpoints[j].second.latitude, sessionEndpoints[j].second.longitude
                )
                val endDist = GpsFilter.calculateDistanceMeters(
                    sessionEndpoints[i].third.latitude, sessionEndpoints[i].third.longitude,
                    sessionEndpoints[j].third.latitude, sessionEndpoints[j].third.longitude
                )

                if (startDist < 400.0 && endDist < 400.0) {
                    group.add(sessionEndpoints[j].first)
                    visited[j] = true
                }
            }
            if (group.size >= 2) {
                matchedGroups.add(group)
            }
        }

        for ((index, group) in matchedGroups.withIndex()) {
            val frequency = group.size
            val confidence = (frequency.toFloat() / sessions.size.toFloat() + 0.3f).coerceIn(0.55f, 0.88f)
            val weeklyFreq = ((frequency.toFloat() / sessions.size.toFloat()) * 6).roundToInt().coerceAtLeast(1)

            clusterList.add(
                PatternInsight(
                    title = "Rota recorrente detectada #${index + 1}",
                    description = "Deslocamento frequente identificado entre pontos comuns. Frequência estimada: $weeklyFreq vezes por semana.",
                    category = InsightCategory.RECURRING_ROUTE,
                    confidence = confidence,
                    count = frequency,
                    estimatedWeeklyFrequency = weeklyFreq
                )
            )
        }

        return clusterList
    }
}
