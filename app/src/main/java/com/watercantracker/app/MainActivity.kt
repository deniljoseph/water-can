package com.watercantracker.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.work.WorkManager
import com.watercantracker.app.notification.ReminderWorker
import com.watercantracker.app.sync.SyncViewModel
import com.watercantracker.app.ui.navigation.Screen
import com.watercantracker.app.ui.navigation.WaterCanNavGraph
import com.watercantracker.app.ui.navigation.bottomNavItems
import com.watercantracker.app.ui.screens.settings.SettingsViewModel
import com.watercantracker.app.ui.theme.WaterCanTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var workManager: WorkManager

    // Hold a reference to the nav controller so onNewIntent can navigate
    private var navController: NavHostController? = null
    // Hold pending room ID from deep link until nav controller is ready
    private var pendingRoomId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderWorker.schedule(workManager)

        // Handle QR scan deep link that launched the app fresh
        pendingRoomId = extractRoomId(intent)

        setContent {
            val settingsVm: SettingsViewModel = hiltViewModel()
            val settingsState by settingsVm.uiState.collectAsStateWithLifecycle()

            WaterCanTrackerTheme(
                themeMode       = settingsState.themeMode,
                darkModeVariant = settingsState.darkModeVariant,
                accentColor     = settingsState.accentColor
            ) {
                WaterCanApp(
                    onNavReady = { controller ->
                        navController = controller
                        // If a room ID was extracted before nav was ready, handle it now
                        pendingRoomId?.let { roomId ->
                            pendingRoomId = null
                            navigateToSyncWithRoomId(controller, roomId)
                        }
                    }
                )
            }
        }
    }

    // Called when app is already running and a QR scan deep link arrives
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val roomId = extractRoomId(intent) ?: return
        val controller = navController
        if (controller != null) {
            navigateToSyncWithRoomId(controller, roomId)
        } else {
            pendingRoomId = roomId
        }
    }

    private fun extractRoomId(intent: Intent?): String? {
        val data = intent?.data ?: return null
        // Handles: watercan://sync/{roomId}
        if (data.scheme == "watercan" && data.host == "sync") {
            return data.lastPathSegment?.takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun navigateToSyncWithRoomId(controller: NavHostController, roomId: String) {
        controller.navigate(Screen.Sync.createRouteWithRoomId(roomId)) {
            launchSingleTop = true
        }
    }
}

@Composable
private fun WaterCanApp(onNavReady: (NavHostController) -> Unit) {
    val navController = rememberNavController()
    val currentBackstack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackstack?.destination?.route
    val topLevelRoutes = bottomNavItems.map { it.screen.route }
    val showBottomBar = currentRoute in topLevelRoutes

    // Notify activity that nav controller is ready
    LaunchedEffect(navController) {
        onNavReady(navController)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(item.screen.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        WaterCanNavGraph(navController = navController, bottomPadding = innerPadding)
    }
}
