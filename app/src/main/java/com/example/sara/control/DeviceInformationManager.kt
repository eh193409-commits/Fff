package com.example.sara.control

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import java.util.concurrent.TimeUnit

class DeviceInformationManager(private val context: Context) {

    fun getDeviceInfo(): DeviceInfo {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024L * 1024L)
        val availRamMb = memInfo.availMem / (1024L * 1024L)

        val uptimeMs = SystemClock.elapsedRealtime()
        val hours = TimeUnit.MILLISECONDS.toHours(uptimeMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMs) % 60
        val uptimeFormatted = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

        return DeviceInfo(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            brand = Build.BRAND.replaceFirstChar { it.uppercase() },
            deviceCode = Build.DEVICE,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            uptimeFormatted = uptimeFormatted
        )
    }

    fun getSpokenDeviceReport(): ControlResult {
        val info = getDeviceInfo()
        val availGb = String.format("%.2f", info.availableRamMb / 1024f)
        val totalGb = String.format("%.2f", info.totalRamMb / 1024f)
        return ControlResult.Success(
            spokenResponse = "This is a ${info.manufacturer} ${info.model} running Android ${info.androidVersion}, SDK ${info.sdkInt}. You have $availGb gigabytes of RAM available out of $totalGb gigabytes, and device uptime is ${info.uptimeFormatted}.",
            detail = "${info.manufacturer} ${info.model} · Android ${info.androidVersion} (SDK ${info.sdkInt})"
        )
    }
}
