package com.example.sara.control

import android.graphics.Rect

enum class PermissionType {
    RECORD_AUDIO,
    CAMERA,
    WRITE_SETTINGS,
    ACCESSIBILITY_SERVICE,
    NOTIFICATION_POLICY
}

enum class ScrollDirection { UP, DOWN, TOP, BOTTOM }

enum class SwipeDirection { LEFT, RIGHT, UP, DOWN }

enum class GlobalNavAction { BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS }

enum class AudioStreamType(val label: String) {
    MEDIA("Media"),
    RINGTONE("Ringtone"),
    NOTIFICATION("Notification"),
    ALARM("Alarm")
}

data class InstalledAppInfo(
    val appName: String,
    val packageName: String
)

data class VisibleUiElement(
    val text: String,
    val contentDescription: String,
    val viewId: String,
    val className: String,
    val isClickable: Boolean,
    val isScrollable: Boolean,
    val bounds: Rect
) {
    val displayLabel: String
        get() = text.ifBlank { contentDescription }.ifBlank {
            viewId.substringAfterLast('/').ifBlank { className.substringAfterLast('.') }
        }
}

data class BatteryInfo(
    val levelPercent: Int,
    val isCharging: Boolean,
    val chargingSource: String,
    val temperatureCelsius: Float,
    val voltageMv: Int,
    val healthLabel: String,
    val technology: String
)

data class StorageInfo(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long,
    val usedPercent: Int
)

data class NetworkInfo(
    val isInternetConnected: Boolean,
    val isWifiConnected: Boolean,
    val isCellularConnected: Boolean,
    val transportLabel: String,
    val isMetered: Boolean,
    val downstreamKbps: Int,
    val upstreamKbps: Int,
    val cellularGenerationHint: String
)

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val brand: String,
    val deviceCode: String,
    val androidVersion: String,
    val sdkInt: Int,
    val securityPatch: String,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val uptimeFormatted: String
)

data class VolumeSnapshot(
    val mediaPercent: Int,
    val ringtonePercent: Int,
    val notificationPercent: Int,
    val alarmPercent: Int,
    val isMediaMuted: Boolean,
    val isRingerMuted: Boolean,
    val ringerModeLabel: String
)

data class BrightnessInfo(
    val levelPercent: Int,
    val rawValue: Int,
    val isAutoMode: Boolean,
    val canWriteSettings: Boolean
)

data class PermissionStatusSnapshot(
    val recordAudioGranted: Boolean,
    val cameraGranted: Boolean,
    val writeSettingsGranted: Boolean,
    val accessibilityEnabled: Boolean,
    val notificationPolicyGranted: Boolean
)

data class PendingSensitiveAction(
    val id: String,
    val title: String,
    val description: String,
    val targetLabel: String,
    val onConfirmAction: suspend () -> ControlResult
)

sealed interface ControlResult {
    val spokenResponse: String

    data class Success(
        override val spokenResponse: String,
        val detail: String = ""
    ) : ControlResult

    data class PermissionRequired(
        val permission: PermissionType,
        override val spokenResponse: String,
        val explanation: String
    ) : ControlResult

    data class ConfirmationRequired(
        override val spokenResponse: String,
        val pendingAction: PendingSensitiveAction
    ) : ControlResult

    data class Restricted(
        override val spokenResponse: String,
        val restrictionReason: String
    ) : ControlResult

    data class Error(
        override val spokenResponse: String,
        val technicalReason: String = ""
    ) : ControlResult
}
