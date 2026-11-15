package com.example.sara.control

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SmartphoneControlManager(private val context: Context) {

    val appLauncher = AppLauncherManager(context)
    val quickSettings = QuickSettingsManager(context)
    val flashlight = FlashlightManager(context)
    val volume = VolumeManager(context)
    val brightness = BrightnessManager(context)
    val battery = BatteryInformationManager(context)
    val storage = StorageInformationManager(context)
    val network = NetworkInformationManager(context)
    val device = DeviceInformationManager(context)

    val isFlashlightOn: StateFlow<Boolean> = flashlight.isTorchOn
    val isFlashlightAvailable: StateFlow<Boolean> = flashlight.isAvailable
    val isAccessibilityConnected: StateFlow<Boolean> = AccessibilityBridge.isConnected
    val activeWindowPackage: StateFlow<String?> = AccessibilityBridge.lastObservedPackage

    private val _pendingSensitiveAction = MutableStateFlow<PendingSensitiveAction?>(null)
    val pendingSensitiveAction: StateFlow<PendingSensitiveAction?> = _pendingSensitiveAction.asStateFlow()

    private val _visibleElements = MutableStateFlow<List<VisibleUiElement>>(emptyList())
    val visibleElements: StateFlow<List<VisibleUiElement>> = _visibleElements.asStateFlow()

    fun getPermissionSnapshot(): PermissionStatusSnapshot {
        val recordAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val camera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val writeSettings = brightness.canWriteSettings()
        val accessibility = AccessibilityBridge.isConnected.value ||
            AccessibilityBridge.isServiceEnabledInSettings(context)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val dndAccess = nm.isNotificationPolicyAccessGranted

        return PermissionStatusSnapshot(
            recordAudioGranted = recordAudio,
            cameraGranted = camera,
            writeSettingsGranted = writeSettings,
            accessibilityEnabled = accessibility,
            notificationPolicyGranted = dndAccess
        )
    }

    fun inspectScreenElements(): ControlResult {
        val service = AccessibilityBridge.getService()
            ?: return accessibilityRequiredResult("inspect visible UI elements on screen")
        val elements = service.inspectVisibleElements()
        _visibleElements.value = elements
        return if (elements.isEmpty()) {
            ControlResult.Success("No interactive elements detected on the current screen.")
        } else {
            val preview = elements.take(5).joinToString(", ") { it.displayLabel }
            ControlResult.Success(
                spokenResponse = "I found ${elements.size} visible UI elements, including $preview.",
                detail = "${elements.size} elements captured"
            )
        }
    }

    fun clickUiElement(query: String, bypassConfirmation: Boolean = false): ControlResult {
        val service = AccessibilityBridge.getService()
            ?: return accessibilityRequiredResult("click \"$query\"")
        val result = service.clickElementByQuery(query, bypassConfirmation)
        if (result is ControlResult.ConfirmationRequired) {
            _pendingSensitiveAction.value = result.pendingAction
        }
        return result
    }

    fun clickNthResult(indexZeroBased: Int, bypassConfirmation: Boolean = false): ControlResult {
        val service = AccessibilityBridge.getService()
            ?: return accessibilityRequiredResult("select result ${indexZeroBased + 1}")
        val result = service.clickNthResult(indexZeroBased, bypassConfirmation)
        if (result is ControlResult.ConfirmationRequired) {
            _pendingSensitiveAction.value = result.pendingAction
        }
        return result
    }

    fun scroll(direction: ScrollDirection): ControlResult {
        val service = AccessibilityBridge.getService()
            ?: return accessibilityRequiredResult("scroll ${direction.name.lowercase()}")
        return service.scroll(direction)
    }

    fun swipe(direction: SwipeDirection): ControlResult {
        val service = AccessibilityBridge.getService()
            ?: return accessibilityRequiredResult("swipe ${direction.name.lowercase()}")
        return service.swipe(direction)
    }

    fun navigateGlobal(action: GlobalNavAction): ControlResult {
        val service = AccessibilityBridge.getService()
            ?: return accessibilityRequiredResult("navigate ${action.name.lowercase()}")
        return service.performGlobalNavigation(action)
    }

    suspend fun confirmPendingAction(): ControlResult? {
        val pending = _pendingSensitiveAction.value ?: return null
        _pendingSensitiveAction.value = null
        return pending.onConfirmAction()
    }

    fun dismissPendingAction() {
        _pendingSensitiveAction.value = null
    }

    fun openPermissionSettings(type: PermissionType) {
        val intent = when (type) {
            PermissionType.WRITE_SETTINGS -> brightness.openWriteSettingsIntent()
            PermissionType.ACCESSIBILITY_SERVICE -> AccessibilityBridge.openAccessibilitySettingsIntent()
            PermissionType.NOTIFICATION_POLICY -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            PermissionType.RECORD_AUDIO, PermissionType.CAMERA -> Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            ).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        runCatching { context.startActivity(intent) }
    }

    fun buildDeviceSummaryForAi(): String {
        val bat = battery.getBatteryInfo()
        val net = network.getNetworkInfo()
        val stor = storage.getStorageInfo()
        return "Battery ${bat.levelPercent}% (${bat.chargingSource}), ${net.transportLabel}, Storage ${stor.usedPercent}% used"
    }

    fun release() {
        flashlight.release()
    }

    private fun accessibilityRequiredResult(actionLabel: String): ControlResult.PermissionRequired {
        return ControlResult.PermissionRequired(
            permission = PermissionType.ACCESSIBILITY_SERVICE,
            spokenResponse = "Please enable Sara's Accessibility Service in Android Settings so I can $actionLabel for you.",
            explanation = "Screen reading, clicking, scrolling, and swiping across apps require manual user opt-in to AccessibilityService."
        )
    }
}
