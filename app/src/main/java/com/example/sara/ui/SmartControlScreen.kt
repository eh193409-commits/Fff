package com.example.sara.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.sara.control.AudioStreamType
import com.example.sara.control.GlobalNavAction
import com.example.sara.control.PermissionType
import com.example.sara.control.ScrollDirection
import com.example.sara.control.SwipeDirection

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartControlScreen(
    viewModel: SaraViewModel,
    modifier: Modifier = Modifier
) {
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isFlashlightOn by viewModel.isFlashlightOn.collectAsStateWithLifecycle()
    val isFlashlightAvailable by viewModel.isFlashlightAvailable.collectAsStateWithLifecycle()
    val isAccessibilityConnected by viewModel.isAccessibilityConnected.collectAsStateWithLifecycle()
    val visibleElements by viewModel.visibleElements.collectAsStateWithLifecycle()

    var appSearchQuery by remember { mutableStateOf("") }
    var clickQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.refreshTelemetry()
    }

    val filteredApps = remember(installedApps, appSearchQuery) {
        if (appSearchQuery.isBlank()) installedApps.take(12)
        else installedApps.filter {
            it.appName.contains(appSearchQuery, ignoreCase = true) ||
                it.packageName.contains(appSearchQuery, ignoreCase = true)
        }.take(16)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.smart_control_header),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Hardware & Screen Control",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(
                    onClick = {
                        viewModel.refreshTelemetry()
                        viewModel.refreshInstalledApps()
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("refresh_telemetry_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh device telemetry"
                    )
                }
            }
        }

        // 1. App Control & Screen Automation
        item {
            ControlSectionCard(
                title = "App Control & Screen Automation",
                icon = Icons.Rounded.Apps,
                badgeLabel = if (isAccessibilityConnected) "ACCESSIBILITY ON" else "ACCESSIBILITY OFF",
                badgeIsOn = isAccessibilityConnected,
                modifier = Modifier.testTag("card_app_control_automation")
            ) {
                OutlinedTextField(
                    value = appSearchQuery,
                    onValueChange = { appSearchQuery = it },
                    placeholder = { Text("Search ${installedApps.size} installed apps (e.g. YouTube, Chrome)...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_search_input")
                )

                if (filteredApps.isEmpty()) {
                    Text(
                        text = "No matching launchable apps found.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filteredApps.forEach { app ->
                            AssistChip(
                                onClick = { viewModel.launchApp(app) },
                                label = {
                                    Text(
                                        text = app.appName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                if (!isAccessibilityConnected) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Enable Sara's Accessibility Service to click buttons, scroll, swipe, and navigate inside apps.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.requestPermission(PermissionType.ACCESSIBILITY_SERVICE) },
                                modifier = Modifier.testTag("enable_accessibility_button")
                            ) {
                                Text("Enable")
                            }
                        }
                    }
                }

                // Screen Click & Automation Controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = clickQuery,
                        onValueChange = { clickQuery = it },
                        placeholder = { Text("Element text (e.g. Search, Settings)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("click_target_input")
                    )
                    Button(
                        onClick = {
                            if (clickQuery.isNotBlank()) {
                                viewModel.clickUiQuery(clickQuery)
                            }
                        },
                        enabled = clickQuery.isNotBlank(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("click_element_button")
                    ) {
                        Icon(Icons.Rounded.TouchApp, contentDescription = "Click element")
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(onClick = { viewModel.inspectScreen() }) {
                        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scan Screen")
                    }
                    FilledTonalButton(onClick = { viewModel.clickFirstResult() }) {
                        Text("Select 1st Result")
                    }
                    FilledTonalButton(onClick = { viewModel.performGlobalNav(GlobalNavAction.BACK) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Back")
                    }
                    FilledTonalButton(onClick = { viewModel.performGlobalNav(GlobalNavAction.HOME) }) {
                        Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Home")
                    }
                    FilledTonalButton(onClick = { viewModel.performScroll(ScrollDirection.DOWN) }) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scroll Down")
                    }
                    FilledTonalButton(onClick = { viewModel.performScroll(ScrollDirection.UP) }) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scroll Up")
                    }
                    FilledTonalButton(onClick = { viewModel.performScroll(ScrollDirection.TOP) }) {
                        Icon(Icons.Rounded.VerticalAlignTop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Top")
                    }
                    FilledTonalButton(onClick = { viewModel.performScroll(ScrollDirection.BOTTOM) }) {
                        Icon(Icons.Rounded.VerticalAlignBottom, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bottom")
                    }
                    FilledTonalButton(onClick = { viewModel.performSwipe(SwipeDirection.LEFT) }) {
                        Icon(Icons.Rounded.Swipe, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Swipe Left")
                    }
                    FilledTonalButton(onClick = { viewModel.performSwipe(SwipeDirection.RIGHT) }) {
                        Icon(Icons.Rounded.Swipe, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Swipe Right")
                    }
                }

                if (visibleElements.isNotEmpty()) {
                    Text(
                        text = "Last Screen Scan (${visibleElements.size} elements)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        visibleElements.take(12).forEach { elem ->
                            AssistChip(
                                onClick = { viewModel.clickUiQuery(elem.displayLabel) },
                                label = { Text(elem.displayLabel, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        }

        // 2. Quick Settings
        item {
            ControlSectionCard(
                title = "Quick Settings",
                icon = Icons.Rounded.Tune,
                badgeLabel = "NATIVE PANELS",
                badgeIsOn = true,
                modifier = Modifier.testTag("card_quick_settings")
            ) {
                Text(
                    text = "Android 10+ restricts silent background toggling of Wi-Fi, Mobile Data, Bluetooth, and GPS by third-party apps. Sara uses official System Settings Panels & Quick Settings shade.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(onClick = {
                        viewModel.openQuickSettingAction(
                            { viewModel.controlManager.quickSettings.openQuickSettingsPanel() },
                            "Open Quick Settings"
                        )
                    }) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Quick Settings Shade")
                    }
                    FilledTonalButton(onClick = {
                        viewModel.openQuickSettingAction(
                            { viewModel.controlManager.quickSettings.openNotificationShade() },
                            "Open Notifications"
                        )
                    }) {
                        Icon(Icons.Rounded.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Notifications")
                    }
                    FilledTonalButton(onClick = {
                        viewModel.openQuickSettingAction(
                            { viewModel.controlManager.quickSettings.openWifiPanel() },
                            "Wi-Fi Panel"
                        )
                    }) {
                        Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Wi-Fi Panel")
                    }
                    FilledTonalButton(onClick = {
                        viewModel.openQuickSettingAction(
                            { viewModel.controlManager.quickSettings.openInternetConnectivityPanel() },
                            "Internet Panel"
                        )
                    }) {
                        Text("Internet / Data")
                    }
                    FilledTonalButton(onClick = {
                        viewModel.openQuickSettingAction(
                            { viewModel.controlManager.quickSettings.openBluetoothSettings() },
                            "Bluetooth Settings"
                        )
                    }) {
                        Icon(Icons.Rounded.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bluetooth")
                    }
                    FilledTonalButton(onClick = {
                        viewModel.openQuickSettingAction(
                            { viewModel.controlManager.quickSettings.openDisplaySettings() },
                            "Display Settings"
                        )
                    }) {
                        Icon(Icons.Rounded.DisplaySettings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Display")
                    }
                    FilledTonalButton(onClick = {
                        viewModel.openQuickSettingAction(
                            { viewModel.controlManager.quickSettings.openLocationSettings() },
                            "Location Settings"
                        )
                    }) {
                        Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Location")
                    }
                }
            }
        }

        // 3. Flashlight
        item {
            ControlSectionCard(
                title = "Flashlight",
                icon = if (isFlashlightOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                badgeLabel = if (!isFlashlightAvailable) "UNAVAILABLE" else if (isFlashlightOn) "ON" else "OFF",
                badgeIsOn = isFlashlightOn,
                modifier = Modifier.testTag("card_flashlight")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isFlashlightAvailable) {
                                "Hardware Torch (${if (isFlashlightOn) "Active" else "Standby"})"
                            } else {
                                "No camera LED flash detected on this hardware"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Controlled directly via Android CameraManager Torch API",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isFlashlightOn,
                        onCheckedChange = { viewModel.toggleFlashlight(it) },
                        enabled = isFlashlightAvailable,
                        modifier = Modifier.testTag("flashlight_switch")
                    )
                }
            }
        }

        // 4. Volume
        item {
            val vol = telemetry.volume
            ControlSectionCard(
                title = "Volume",
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                badgeLabel = vol.ringerModeLabel.uppercase(),
                badgeIsOn = !vol.isRingerMuted,
                modifier = Modifier.testTag("card_volume")
            ) {
                VolumeSliderRow(
                    label = "Media",
                    percent = vol.mediaPercent,
                    onValueChangeFinished = { viewModel.setVolume(AudioStreamType.MEDIA, it) },
                    onStep = { inc -> viewModel.adjustVolume(AudioStreamType.MEDIA, inc) }
                )
                VolumeSliderRow(
                    label = "Ringtone",
                    percent = vol.ringtonePercent,
                    onValueChangeFinished = { viewModel.setVolume(AudioStreamType.RINGTONE, it) },
                    onStep = { inc -> viewModel.adjustVolume(AudioStreamType.RINGTONE, inc) }
                )
                VolumeSliderRow(
                    label = "Notification",
                    percent = vol.notificationPercent,
                    onValueChangeFinished = { viewModel.setVolume(AudioStreamType.NOTIFICATION, it) },
                    onStep = { inc -> viewModel.adjustVolume(AudioStreamType.NOTIFICATION, inc) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.toggleMuteStream(AudioStreamType.MEDIA, true) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeMute, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mute Media")
                    }
                    OutlinedButton(
                        onClick = { viewModel.toggleMuteStream(AudioStreamType.MEDIA, false) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Unmute Media")
                    }
                }
            }
        }

        // 5. Brightness
        item {
            val bright = telemetry.brightness
            var sliderBrightness by remember(bright.levelPercent) {
                mutableFloatStateOf(bright.levelPercent.toFloat())
            }
            ControlSectionCard(
                title = "Brightness",
                icon = if (bright.levelPercent > 60) Icons.Rounded.BrightnessHigh else Icons.Rounded.BrightnessMedium,
                badgeLabel = "${bright.levelPercent}%",
                badgeIsOn = bright.canWriteSettings,
                modifier = Modifier.testTag("card_brightness")
            ) {
                if (!bright.canWriteSettings) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Grant Modify System Settings permission so Sara can change screen brightness.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.requestPermission(PermissionType.WRITE_SETTINGS) },
                                modifier = Modifier.testTag("grant_write_settings_button")
                            ) {
                                Text("Grant")
                            }
                        }
                    }
                }

                Slider(
                    value = sliderBrightness,
                    onValueChange = { sliderBrightness = it },
                    onValueChangeFinished = { viewModel.setBrightness(sliderBrightness.toInt()) },
                    valueRange = 1f..100f,
                    modifier = Modifier.testTag("brightness_slider")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(25, 50, 80, 100).forEach { pct ->
                        FilledTonalButton(
                            onClick = {
                                sliderBrightness = pct.toFloat()
                                viewModel.setBrightness(pct)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("$pct%")
                        }
                    }
                }
            }
        }

        // 6. Battery Information
        item { BatteryInformationCard(battery = telemetry.battery) }

        // 7. Storage Information
        item {
            StorageInformationCard(
                storage = telemetry.storage,
                formatBytes = { viewModel.formatStorageSize(it) }
            )
        }

        // 8. Network Information
        item { NetworkInformationCard(network = telemetry.network) }

        // 9. Device Information
        item { DeviceInformationCard(device = telemetry.device) }

        // 10. Permission Status
        item {
            PermissionStatusCard(
                permissions = telemetry.permissions,
                onRequestPermission = { viewModel.requestPermission(it) }
            )
        }
    }
}

@Composable
private fun VolumeSliderRow(
    label: String,
    percent: Int,
    onValueChangeFinished: (Int) -> Unit,
    onStep: (Boolean) -> Unit
) {
    var sliderVal by remember(percent) { mutableFloatStateOf(percent.toFloat()) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onStep(false) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = "Decrease $label volume")
                }
                Text(
                    text = "${sliderVal.toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = { onStep(true) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Increase $label volume")
                }
            }
        }
        Slider(
            value = sliderVal,
            onValueChange = { sliderVal = it },
            onValueChangeFinished = { onValueChangeFinished(sliderVal.toInt()) },
            valueRange = 0f..100f
        )
    }
}
