package com.example.ui.navigation

object Routes {
    const val HOME = "home"
    const val MAP = "map"
    const val MAP_WITH_DEVICE = "map?deviceId={deviceId}"
    const val DEVICES = "devices"
    const val SHARING = "sharing"
    const val ACCOUNT = "account"
    const val HISTORY = "history"
    const val ROUTE_DETAILS = "route_details/{sessionId}"
    const val AI_INSIGHTS = "ai_insights"
    const val GEOFENCE = "geofence"
    const val SETTINGS = "settings"

    fun routeDetails(sessionId: String): String = "route_details/$sessionId"
    fun mapWithDevice(deviceId: String): String = "map?deviceId=$deviceId"
}
