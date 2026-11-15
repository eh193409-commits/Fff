package com.example.sara.control

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

class AppLauncherManager(private val context: Context) {

    private val commonAliases: Map<String, List<String>> = mapOf(
        "youtube" to listOf("com.google.android.youtube", "youtube"),
        "whatsapp" to listOf("com.whatsapp", "whatsapp"),
        "chrome" to listOf("com.android.chrome", "chrome", "google chrome"),
        "settings" to listOf("com.android.settings", "settings"),
        "camera" to listOf("camera", "com.android.camera", "com.google.android.googlecamera"),
        "maps" to listOf("com.google.android.apps.maps", "maps", "google maps"),
        "gmail" to listOf("com.google.android.gm", "gmail"),
        "play store" to listOf("com.android.vending", "play store", "google play"),
        "phone" to listOf("com.google.android.dialer", "com.android.dialer", "phone"),
        "messages" to listOf("com.google.android.apps.messaging", "messages"),
        "calculator" to listOf("com.google.android.calculator", "calculator"),
        "clock" to listOf("com.google.android.deskclock", "clock"),
        "calendar" to listOf("com.google.android.calendar", "calendar"),
        "photos" to listOf("com.google.android.apps.photos", "photos", "gallery"),
        "spotify" to listOf("com.spotify.music", "spotify"),
        "instagram" to listOf("com.instagram.android", "instagram"),
        "telegram" to listOf("org.telegram.messenger", "telegram")
    )

    fun getInstalledLaunchableApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                mainIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        return resolveInfos
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isBlank()) return@mapNotNull null
                InstalledAppInfo(appName = label, packageName = pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
    }

    fun openAppByName(requestedName: String): ControlResult {
        val cleanedQuery = requestedName
            .lowercase()
            .replace("the app", "")
            .replace("app", "")
            .trim()

        if (cleanedQuery.isBlank()) {
            return ControlResult.Error("Please tell me which app you want to open.")
        }

        // Direct Android Settings shortcut
        if (cleanedQuery == "settings" || cleanedQuery == "system settings" || cleanedQuery == "phone settings") {
            return launchSettings()
        }

        val installed = getInstalledLaunchableApps()

        // 1. Exact case-insensitive match
        val exact = installed.firstOrNull { it.appName.equals(cleanedQuery, ignoreCase = true) }
        if (exact != null) {
            return launchPackage(exact.packageName, exact.appName)
        }

        // 2. Alias package or label match
        val aliasCandidates = commonAliases.entries
            .firstOrNull { (key, _) -> cleanedQuery == key || cleanedQuery.contains(key) }
            ?.value
            .orEmpty()

        for (candidate in aliasCandidates) {
            val byPkg = installed.firstOrNull { it.packageName.equals(candidate, ignoreCase = true) }
            if (byPkg != null) return launchPackage(byPkg.packageName, byPkg.appName)

            val byLabel = installed.firstOrNull { it.appName.contains(candidate, ignoreCase = true) }
            if (byLabel != null) return launchPackage(byLabel.packageName, byLabel.appName)
        }

        // 3. Starts-with match
        val startsWith = installed.firstOrNull {
            it.appName.lowercase().startsWith(cleanedQuery)
        }
        if (startsWith != null) {
            return launchPackage(startsWith.packageName, startsWith.appName)
        }

        // 4. Contains match
        val contains = installed.firstOrNull {
            it.appName.lowercase().contains(cleanedQuery) ||
                cleanedQuery.contains(it.appName.lowercase())
        }
        if (contains != null) {
            return launchPackage(contains.packageName, contains.appName)
        }

        return ControlResult.Error(
            spokenResponse = "$requestedName doesn't appear to be installed on this device.",
            technicalReason = "No launchable activity matched '$cleanedQuery'."
        )
    }

    fun launchPackage(packageName: String, displayName: String = packageName): ControlResult {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                ControlResult.Error(
                    spokenResponse = "I found $displayName, but Android does not expose a launch activity for it.",
                    technicalReason = "getLaunchIntentForPackage returned null for $packageName"
                )
            } else {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                ControlResult.Success(
                    spokenResponse = "Opening $displayName.",
                    detail = "Launched $packageName"
                )
            }
        } catch (e: Exception) {
            ControlResult.Error(
                spokenResponse = "I couldn't open $displayName.",
                technicalReason = e.localizedMessage ?: "Launch failed"
            )
        }
    }

    private fun launchSettings(): ControlResult {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ControlResult.Success(
                spokenResponse = "Opening Settings.",
                detail = "Launched Settings.ACTION_SETTINGS"
            )
        } catch (e: Exception) {
            ControlResult.Error(
                spokenResponse = "I couldn't open Android Settings.",
                technicalReason = e.localizedMessage ?: "Settings launch error"
            )
        }
    }
}
