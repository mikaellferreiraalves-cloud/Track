package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.GeofenceArea
import com.example.data.database.LocationPoint
import com.example.data.database.TrackingSession
import com.example.data.export.RouteExporter
import com.example.domain.ai.AiRouteAnalyzer
import com.example.domain.ai.AnomalyDetector
import com.example.domain.ai.RoutePredictor
import com.example.domain.tracking.FilterResult
import com.example.domain.tracking.GeofenceManager
import com.example.domain.tracking.GpsFilter
import com.example.domain.tracking.GpsFilterConfig
import com.example.domain.tracking.TransitionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Rastreamento GPS", appName)
    }

    @Test
    fun `gps filter calculates haversine distance correctly`() {
        val distance = GpsFilter.calculateDistanceMeters(
            -23.55052, -46.633308,
            -22.906847, -43.172896
        )
        assertTrue("Distance should be around 357 km", distance in 350000.0..365000.0)
    }

    @Test
    fun `gps filter validates coordinates boundaries`() {
        assertTrue(GpsFilter.isValidCoordinate(-23.55, -46.63))
        assertFalse(GpsFilter.isValidCoordinate(95.0, 0.0))
        assertFalse(GpsFilter.isValidCoordinate(0.0, 195.0))
        assertFalse(GpsFilter.isValidCoordinate(0.0, 0.0))
    }

    @Test
    fun `gps filter rejects inaccurate location fixes`() {
        val config = GpsFilterConfig(maxAccuracyMeters = 30.0f)
        val result = GpsFilter.shouldRecordPoint(
            prevLat = -23.55,
            prevLon = -46.63,
            prevTimestamp = 1000L,
            newLat = -23.56,
            newLon = -46.64,
            newAccuracy = 80.0f,
            newTimestamp = 5000L,
            config = config
        )
        assertTrue("Low accuracy fix should be rejected", result is FilterResult.Rejected)
    }

    @Test
    fun `geofence manager detects entry and exit transitions`() {
        val homeGeofence = GeofenceArea(
            id = 1L,
            name = "Casa",
            latitude = -23.55000,
            longitude = -46.63000,
            radiusMeters = 100.0,
            isEnabled = true,
            isInside = false
        )

        // Device enters geofence radius
        val enterTransitions = GeofenceManager.evaluateTransitions(
            latitude = -23.55010,
            longitude = -46.63010,
            geofences = listOf(homeGeofence)
        )
        assertEquals(1, enterTransitions.size)
        assertEquals(TransitionType.ENTERED, enterTransitions[0].transitionType)
        assertTrue(enterTransitions[0].geofence.isInside)

        // Device moves far away (exits geofence)
        val updatedGeofence = enterTransitions[0].geofence
        val exitTransitions = GeofenceManager.evaluateTransitions(
            latitude = -23.57000,
            longitude = -46.65000,
            geofences = listOf(updatedGeofence)
        )
        assertEquals(1, exitTransitions.size)
        assertEquals(TransitionType.EXITED, exitTransitions[0].transitionType)
        assertFalse(exitTransitions[0].geofence.isInside)
    }

    @Test
    fun `route exporter produces valid csv and kml formats`() {
        val testSession = TrackingSession(
            id = "test-session-123",
            startTime = 1700000000000L,
            endTime = 1700003600000L,
            distanceMeters = 5400.0,
            averageSpeed = 15.0,
            maxSpeed = 22.0,
            pointCount = 2
        )

        val points = listOf(
            LocationPoint(
                latitude = -23.55052,
                longitude = -46.633308,
                altitude = 760.0,
                accuracy = 5.0f,
                speed = 12.0f,
                bearing = 90.0f,
                timestamp = 1700000000000L,
                sessionId = "test-session-123"
            ),
            LocationPoint(
                latitude = -23.55152,
                longitude = -46.634308,
                altitude = 762.0,
                accuracy = 4.0f,
                speed = 14.0f,
                bearing = 95.0f,
                timestamp = 1700000010000L,
                sessionId = "test-session-123"
            )
        )

        val csv = RouteExporter.generateCsv(points)
        assertTrue(csv.startsWith("timestamp,latitude,longitude,altitude,accuracy,speed,bearing"))
        assertTrue(csv.contains("-23.55052,-46.633308"))

        val kml = RouteExporter.generateKml(testSession, points)
        assertTrue(kml.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(kml.contains("<LineString>"))
        assertTrue(kml.contains("-46.633308,-23.55052"))
    }

    @Test
    fun `room database inserts and queries sessions and location points correctly`() = runBlocking {
        val dao = db.trackingDao()

        val session = TrackingSession(
            id = "sess-01",
            startTime = System.currentTimeMillis(),
            distanceMeters = 1250.0,
            pointCount = 1
        )
        dao.insertSession(session)

        val retrievedSession = dao.getSessionById("sess-01")
        assertNotNull(retrievedSession)
        assertEquals(1250.0, retrievedSession?.distanceMeters ?: 0.0, 0.01)

        val point = LocationPoint(
            latitude = -23.55,
            longitude = -46.63,
            timestamp = System.currentTimeMillis(),
            sessionId = "sess-01"
        )
        dao.insertLocationPoint(point)

        val points = dao.getPointsForSession("sess-01")
        assertEquals(1, points.size)
        assertEquals(-23.55, points[0].latitude, 0.001)
    }

    @Test
    fun `ai pattern analyzer and predictor identify frequent patterns`() {
        val now = System.currentTimeMillis()
        val sessions = listOf(
            TrackingSession(id = "s1", startTime = now - 86400000L * 1, distanceMeters = 5000.0, averageSpeed = 8.0, pointCount = 10),
            TrackingSession(id = "s2", startTime = now - 86400000L * 2, distanceMeters = 5200.0, averageSpeed = 8.2, pointCount = 10),
            TrackingSession(id = "s3", startTime = now - 86400000L * 3, distanceMeters = 4900.0, averageSpeed = 7.9, pointCount = 10)
        )
        val points = listOf(
            LocationPoint(latitude = -23.55, longitude = -46.63, timestamp = now, sessionId = "s1"),
            LocationPoint(latitude = -23.58, longitude = -46.68, timestamp = now + 1000, sessionId = "s1")
        )

        val patterns = AiRouteAnalyzer.analyzeSessions(sessions, points)
        assertTrue(patterns.isNotEmpty())

        val prediction = RoutePredictor.predictNextRoute(
            currentLatitude = -23.55,
            currentLongitude = -46.63,
            historicalSessions = sessions,
            allPoints = points
        )
        assertNotNull(prediction)
    }
}
