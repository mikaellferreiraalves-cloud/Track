package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.TrackerApp
import com.example.data.database.LocationPoint
import com.example.data.database.TrackingSession
import com.example.data.location.AdaptiveFixDecision
import com.example.data.location.AdaptiveLocationManager
import com.example.data.location.DefaultLocationClient
import com.example.data.location.LocationClient
import com.example.data.repository.LocationIntervalMode
import com.example.domain.model.TrackingMetrics
import com.example.domain.model.TrackingState
import com.example.domain.tracking.GeofenceManager
import com.example.domain.tracking.GpsFilterConfig
import com.example.domain.tracking.TransitionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class LocationTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var locationJob: Job? = null
    private var timerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var locationClient: LocationClient
    private lateinit var adaptiveLocationManager: AdaptiveLocationManager

    private var currentSessionId: String? = null
    private var sessionStartTime: Long = 0L
    private var totalDistanceMeters: Double = 0.0
    private var maxSpeedMps: Double = 0.0
    private var recordedPointsCount: Int = 0
    private var isPaused: Boolean = false

    companion object {
        const val CHANNEL_ID = "gps_tracking_channel"
        const val GEOFENCE_CHANNEL_ID = "gps_geofence_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        locationClient = DefaultLocationClient(applicationContext)
        adaptiveLocationManager = AdaptiveLocationManager()
        createNotificationChannels()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GpsTracker:WakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        val app = application as TrackerApp
        val repo = app.trackingRepository
        val settingsRepo = app.settingsRepository

        if (repo.currentMetrics.value.state == TrackingState.TRACKING) {
            return
        }

        wakeLock?.acquire(3 * 60 * 60 * 1000L) // 3h max safety timeout

        val newSessionId = UUID.randomUUID().toString()
        currentSessionId = newSessionId
        sessionStartTime = System.currentTimeMillis()
        totalDistanceMeters = 0.0
        maxSpeedMps = 0.0
        recordedPointsCount = 0
        isPaused = false
        adaptiveLocationManager.reset()

        // Insert initial session into DB
        serviceScope.launch {
            val settings = settingsRepo.settingsFlow.first()
            if (settings.saveHistory) {
                repo.insertSession(
                    TrackingSession(
                        id = newSessionId,
                        startTime = sessionStartTime,
                        distanceMeters = 0.0,
                        pointCount = 0
                    )
                )
            }
        }

        // Start Foreground Notification
        val notification = buildNotification("Rastreamento ativo", "0.0 km • 00:00")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        repo.updateMetrics {
            it.copy(
                state = TrackingState.TRACKING,
                sessionId = newSessionId,
                distanceMeters = 0.0,
                currentSpeedKmh = 0f,
                averageSpeedKmh = 0.0,
                maxSpeedKmh = 0.0,
                elapsedTimeMs = 0L,
                pointCount = 0,
                errorMessage = null
            )
        }

        startTimer()
        startLocationUpdates()
    }

    private fun pauseTracking() {
        val app = application as TrackerApp
        isPaused = true
        locationJob?.cancel()
        timerJob?.cancel()

        app.trackingRepository.updateMetrics {
            it.copy(state = TrackingState.PAUSED, currentSpeedKmh = 0f)
        }

        updateNotification("Rastreamento pausado", formatNotificationText())
    }

    private fun resumeTracking() {
        val app = application as TrackerApp
        isPaused = false
        app.trackingRepository.updateMetrics {
            it.copy(state = TrackingState.TRACKING)
        }
        startTimer()
        startLocationUpdates()
        updateNotification("Rastreamento ativo", formatNotificationText())
    }

    private fun stopTracking() {
        val app = application as TrackerApp
        val repo = app.trackingRepository
        val sessionId = currentSessionId

        locationJob?.cancel()
        timerJob?.cancel()

        repo.updateMetrics { it.copy(state = TrackingState.STOPPING) }

        serviceScope.launch {
            if (sessionId != null) {
                val existingSession = repo.getSessionById(sessionId)
                if (existingSession != null) {
                    val endTime = System.currentTimeMillis()
                    val durationSeconds = ((endTime - sessionStartTime) / 1000.0).coerceAtLeast(1.0)
                    val avgSpeed = totalDistanceMeters / durationSeconds

                    repo.updateSession(
                        existingSession.copy(
                            endTime = endTime,
                            distanceMeters = totalDistanceMeters,
                            averageSpeed = avgSpeed,
                            maxSpeed = maxSpeedMps,
                            pointCount = recordedPointsCount
                        )
                    )
                }
            }

            repo.updateMetrics {
                it.copy(
                    state = TrackingState.IDLE,
                    currentSpeedKmh = 0f
                )
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                if (!isPaused) {
                    val elapsed = System.currentTimeMillis() - sessionStartTime
                    val durationSeconds = (elapsed / 1000.0).coerceAtLeast(1.0)
                    val avgSpeedKmh = (totalDistanceMeters / durationSeconds) * 3.6

                    val app = application as TrackerApp
                    app.trackingRepository.updateMetrics {
                        it.copy(
                            elapsedTimeMs = elapsed,
                            averageSpeedKmh = avgSpeedKmh
                        )
                    }

                    updateNotification("Rastreamento ativo", formatNotificationText())
                }
                delay(1000L)
            }
        }
    }

    private fun startLocationUpdates() {
        locationJob?.cancel()
        val app = application as TrackerApp
        val repo = app.trackingRepository
        val settingsRepo = app.settingsRepository

        locationJob = serviceScope.launch {
            val settings = settingsRepo.settingsFlow.first()

            val intervalMs = when (settings.intervalMode) {
                LocationIntervalMode.HIGH_PRECISION -> 1500L
                LocationIntervalMode.BATTERY_SAVING -> 12000L
                LocationIntervalMode.AUTOMATIC -> 3000L
            }

            adaptiveLocationManager.filterConfig = GpsFilterConfig(
                maxAccuracyMeters = settings.accuracyLevel.maxMeters
            )

            locationClient.getLocationUpdates(intervalMs)
                .catch { e ->
                    repo.updateMetrics {
                        it.copy(
                            state = TrackingState.ERROR,
                            errorMessage = e.message ?: "Erro ao receber sinal GPS"
                        )
                    }
                }
                .onEach { location ->
                    if (isPaused) return@onEach

                    val lat = location.latitude
                    val lon = location.longitude
                    val accuracy = if (location.hasAccuracy()) location.accuracy else null
                    val speed = if (location.hasSpeed()) location.speed else null
                    val altitude = if (location.hasAltitude()) location.altitude else null
                    val bearing = if (location.hasBearing()) location.bearing else null
                    val timestamp = location.time.let { if (it > 0) it else System.currentTimeMillis() }

                    val decision = adaptiveLocationManager.processLocationFix(
                        latitude = lat,
                        longitude = lon,
                        accuracy = accuracy,
                        speed = speed,
                        timestamp = timestamp
                    )

                    when (decision) {
                        is AdaptiveFixDecision.Accept -> {
                            totalDistanceMeters += decision.distanceMeters
                            val speedMps = speed?.toDouble() ?: decision.speedMps
                            if (speedMps > maxSpeedMps) {
                                maxSpeedMps = speedMps
                            }
                            val speedKmh = (speedMps * 3.6).toFloat()
                            recordedPointsCount++

                            val sessionId = currentSessionId
                            if (sessionId != null && settings.saveHistory) {
                                val pt = LocationPoint(
                                    latitude = lat,
                                    longitude = lon,
                                    altitude = altitude,
                                    accuracy = accuracy,
                                    speed = speed,
                                    bearing = bearing,
                                    timestamp = timestamp,
                                    sessionId = sessionId
                                )
                                repo.insertPoint(pt)
                                app.firestoreSyncManager.uploadLocationPoint(pt)
                            }

                            // Geofence check
                            if (settings.geofenceAlertsEnabled) {
                                checkGeofences(lat, lon)
                            }

                            val elapsed = System.currentTimeMillis() - sessionStartTime
                            val avgSpeedKmh = if (elapsed > 0) (totalDistanceMeters / (elapsed / 1000.0)) * 3.6 else 0.0

                            repo.updateMetrics {
                                it.copy(
                                    distanceMeters = totalDistanceMeters,
                                    currentSpeedKmh = speedKmh,
                                    averageSpeedKmh = avgSpeedKmh,
                                    maxSpeedKmh = maxSpeedMps * 3.6,
                                    pointCount = recordedPointsCount,
                                    currentLatitude = lat,
                                    currentLongitude = lon,
                                    currentAltitude = altitude,
                                    currentAccuracy = accuracy
                                )
                            }
                        }
                        is AdaptiveFixDecision.Reject -> {
                            // Update current coordinate for live map view even if filtered from recorded track
                            repo.updateMetrics {
                                it.copy(
                                    currentLatitude = lat,
                                    currentLongitude = lon,
                                    currentAccuracy = accuracy
                                )
                            }
                        }
                    }
                }
                .launchIn(this)
        }
    }

    private suspend fun checkGeofences(latitude: Double, longitude: Double) {
        val app = application as TrackerApp
        val repo = app.trackingRepository
        val activeGeofences = repo.getActiveGeofences()
        val transitions = GeofenceManager.evaluateTransitions(latitude, longitude, activeGeofences)

        for (transition in transitions) {
            repo.updateGeofence(transition.geofence)
            val actionText = if (transition.transitionType == TransitionType.ENTERED) "entrou na área" else "saiu da área"
            showGeofenceNotification(
                title = "Alerta de Área: ${transition.geofence.name}",
                message = "O dispositivo $actionText \"${transition.geofence.name}\"."
            )
        }
    }

    private fun formatNotificationText(): String {
        val distKm = totalDistanceMeters / 1000.0
        val elapsedSec = (System.currentTimeMillis() - sessionStartTime) / 1000
        val hours = elapsedSec / 3600
        val minutes = (elapsedSec % 3600) / 60
        val seconds = elapsedSec % 60
        val timeStr = if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
        return String.format(Locale.getDefault(), "%.2f km • %s • %d pts", distKm, timeStr, recordedPointsCount)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseResumeIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val pauseResumePendingIntent = PendingIntent.getService(
            this,
            1,
            pauseResumeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.app_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                0,
                if (isPaused) "Retomar" else "Pausar",
                pauseResumePendingIntent
            )
            .addAction(0, "Parar", stopPendingIntent)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    @SuppressLint("MissingPermission")
    private fun showGeofenceNotification(title: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, GEOFENCE_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.app_logo)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify((System.currentTimeMillis() % 10000).toInt() + 2000, notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val trackingChannel = NotificationChannel(
                CHANNEL_ID,
                "Rastreamento de Localização",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação persistente durante o registro do trajeto"
                setShowBadge(false)
            }

            val geofenceChannel = NotificationChannel(
                GEOFENCE_CHANNEL_ID,
                "Alertas de Geofence",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas ao entrar ou sair de áreas configuradas"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(trackingChannel)
            manager.createNotificationChannel(geofenceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        locationJob?.cancel()
        timerJob?.cancel()
        serviceScope.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }
}
