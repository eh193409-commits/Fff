package com.example.sara.control

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import kotlin.math.roundToInt

class StorageInformationManager(private val context: Context) {

    fun getStorageInfo(): StorageInfo {
        val dataDir = Environment.getDataDirectory()
        val statFs = StatFs(dataDir.path)
        val total = statFs.totalBytes.coerceAtLeast(1L)
        val available = statFs.availableBytes.coerceIn(0L, total)
        val used = (total - available).coerceAtLeast(0L)
        val usedPct = ((used.toDouble() / total.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)

        return StorageInfo(
            totalBytes = total,
            availableBytes = available,
            usedBytes = used,
            usedPercent = usedPct
        )
    }

    fun formatBytes(bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

    fun getSpokenStorageReport(): ControlResult {
        val info = getStorageInfo()
        val availStr = formatBytes(info.availableBytes)
        val totalStr = formatBytes(info.totalBytes)
        val usedStr = formatBytes(info.usedBytes)
        return ControlResult.Success(
            spokenResponse = "You have $availStr available out of $totalStr total internal storage. $usedStr is currently used, which is ${info.usedPercent} percent.",
            detail = "$availStr free / $totalStr total (${info.usedPercent}% used)"
        )
    }
}
