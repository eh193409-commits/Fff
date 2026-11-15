package com.example.sara.control

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FlashlightManager(private val context: Context) {

    private val cameraManager: CameraManager? =
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _isAvailable = MutableStateFlow(checkHardwareAvailability())
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val torchCameraId: String? by lazy { resolveTorchCameraId() }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId || torchCameraId == null) {
                _isTorchOn.value = enabled
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == torchCameraId) {
                _isTorchOn.value = false
            }
        }
    }

    init {
        try {
            cameraManager?.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
        } catch (_: Exception) {
            // Safe fallback on test/virtual environments
        }
    }

    fun release() {
        try {
            cameraManager?.unregisterTorchCallback(torchCallback)
        } catch (_: Exception) {
        }
    }

    private fun checkHardwareAvailability(): Boolean {
        val hasFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        return hasFeature && resolveTorchCameraId() != null
    }

    private fun resolveTorchCameraId(): String? {
        val manager = cameraManager ?: return null
        return try {
            manager.cameraIdList.firstOrNull { id ->
                val chars = manager.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                hasFlash && (facing == CameraCharacteristics.LENS_FACING_BACK || facing == null)
            } ?: manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
    }

    fun setFlashlight(enabled: Boolean): ControlResult {
        val manager = cameraManager
        val camId = torchCameraId
        if (manager == null || camId == null) {
            _isAvailable.value = false
            return ControlResult.Error(
                spokenResponse = "This device doesn't have an accessible camera flashlight.",
                technicalReason = "No camera with FLASH_INFO_AVAILABLE found."
            )
        }

        return try {
            manager.setTorchMode(camId, enabled)
            _isTorchOn.value = enabled
            ControlResult.Success(
                spokenResponse = if (enabled) "Flashlight is now on." else "Flashlight is now off.",
                detail = "Torch mode set to ${if (enabled) "ON" else "OFF"}"
            )
        } catch (e: SecurityException) {
            ControlResult.PermissionRequired(
                permission = PermissionType.CAMERA,
                spokenResponse = "Camera permission is required on this device to control the flashlight.",
                explanation = e.localizedMessage ?: "Camera permission denied"
            )
        } catch (e: CameraAccessException) {
            ControlResult.Error(
                spokenResponse = "The flashlight is currently unavailable because the camera is in use.",
                technicalReason = e.localizedMessage ?: "CameraAccessException"
            )
        } catch (e: Exception) {
            ControlResult.Error(
                spokenResponse = "I couldn't change the flashlight state.",
                technicalReason = e.localizedMessage ?: "Unknown torch error"
            )
        }
    }

    fun toggleFlashlight(): ControlResult = setFlashlight(!_isTorchOn.value)
}
