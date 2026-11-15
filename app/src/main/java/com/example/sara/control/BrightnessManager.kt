package com.example.sara.control

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlin.math.roundToInt

class BrightnessManager(private val context: Context) {

    fun canWriteSettings(): Boolean = Settings.System.canWrite(context)

    fun openWriteSettingsIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun getBrightnessInfo(): BrightnessInfo {
        val resolver = context.contentResolver
        val raw = try {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        } catch (_: Exception) {
            128
        }.coerceIn(0, 255)

        val mode = try {
            Settings.System.getInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
        } catch (_: Exception) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        }

        val percent = ((raw / 255f) * 100f).roundToInt().coerceIn(0, 100)
        return BrightnessInfo(
            levelPercent = percent,
            rawValue = raw,
            isAutoMode = mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
            canWriteSettings = canWriteSettings()
        )
    }

    fun setBrightnessPercent(percent: Int): ControlResult {
        if (!canWriteSettings()) {
            return ControlResult.PermissionRequired(
                permission = PermissionType.WRITE_SETTINGS,
                spokenResponse = "Please grant Modify System Settings permission so I can adjust your screen brightness.",
                explanation = "Android requires WRITE_SETTINGS approval before apps can change system screen brightness."
            )
        }

        val clamped = percent.coerceIn(1, 100)
        val rawValue = ((clamped / 100f) * 255f).roundToInt().coerceIn(2, 255)
        return try {
            val resolver = context.contentResolver
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS,
                rawValue
            )
            ControlResult.Success(
                spokenResponse = "Brightness adjusted to $clamped percent.",
                detail = "Screen brightness: $clamped% ($rawValue/255)"
            )
        } catch (e: Exception) {
            ControlResult.Error(
                spokenResponse = "I couldn't change the screen brightness.",
                technicalReason = e.localizedMessage ?: "Settings.System write failed"
            )
        }
    }

    fun increaseBrightness(stepPercent: Int = 15): ControlResult {
        val current = getBrightnessInfo().levelPercent
        return setBrightnessPercent((current + stepPercent).coerceAtMost(100))
    }

    fun decreaseBrightness(stepPercent: Int = 15): ControlResult {
        val current = getBrightnessInfo().levelPercent
        return setBrightnessPercent((current - stepPercent).coerceAtLeast(5))
    }
}
