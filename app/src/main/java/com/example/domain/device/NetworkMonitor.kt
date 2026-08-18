package com.example.domain.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class NetworkState(
    val isWifiConnected: Boolean = false,
    val wifiSsid: String = "Não conectado", // "MinhaRede", "Não conectado", "Indisponível"
    val isCellularConnected: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

class NetworkMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    fun getCurrentNetworkState(): NetworkState {
        val cm = connectivityManager ?: return NetworkState()

        val activeNetwork = cm.activeNetwork ?: return NetworkState(
            isWifiConnected = false,
            wifiSsid = "Não conectado"
        )

        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return NetworkState(
            isWifiConnected = false,
            wifiSsid = "Não conectado"
        )

        val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

        var ssidText = "Não conectado"
        if (isWifi) {
            ssidText = extractWifiSsid(capabilities)
        }

        return NetworkState(
            isWifiConnected = isWifi,
            wifiSsid = ssidText,
            isCellularConnected = isCellular,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun extractWifiSsid(capabilities: NetworkCapabilities): String {
        try {
            // Android 10+ (Q+): Try getting WifiInfo from NetworkCapabilities safely
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val wifiInfo = capabilities.transportInfo as? WifiInfo
                if (wifiInfo != null) {
                    val rawSsid = wifiInfo.ssid
                    val cleaned = cleanSsid(rawSsid)
                    if (cleaned != null) return cleaned
                }
            }

            // Fallback via WifiManager.connectionInfo
            val wm = wifiManager
            if (wm != null && wm.isWifiEnabled) {
                @Suppress("DEPRECATION")
                val info = wm.connectionInfo
                if (info != null) {
                    val rawSsid = info.ssid
                    val cleaned = cleanSsid(rawSsid)
                    if (cleaned != null) return cleaned
                }
            }
        } catch (e: Exception) {
            Log.w("NetworkMonitor", "Could not safely read Wi-Fi SSID: ${e.message}")
        }

        // If connected to Wi-Fi but SSID cannot be legitimately read (due to OS privacy or permissions)
        return "Indisponível"
    }

    private fun cleanSsid(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (trimmed == "<unknown ssid>" || trimmed == "0x" || trimmed == "unknown") {
            return "Indisponível"
        }
        // Remove surrounding quotation marks if present
        val unquoted = if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length > 1) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }

        return if (unquoted.isBlank() || unquoted == "<unknown ssid>") "Indisponível" else unquoted
    }

    fun observeNetworkState(): Flow<NetworkState> = callbackFlow {
        val cm = connectivityManager
        if (cm == null) {
            trySend(NetworkState())
            awaitClose { }
            return@callbackFlow
        }

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(getCurrentNetworkState())
            }

            override fun onLost(network: Network) {
                trySend(getCurrentNetworkState())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(getCurrentNetworkState())
            }
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        try {
            cm.registerNetworkCallback(request, networkCallback)
            // Emit initial state
            trySend(getCurrentNetworkState())
        } catch (e: Exception) {
            Log.e("NetworkMonitor", "Failed to register network callback: ${e.message}")
            trySend(getCurrentNetworkState())
        }

        awaitClose {
            try {
                cm.unregisterNetworkCallback(networkCallback)
            } catch (e: Exception) {
                Log.w("NetworkMonitor", "Error unregistering network callback: ${e.message}")
            }
        }
    }
}
