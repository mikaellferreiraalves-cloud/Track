package com.example

import android.app.Application
import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.repository.SettingsRepository
import com.example.data.repository.TrackingRepository
import com.example.data.repository.TrackingRepositoryImpl
import org.osmdroid.config.Configuration

class TrackerApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var trackingRepository: TrackingRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var deviceSyncManager: com.example.domain.devices.DeviceSyncManager
        private set

    lateinit var authManager: com.example.domain.auth.AuthManager
        private set

    lateinit var deviceStatusRepository: com.example.domain.device.DeviceStatusRepository
        private set

    lateinit var firestoreSyncManager: com.example.domain.sync.FirestoreSyncManager
        private set

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this)
        trackingRepository = TrackingRepositoryImpl(database.trackingDao())
        settingsRepository = SettingsRepository(this)
        deviceSyncManager = com.example.domain.devices.DeviceSyncManager(this, trackingRepository)
        authManager = com.example.domain.auth.AuthManager(this)
        deviceStatusRepository = com.example.domain.device.DeviceStatusRepository(this)
        firestoreSyncManager = com.example.domain.sync.FirestoreSyncManager(this, authManager, trackingRepository, deviceStatusRepository)

        // Configure osmdroid OpenStreetMap settings
        val osmBasePath = java.io.File(cacheDir, "osmdroid")
        val osmTileCache = java.io.File(osmBasePath, "tiles")
        if (!osmTileCache.exists()) {
            osmTileCache.mkdirs()
        }

        try {
            org.osmdroid.tileprovider.BitmapPool.getInstance().clearBitmapPool()
        } catch (_: Throwable) {}

        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = osmBasePath
            osmdroidTileCache = osmTileCache
            cacheMapTileCount = 16.toShort()
            cacheMapTileOvershoot = 0.toShort()
            tileFileSystemCacheMaxBytes = 64L * 1024L * 1024L
            tileFileSystemCacheTrimBytes = 48L * 1024L * 1024L
            isMapViewHardwareAccelerated = true
            load(applicationContext, applicationContext.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE))
        }
    }
}

