package com.example.sara.control

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class BatteryInformationManager(private val context: Context) {

    fun getBatteryInfo(): BatteryInfo {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val statusIntent = context.registerReceiver(null, filter)

        val level = statusIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = statusIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val computedPct = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } else {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 0
        }

        val status = statusIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = statusIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val chargingSource = when {
            status == BatteryManager.BATTERY_STATUS_FULL -> "Full"
            plugged == BatteryManager.BATTERY_PLUGGED_AC -> "Charging (AC)"
            plugged == BatteryManager.BATTERY_PLUGGED_USB -> "Charging (USB)"
            plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Charging (Wireless)"
            isCharging -> "Charging"
            else -> "Discharging"
        }

        val tempTenths = statusIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempCelsius = tempTenths / 10f

        val voltageMv = statusIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val tech = statusIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.ifBlank { "Unknown" }
            ?: "Unknown"

        val healthInt = statusIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val healthLabel = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified Failure"
            else -> "Unknown"
        }

        return BatteryInfo(
            levelPercent = computedPct,
            isCharging = isCharging,
            chargingSource = chargingSource,
            temperatureCelsius = tempCelsius,
            voltageMv = voltageMv,
            healthLabel = healthLabel,
            technology = tech
        )
    }

    fun getSpokenBatteryReport(): ControlResult {
        val info = getBatteryInfo()
        val chargePhrase = if (info.isCharging) {
            "and it is currently ${info.chargingSource.lowercase()}"
        } else {
            "and it is not charging"
        }
        val tempPhrase = if (info.temperatureCelsius > 0f) {
            " Temperature is ${String.format("%.1f", info.temperatureCelsius)} degrees Celsius, and battery health is ${info.healthLabel}."
        } else {
            " Battery health is ${info.healthLabel}."
        }
        return ControlResult.Success(
            spokenResponse = "Your phone battery is at ${info.levelPercent} percent, $chargePhrase.$tempPhrase",
            detail = "${info.levelPercent}% · ${info.chargingSource} · ${info.healthLabel}"
        )
    }
}
