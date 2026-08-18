package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.account.AccountScreen
import com.example.ui.account.AccountViewModel
import com.example.ui.ai.AiInsightsScreen
import com.example.ui.ai.AiInsightsViewModel
import com.example.ui.components.AppBottomNavigation
import com.example.ui.components.UserTopHeader
import com.example.ui.devices.DevicesScreen
import com.example.ui.devices.DevicesViewModel
import com.example.ui.geofence.GeofenceScreen
import com.example.ui.geofence.GeofenceViewModel
import com.example.ui.history.HistoryScreen
import com.example.ui.history.HistoryViewModel
import com.example.ui.history.RouteDetailsScreen
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.map.MapScreen
import com.example.ui.map.MapViewModel
import com.example.ui.navigation.Routes
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.sharing.SharingScreen
import com.example.ui.sharing.SharingViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navController = rememberNavController()

    val homeViewModel: HomeViewModel = viewModel()
    val mapViewModel: MapViewModel = viewModel()
    val devicesViewModel: DevicesViewModel = viewModel()
    val historyViewModel: HistoryViewModel = viewModel()
    val aiInsightsViewModel: AiInsightsViewModel = viewModel()
    val geofenceViewModel: GeofenceViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val accountViewModel: AccountViewModel = viewModel()
    val sharingViewModel: SharingViewModel = viewModel()

    val user by accountViewModel.currentUser.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Show bottom bar on primary tabs
    val showBottomBar = currentRoute in listOf(
        Routes.HOME,
        Routes.MAP,
        Routes.HISTORY,
        Routes.DEVICES,
        Routes.SHARING,
        Routes.SETTINGS
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                AppBottomNavigation(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        if (route != currentRoute) {
                            navController.navigate(route) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // User Top Header on main dashboard
            if (currentRoute == Routes.HOME) {
                UserTopHeader(
                    user = user,
                    pendingOfflineCount = 0,
                    onNavigateToAccount = { navController.navigate(Routes.ACCOUNT) },
                    onNavigateToDevices = { navController.navigate(Routes.DEVICES) },
                    onNavigateToSharing = { navController.navigate(Routes.SHARING) },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    onSignOut = { accountViewModel.signOut(context) },
                    onSignIn = { navController.navigate(Routes.ACCOUNT) }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            viewModel = homeViewModel,
                            onNavigateToMap = { navController.navigate(Routes.MAP) },
                            onNavigateToDevices = { navController.navigate(Routes.DEVICES) },
                            onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                            onNavigateToAiInsights = { navController.navigate(Routes.AI_INSIGHTS) },
                            onNavigateToGeofence = { navController.navigate(Routes.GEOFENCE) },
                            onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
                        )
                    }

                    composable(Routes.ACCOUNT) {
                        AccountScreen(
                            viewModel = accountViewModel,
                            onBackClick = { navController.popBackStack() },
                            onNavigateToDevices = { navController.navigate(Routes.DEVICES) },
                            onNavigateToSharing = { navController.navigate(Routes.SHARING) }
                        )
                    }

                    composable(Routes.SHARING) {
                        SharingScreen(
                            viewModel = sharingViewModel,
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable(Routes.DEVICES) {
                        DevicesScreen(
                            viewModel = devicesViewModel,
                            onDeviceClick = { deviceId ->
                                navController.navigate(Routes.mapWithDevice(deviceId))
                            },
                            onNavigateToSharing = { navController.navigate(Routes.SHARING) },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable(Routes.MAP) {
                        MapScreen(
                            viewModel = mapViewModel,
                            initialFocusedDeviceId = null,
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = Routes.MAP_WITH_DEVICE,
                        arguments = listOf(navArgument("deviceId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        })
                    ) { backStackEntry ->
                        val devId = backStackEntry.arguments?.getString("deviceId")
                        MapScreen(
                            viewModel = mapViewModel,
                            initialFocusedDeviceId = devId,
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable(Routes.HISTORY) {
                        HistoryScreen(
                            viewModel = historyViewModel,
                            onSessionClick = { sessionId ->
                                navController.navigate(Routes.routeDetails(sessionId))
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = Routes.ROUTE_DETAILS,
                        arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
                        RouteDetailsScreen(
                            sessionId = sessionId,
                            viewModel = historyViewModel,
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable(Routes.AI_INSIGHTS) {
                        AiInsightsScreen(
                            viewModel = aiInsightsViewModel,
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable(Routes.GEOFENCE) {
                        GeofenceScreen(
                            viewModel = geofenceViewModel,
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
