package com.example.sara.control

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

class QuickSettingsManager(private val context: Context) {

    fun openNotificationShade(): ControlResult {
        val service = AccessibilityBridge.getService()
        if (service != null) {
            return service.performGlobalNavigation(GlobalNavAction.NOTIFICATIONS)
        }
        return ControlResult.PermissionRequired(
            permission = PermissionType.ACCESSIBILITY_SERVICE,
            spokenResponse = "Please enable Sara's Accessibility Service so I can pull down the notification shade for you.",
            explanation = "Android restricts programmatic notification shade expansion to AccessibilityService."
        )
    }

    fun openQuickSettingsPanel(): ControlResult {
        val service = AccessibilityBridge.getService()
        if (service != null) {
            return service.performGlobalNavigation(GlobalNavAction.QUICK_SETTINGS)
        }
        return ControlResult.PermissionRequired(
            permission = PermissionType.ACCESSIBILITY_SERVICE,
            spokenResponse = "Enable Sara's Accessibility Service so I can open the Android Quick Settings panel directly.",
            explanation = "Opening the system Quick Settings tile drawer requires AccessibilityService global action support."
        )
    }

    fun openWifiPanel(): ControlResult {
        return launchSettingsPanelOrFallback(
            panelAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_WIFI else null,
            fallbackAction = Settings.ACTION_WIFI_SETTINGS,
            spoken = "Opening Wi-Fi controls. Android 10 and newer restricts third-party apps from toggling Wi-Fi silently in the background, so you can tap the toggle directly here.",
            detail = "Wi-Fi Panel"
        )
    }

    fun openInternetConnectivityPanel(): ControlResult {
        return launchSettingsPanelOrFallback(
            panelAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else null,
            fallbackAction = Settings.ACTION_WIRELESS_SETTINGS,
            spoken = "Opening Internet & Mobile Data controls. Android requires user confirmation to toggle Wi-Fi or mobile data.",
            detail = "Internet Panel"
        )
    }

    fun openVolumePanel(): ControlResult {
        return launchSettingsPanelOrFallback(
            panelAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_VOLUME else null,
            fallbackAction = Settings.ACTION_SOUND_SETTINGS,
            spoken = "Opening system volume panel.",
            detail = "Volume Panel"
        )
    }

    fun openBluetoothSettings(): ControlResult {
        return launchIntent(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
            spoken = "Opening Bluetooth settings. On Android 12+, system Bluetooth toggles require user interaction or Bluetooth Connect permissions.",
            detail = "Bluetooth Settings"
        )
    }

    fun openLocationSettings(): ControlResult {
        return launchIntent(
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            spoken = "Opening Location settings. Android prohibits third-party apps from toggling GPS silently.",
            detail = "Location Settings"
        )
    }

    fun openAirplaneModeSettings(): ControlResult {
        return launchIntent(
            Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS),
            spoken = "Opening Airplane Mode settings. Android restricts Airplane Mode toggles to system apps.",
            detail = "Airplane Mode Settings"
        )
    }

    fun openDisplaySettings(): ControlResult {
        return launchIntent(
            Intent(Settings.ACTION_DISPLAY_SETTINGS),
            spoken = "Opening Display & Dark Mode settings.",
            detail = "Display Settings"
        )
    }

    private fun launchSettingsPanelOrFallback(
        panelAction: String?,
        fallbackAction: String,
        spoken: String,
        detail: String
    ): ControlResult {
        if (panelAction != null) {
            val panelIntent = Intent(panelAction)
            val panelResult = launchIntent(panelIntent, spoken, detail)
            if (panelResult is ControlResult.Success) return panelResult
        }
        return launchIntent(Intent(fallbackAction), spoken, detail)
    }

    private fun launchIntent(intent: Intent, spoken: String, detail: String): ControlResult {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ControlResult.Success(spokenResponse = spoken, detail = detail)
        } catch (e: Exception) {
            ControlResult.Restricted(
                spokenResponse = "Your device manufacturer restricts opening $detail directly.",
                restrictionReason = e.localizedMessage ?: "Activity not found"
            )
        }
    }
}
