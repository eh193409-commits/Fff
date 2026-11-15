package com.example.sara.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.sara.control.BatteryInfo
import com.example.sara.control.DeviceInfo
import com.example.sara.control.NetworkInfo
import com.example.sara.control.PermissionStatusSnapshot
import com.example.sara.control.PermissionType
import com.example.sara.control.StorageInfo
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldActive

@Composable
fun StatusBadge(
    label: String,
    isOn: Boolean,
    onColor: Color = EmeraldActive,
    offColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val accent = if (isOn) onColor else offColor
    Surface(
        color = accent.copy(alpha = 0.16f),
        shape = CircleShape,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ControlSectionCard(
    title: String,
    icon: ImageVector,
    badgeLabel: String? = null,
    badgeIsOn: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(7.dp)
                                .size(20.dp)
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (badgeLabel != null) {
                    StatusBadge(label = badgeLabel, isOn = badgeIsOn)
                }
            }
            content()
        }
    }
}

@Composable
fun BatteryInformationCard(battery: BatteryInfo) {
    ControlSectionCard(
        title = "Battery Information",
        icon = if (battery.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
        badgeLabel = if (battery.isCharging) "CHARGING" else "${battery.levelPercent}%",
        badgeIsOn = battery.isCharging || battery.levelPercent > 25,
        modifier = Modifier.testTag("card_battery_info")
    ) {
        LinearProgressIndicator(
            progress = { battery.levelPercent / 100f },
            color = if (battery.levelPercent <= 20) MaterialTheme.colorScheme.error else EmeraldActive,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
        )
        TelemetryRow("Battery Level", "${battery.levelPercent}%")
        TelemetryRow("Charging Status", battery.chargingSource)
        TelemetryRow("Temperature", "${String.format("%.1f", battery.temperatureCelsius)} °C")
        TelemetryRow("Health", battery.healthLabel)
        TelemetryRow("Voltage / Tech", "${battery.voltageMv} mV · ${battery.technology}")
    }
}

@Composable
fun StorageInformationCard(
    storage: StorageInfo,
    formatBytes: (Long) -> String
) {
    ControlSectionCard(
        title = "Storage Information",
        icon = Icons.Rounded.Storage,
        badgeLabel = "${storage.usedPercent}% USED",
        badgeIsOn = storage.usedPercent < 85,
        modifier = Modifier.testTag("card_storage_info")
    ) {
        LinearProgressIndicator(
            progress = { storage.usedPercent / 100f },
            color = if (storage.usedPercent >= 90) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
        )
        TelemetryRow("Total Internal Storage", formatBytes(storage.totalBytes))
        TelemetryRow("Available Space", formatBytes(storage.availableBytes))
        TelemetryRow("Used Space", "${formatBytes(storage.usedBytes)} (${storage.usedPercent}%)")
    }
}

@Composable
fun NetworkInformationCard(network: NetworkInfo) {
    ControlSectionCard(
        title = "Network Information",
        icon = if (network.isWifiConnected) Icons.Rounded.Wifi
        else if (network.isCellularConnected) Icons.Rounded.CellTower
        else Icons.Rounded.WifiOff,
        badgeLabel = if (network.isInternetConnected) "ONLINE" else "OFFLINE",
        badgeIsOn = network.isInternetConnected,
        modifier = Modifier.testTag("card_network_info")
    ) {
        TelemetryRow("Internet Connectivity", if (network.isInternetConnected) "Connected" else "Disconnected")
        TelemetryRow("Wi-Fi Status", if (network.isWifiConnected) "ON · Connected" else "OFF / Disconnected")
        TelemetryRow("Mobile Network", if (network.isCellularConnected) "ON · ${network.cellularGenerationHint}" else "Inactive")
        TelemetryRow("Network Type", network.transportLabel)
        TelemetryRow("Metered Connection", if (network.isMetered) "Yes" else "No")
        if (network.downstreamKbps > 0) {
            val downMbps = String.format("%.1f", network.downstreamKbps / 1000f)
            val upMbps = String.format("%.1f", network.upstreamKbps / 1000f)
            TelemetryRow("Link Bandwidth", "↓ $downMbps Mbps / ↑ $upMbps Mbps")
        }
    }
}

@Composable
fun DeviceInformationCard(device: DeviceInfo) {
    ControlSectionCard(
        title = "Device Information",
        icon = Icons.Rounded.PhoneAndroid,
        badgeLabel = "API ${device.sdkInt}",
        badgeIsOn = true,
        modifier = Modifier.testTag("card_device_info")
    ) {
        TelemetryRow("Manufacturer", device.manufacturer)
        TelemetryRow("Model", "${device.brand} ${device.model} (${device.deviceCode})")
        TelemetryRow("Android Version", "Android ${device.androidVersion} (SDK ${device.sdkInt})")
        TelemetryRow("Security Patch", device.securityPatch)
        TelemetryRow(
            "Available RAM",
            "${String.format("%.2f", device.availableRamMb / 1024f)} GB / ${String.format("%.2f", device.totalRamMb / 1024f)} GB"
        )
        TelemetryRow("Device Uptime", device.uptimeFormatted)
    }
}

@Composable
fun PermissionStatusCard(
    permissions: PermissionStatusSnapshot,
    onRequestPermission: (PermissionType) -> Unit
) {
    ControlSectionCard(
        title = "Permission Status",
        icon = Icons.Rounded.Security,
        badgeLabel = "${
            listOf(
                permissions.recordAudioGranted,
                permissions.cameraGranted,
                permissions.writeSettingsGranted,
                permissions.accessibilityEnabled,
                permissions.notificationPolicyGranted
            ).count { it }
        }/5 READY",
        badgeIsOn = permissions.recordAudioGranted && permissions.writeSettingsGranted,
        modifier = Modifier.testTag("card_permission_status")
    ) {
        PermissionRow(
            type = PermissionType.RECORD_AUDIO,
            granted = permissions.recordAudioGranted,
            actionLabel = "Grant Mic",
            onAction = { onRequestPermission(PermissionType.RECORD_AUDIO) }
        )
        PermissionRow(
            type = PermissionType.WRITE_SETTINGS,
            granted = permissions.writeSettingsGranted,
            actionLabel = "Allow",
            onAction = { onRequestPermission(PermissionType.WRITE_SETTINGS) }
        )
        PermissionRow(
            type = PermissionType.ACCESSIBILITY_SERVICE,
            granted = permissions.accessibilityEnabled,
            actionLabel = "Enable",
            onAction = { onRequestPermission(PermissionType.ACCESSIBILITY_SERVICE) }
        )
        PermissionRow(
            type = PermissionType.CAMERA,
            granted = permissions.cameraGranted,
            actionLabel = "Grant Cam",
            onAction = { onRequestPermission(PermissionType.CAMERA) }
        )
        PermissionRow(
            type = PermissionType.NOTIFICATION_POLICY,
            granted = permissions.notificationPolicyGranted,
            actionLabel = "Allow DND",
            onAction = { onRequestPermission(PermissionType.NOTIFICATION_POLICY) }
        )
    }
}

@Composable
private fun PermissionRow(
    type: PermissionType,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                contentDescription = if (granted) "Granted" else "Not granted",
                tint = if (granted) EmeraldActive else AmberWarning,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = permissionTitle(type),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = permissionExplanation(type),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (granted) {
                StatusBadge(label = "ON", isOn = true)
            } else {
                Button(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("permission_btn_${type.name.lowercase()}")
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
