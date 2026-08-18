package com.example.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.TrackerApp
import com.example.domain.ai.AiRouteAnalyzer
import com.example.domain.ai.AnomalyDetector
import com.example.domain.ai.RoutePredictor
import com.example.domain.model.PatternInsight
import com.example.domain.model.RouteAnomaly
import com.example.domain.model.RoutePrediction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AiInsightsUiState(
    val isLoading: Boolean = true,
    val patterns: List<PatternInsight> = emptyList(),
    val anomalies: List<RouteAnomaly> = emptyList(),
    val prediction: RoutePrediction? = null,
    val totalSessionsAnalyzed: Int = 0
)

class AiInsightsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TrackerApp
    private val repo = app.trackingRepository

    private val _uiState = MutableStateFlow(AiInsightsUiState())
    val uiState: StateFlow<AiInsightsUiState> = _uiState.asStateFlow()

    init {
        loadInsights()
    }

    fun loadInsights() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val sessions = repo.getAllSessions()
            // Collect one-shot snapshot for computation
            kotlinx.coroutines.flow.combine(
                repo.getAllSessions(),
                repo.currentMetrics
            ) { sessionList, metrics ->
                val allPoints = mutableListOf<com.example.data.database.LocationPoint>()
                for (s in sessionList) {
                    allPoints.addAll(repo.getPointsForSession(s.id))
                }

                val patterns = AiRouteAnalyzer.analyzeSessions(sessionList, allPoints)

                val allAnomalies = mutableListOf<RouteAnomaly>()
                for (s in sessionList) {
                    allAnomalies.addAll(AnomalyDetector.detectAnomalies(s, sessionList))
                }

                val prediction = RoutePredictor.predictNextRoute(
                    currentLatitude = metrics.currentLatitude,
                    currentLongitude = metrics.currentLongitude,
                    historicalSessions = sessionList,
                    allPoints = allPoints
                )

                AiInsightsUiState(
                    isLoading = false,
                    patterns = patterns,
                    anomalies = allAnomalies,
                    prediction = prediction,
                    totalSessionsAnalyzed = sessionList.size
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
