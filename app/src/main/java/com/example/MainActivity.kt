package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.HomeScreen
import com.example.ui.MainViewModel
import com.example.ui.SettingsScreen
import com.example.ui.theme.DoubleTapWakeTheme

const val ROUTE_HOME = "home"
const val ROUTE_SETTINGS = "settings"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            DoubleTapWakeTheme {
                DoubleTapWakeApp()
            }
        }
    }
}

@Composable
fun DoubleTapWakeApp(
    viewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasNotificationPermission = granted
    }

    // Refresh battery optimization status & permission state when returning from system Settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshDeviceCapabilities()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    val requestNotificationPermissionIfNeeded: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_HOME,
    ) {
        composable(ROUTE_HOME) {
            HomeScreen(
                uiState = uiState,
                needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !hasNotificationPermission,
                onRequestNotificationPermission = requestNotificationPermissionIfNeeded,
                onToggleFeature = { enable ->
                    if (enable) {
                        requestNotificationPermissionIfNeeded()
                    }
                    viewModel.toggleFeature(enable)
                },
                onSensitivitySelected = viewModel::setSensitivity,
                onOpenSettings = {
                    navController.navigate(ROUTE_SETTINGS) {
                        launchSingleTop = true
                    }
                },
                onOpenDisplaySettings = viewModel::openDisplaySettings,
            )
        }

        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                uiState = uiState,
                onNavigateBack = { navController.popBackStack() },
                onToggleBoot = viewModel::setEnableOnBoot,
                onTogglePocketProtection = viewModel::setPocketProtection,
                onSelectSensitivity = viewModel::setSensitivity,
                onOpenBatterySettings = viewModel::openBatteryOptimizationSettings,
            )
        }
    }
}
