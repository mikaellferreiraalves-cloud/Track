package com.example.domain.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class BatteryState(
    val levelPercent: Int = 100,
    val isCharging: Boolean = false,
    val statusText: String = "Não carregando", // "Carregando", "Descarregando", "Carregada", "Não carregando"
    val timestamp: Long = System.currentTimeMillis()
)

class BatteryMonitor(private val context: Context) {

    fun getCurrentBatteryState(): BatteryState {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryIntent = context.registerReceiver(null, intentFilter)
            parseBatteryIntent(batteryIntent)
        } catch (e: Exception) {
            Log.w("BatteryMonitor", "Failed to read battery state: ${e.message}")
            BatteryState()
        }
    }

    fun observeBatteryState(): Flow<BatteryState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val state = parseBatteryIntent(intent)
                    trySend(state)
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }

        try {
            context.registerReceiver(receiver, filter)
            // Emit initial state
            trySend(getCurrentBatteryState())
        } catch (e: Exception) {
            Log.e("BatteryMonitor", "Failed to register battery receiver: ${e.message}")
            trySend(BatteryState())
        }

        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.w("BatteryMonitor", "Error unregistering battery receiver: ${e.message}")
            }
        }
    }

    private fun parseBatteryIntent(intent: Intent?): BatteryState {
        if (intent == null) return BatteryState()

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) {
            ((level.toFloat() / scale.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else {
            100
        }

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val statusText = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Carregando"
            BatteryManager.BATTERY_STATUS_FULL -> "Carregada"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Descarregando"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Não carregando"
            else -> if (isCharging) "Carregando" else "Não carregando"
        }

        return BatteryState(
            levelPercent = percent,
            isCharging = isCharging,
            statusText = statusText,
            timestamp = System.currentTimeMillis()
        )
    }
}
