package com.example

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.sara.control.PermissionType
import com.example.sara.ui.MemoryScreen
import com.example.sara.ui.SaraViewModel
import com.example.sara.ui.SaraVoiceScreen
import com.example.sara.ui.SensitiveActionConfirmationDialog
import com.example.sara.ui.SmartControlScreen
import com.example.sara.ui.UiEvent
import com.example.ui.theme.SaraAiTheme

const val ROUTE_SARA = "sara"
const val ROUTE_SMART_CONTROL = "smart_control"
const val ROUTE_MEMORY = "memory"

private data class NavDestinationItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SaraAiTheme {
                SaraAppRoot()
            }
        }
    }
}

@Composable
fun SaraAppRoot(
    viewModel: SaraViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_SARA
    val snackbarHostState = remember { SnackbarHostState() }
    val pendingSensitive by viewModel.pendingSensitiveAction.collectAsStateWithLifecycle()

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshTelemetry()
        if (granted) {
            viewModel.startVoiceCommand()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshTelemetry()
        if (granted) {
            viewModel.toggleFlashlight(true)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshTelemetry()
        viewModel.refreshInstalledApps()
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.RequestRuntimePermission -> {
                    when (event.permissionType) {
                        PermissionType.RECORD_AUDIO -> audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        PermissionType.CAMERA -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        else -> viewModel.controlManager.openPermissionSettings(event.permissionType)
                    }
                }
                is UiEvent.ShowFeedback -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    val destinations = remember {
        listOf(
            NavDestinationItem(
                route = ROUTE_SARA,
                labelRes = R.string.nav_sara_voice,
                selectedIcon = Icons.Rounded.Mic,
                unselectedIcon = Icons.Outlined.Mic,
                testTag = "nav_sara_voice"
            ),
            NavDestinationItem(
                route = ROUTE_SMART_CONTROL,
                labelRes = R.string.nav_smart_control,
                selectedIcon = Icons.Rounded.Tune,
                unselectedIcon = Icons.Outlined.Tune,
                testTag = "nav_smart_control"
            ),
            NavDestinationItem(
                route = ROUTE_MEMORY,
                labelRes = R.string.nav_memory,
                selectedIcon = Icons.Rounded.Psychology,
                unselectedIcon = Icons.Outlined.Psychology,
                testTag = "nav_memory"
            )
        )
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    pendingSensitive?.let { pending ->
        SensitiveActionConfirmationDialog(
            pending = pending,
            onConfirm = { viewModel.confirmSensitiveAction() },
            onDismiss = { viewModel.dismissSensitiveAction() }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val label = stringResource(dest.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val label = stringResource(dest.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_SARA,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_SARA) {
                        SaraVoiceScreen(
                            viewModel = viewModel,
                            onOpenSmartControl = { navigateTo(ROUTE_SMART_CONTROL) }
                        )
                    }
                    composable(ROUTE_SMART_CONTROL) {
                        SmartControlScreen(viewModel = viewModel)
                    }
                    composable(ROUTE_MEMORY) {
                        MemoryScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
