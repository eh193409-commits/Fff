package com.example.sara.control

import com.example.sara.ai.AiRepository
import com.example.sara.memory.MemoryRepository
import kotlinx.coroutines.delay

data class VoiceExecutionOutcome(
    val spokenResponse: String,
    val actionBadge: String?,
    val succeeded: Boolean,
    val isPlaceholderAi: Boolean = false,
    val permissionRequired: PermissionType? = null
)

class VoiceCommandRouter(
    private val controlManager: SmartphoneControlManager,
    private val memoryRepository: MemoryRepository,
    private val aiRepository: AiRepository
) {
    private val wakePrefixRegex = Regex("^(hey\\s+sara|ok\\s+sara|hi\\s+sara|sara)[,\\s:]*", RegexOption.IGNORE_CASE)
    private val percentRegex = Regex("(\\d{1,3})\\s*(%|percent)?", RegexOption.IGNORE_CASE)

    suspend fun routeAndExecute(rawInput: String): VoiceExecutionOutcome {
        val stripped = rawInput.trim().replace(wakePrefixRegex, "").trim()
        if (stripped.isBlank()) {
            return VoiceExecutionOutcome(
                spokenResponse = "Yes, I'm listening! What would you like me to do?",
                actionBadge = "Wake-Up",
                succeeded = true
            )
        }

        // Check memory storage command first before splitting
        val lowerFull = stripped.lowercase()
        if (lowerFull.startsWith("remember that ") || lowerFull.startsWith("remember ")) {
            val fact = stripped
                .replace(Regex("^remember\\s+(that\\s+)?", RegexOption.IGNORE_CASE), "")
                .trim()
            if (fact.isNotBlank()) {
                val category = when {
                    fact.contains("name", ignoreCase = true) -> "Profile"
                    fact.contains("favorite", ignoreCase = true) || fact.contains("prefer", ignoreCase = true) -> "Preference"
                    else -> "Note"
                }
                memoryRepository.rememberFact(category, fact)
                val response = "Got it, I'll remember that: $fact."
                memoryRepository.logCommand(stripped, "Saved long-term memory", true)
                return VoiceExecutionOutcome(response, "Memory Saved", true)
            }
        }

        if (lowerFull.contains("what do you remember") || lowerFull.contains("my memories") || lowerFull.contains("recall memory")) {
            val memories = memoryRepository.getMemoriesSnapshot()
            val response = if (memories.isEmpty()) {
                "I don't have any saved long-term memories yet. You can say, \"Remember that my favorite app is YouTube.\""
            } else {
                val preview = memories.take(3).joinToString("; ") { it.content }
                "I remember ${memories.size} thing${if (memories.size == 1) "" else "s"}: $preview."
            }
            return VoiceExecutionOutcome(response, "Memory Recall", true)
        }

        // Split compound commands like "Open Settings and scroll down"
        val segments = splitCompoundCommands(stripped)
        if (segments.size > 1) {
            val parsedAll = segments.map { parseSingleCommand(it) }
            if (parsedAll.all { it != null } && parsedAll.distinct().size > 1) {
                val spokenParts = mutableListOf<String>()
                var allOk = true
                var lastPermission: PermissionType? = null

                for ((index, action) in parsedAll.withIndex()) {
                    if (index > 0) {
                        // Give newly launched activity a brief window to render before UI automation
                        delay(750L)
                    }
                    val result = executeParsedCommand(action!!)
                    spokenParts.add(result.spokenResponse)
                    if (result !is ControlResult.Success) {
                        allOk = false
                    }
                    if (result is ControlResult.PermissionRequired) {
                        lastPermission = result.permission
                    }
                }

                val combinedSpeech = spokenParts.joinToString(" ")
                memoryRepository.logCommand(stripped, "Compound (${segments.size} steps)", allOk)
                return VoiceExecutionOutcome(
                    spokenResponse = combinedSpeech,
                    actionBadge = "Multi-Step Control",
                    succeeded = allOk,
                    permissionRequired = lastPermission
                )
            }
        }

        // Single device command
        val singleAction = parseSingleCommand(stripped)
        if (singleAction != null) {
            val result = executeParsedCommand(singleAction)
            val ok = result is ControlResult.Success
            val badge = badgeFor(singleAction)
            memoryRepository.logCommand(stripped, result.spokenResponse, ok)
            return VoiceExecutionOutcome(
                spokenResponse = result.spokenResponse,
                actionBadge = badge,
                succeeded = ok,
                permissionRequired = (result as? ControlResult.PermissionRequired)?.permission
            )
        }

        // Fallback to Sara AI conversational brain
        val memories = memoryRepository.getMemoriesSnapshot()
        val deviceSummary = controlManager.buildDeviceSummaryForAi()
        val aiReply = aiRepository.generateConversationalReply(
            userMessage = stripped,
            memories = memories,
            deviceSummary = deviceSummary
        )
        return VoiceExecutionOutcome(
            spokenResponse = aiReply.text,
            actionBadge = if (aiReply.isPlaceholderNotice) "Sara Chat (Local)" else "Sara Chat",
            succeeded = true,
            isPlaceholderAi = aiReply.isPlaceholderNotice
        )
    }

    private fun splitCompoundCommands(input: String): List<String> {
        return input.split(Regex("\\s+(?:and\\s+then|then|and)\\s+|,\\s*", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    internal sealed interface ParsedCommand {
        data class OpenApp(val appName: String) : ParsedCommand
        data class ClickElement(val query: String) : ParsedCommand
        data class ClickResultIndex(val zeroBasedIndex: Int) : ParsedCommand
        data class Scroll(val direction: ScrollDirection) : ParsedCommand
        data class Swipe(val direction: SwipeDirection) : ParsedCommand
        data class GlobalNav(val action: GlobalNavAction) : ParsedCommand
        data object InspectScreen : ParsedCommand
        data object OpenWifiPanel : ParsedCommand
        data object OpenInternetPanel : ParsedCommand
        data object OpenBluetoothSettings : ParsedCommand
        data object OpenLocationSettings : ParsedCommand
        data object OpenDisplaySettings : ParsedCommand
        data object OpenVolumePanel : ParsedCommand
        data class Flashlight(val enabled: Boolean?) : ParsedCommand
        data class VolumeAdjust(val stream: AudioStreamType, val delta: Int) : ParsedCommand
        data class VolumeSet(val stream: AudioStreamType, val percent: Int) : ParsedCommand
        data class VolumeMute(val stream: AudioStreamType, val mute: Boolean) : ParsedCommand
        data class BrightnessAdjust(val delta: Int) : ParsedCommand
        data class BrightnessSet(val percent: Int) : ParsedCommand
        data object BrightnessRead : ParsedCommand
        data object BatteryReport : ParsedCommand
        data object StorageReport : ParsedCommand
        data object NetworkReport : ParsedCommand
        data object DeviceReport : ParsedCommand
    }

    internal fun parseSingleCommand(segment: String): ParsedCommand? {
        val text = segment.trim()
        val lower = text.lowercase()

        // Quick settings & notifications
        if (lower.contains("quick settings")) {
            return ParsedCommand.GlobalNav(GlobalNavAction.QUICK_SETTINGS)
        }
        if (lower.contains("notification shade") || lower.contains("notifications panel") || lower == "open notifications") {
            return ParsedCommand.GlobalNav(GlobalNavAction.NOTIFICATIONS)
        }
        if (lower.contains("wifi settings") || lower.contains("wi-fi settings") || lower.contains("wifi panel") || lower.contains("turn on wifi") || lower.contains("turn off wifi")) {
            return ParsedCommand.OpenWifiPanel
        }
        if (lower.contains("internet panel") || lower.contains("mobile data settings") || lower.contains("turn on mobile data")) {
            return ParsedCommand.OpenInternetPanel
        }
        if (lower.contains("bluetooth settings") || lower.contains("open bluetooth") || lower.contains("turn on bluetooth") || lower.contains("turn off bluetooth")) {
            return ParsedCommand.OpenBluetoothSettings
        }
        if (lower.contains("location settings") || lower.contains("gps settings")) {
            return ParsedCommand.OpenLocationSettings
        }
        if (lower.contains("display settings")) {
            return ParsedCommand.OpenDisplaySettings
        }
        if (lower.contains("volume panel")) {
            return ParsedCommand.OpenVolumePanel
        }

        // Flashlight
        if (lower.contains("flashlight") || lower.contains("torch")) {
            return when {
                lower.contains("off") || lower.contains("disable") -> ParsedCommand.Flashlight(false)
                lower.contains("on") || lower.contains("enable") -> ParsedCommand.Flashlight(true)
                else -> ParsedCommand.Flashlight(null)
            }
        }

        // Brightness
        if (lower.contains("brightness")) {
            val pctMatch = percentRegex.find(lower)
            val pct = pctMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
            return when {
                lower.contains("what") || lower.contains("current") || lower.contains("read") || lower.contains("check") ->
                    ParsedCommand.BrightnessRead
                pct != null -> ParsedCommand.BrightnessSet(pct)
                lower.contains("increase") || lower.contains("up") || lower.contains("higher") || lower.contains("raise") || lower.contains("brighter") ->
                    ParsedCommand.BrightnessAdjust(+15)
                lower.contains("decrease") || lower.contains("down") || lower.contains("lower") || lower.contains("dim") ->
                    ParsedCommand.BrightnessAdjust(-15)
                else -> ParsedCommand.BrightnessRead
            }
        }

        // Volume
        if (lower.contains("volume") || lower == "mute" || lower == "unmute" || lower.startsWith("mute ") || lower.startsWith("unmute ")) {
            val stream = when {
                lower.contains("ringtone") || lower.contains("ringer") -> AudioStreamType.RINGTONE
                lower.contains("notification") -> AudioStreamType.NOTIFICATION
                lower.contains("alarm") -> AudioStreamType.ALARM
                else -> AudioStreamType.MEDIA
            }
            if (lower.contains("unmute")) return ParsedCommand.VolumeMute(stream, false)
            if (lower.contains("mute")) return ParsedCommand.VolumeMute(stream, true)

            val pctMatch = percentRegex.find(lower)
            val pct = pctMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (pct != null && (lower.contains("set") || lower.contains("to") || lower.contains("%") || lower.contains("percent"))) {
                return ParsedCommand.VolumeSet(stream, pct)
            }
            if (lower.contains("increase") || lower.contains("up") || lower.contains("raise") || lower.contains("louder") || lower.contains("higher")) {
                return ParsedCommand.VolumeAdjust(stream, +1)
            }
            if (lower.contains("decrease") || lower.contains("down") || lower.contains("lower") || lower.contains("quieter")) {
                return ParsedCommand.VolumeAdjust(stream, -1)
            }
        }

        // Battery, Storage, Network, Device Info
        if (lower.contains("battery") || lower.contains("charging")) {
            return ParsedCommand.BatteryReport
        }
        if (lower.contains("storage") || lower.contains("disk space") || lower.contains("internal space")) {
            return ParsedCommand.StorageReport
        }
        if (lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("network") || lower.contains("internet") || lower.contains("mobile data")) {
            return ParsedCommand.NetworkReport
        }
        if (lower.contains("device info") || lower.contains("phone model") || lower.contains("what phone") || lower.contains("android version") || lower.contains("sdk version") || lower.contains("ram") || lower.contains("uptime") || lower.contains("manufacturer")) {
            return ParsedCommand.DeviceReport
        }

        // Accessibility screen navigation, scrolling, swiping, clicking
        if (lower.contains("scan screen") || lower.contains("visible elements") || lower.contains("what is on screen") || lower.contains("read screen")) {
            return ParsedCommand.InspectScreen
        }
        if (lower == "go back" || lower == "back" || lower == "navigate back") {
            return ParsedCommand.GlobalNav(GlobalNavAction.BACK)
        }
        if (lower == "go home" || lower == "home screen" || lower == "go to home") {
            return ParsedCommand.GlobalNav(GlobalNavAction.HOME)
        }
        if (lower.contains("recent apps") || lower == "recents") {
            return ParsedCommand.GlobalNav(GlobalNavAction.RECENTS)
        }

        if (lower.contains("go to the bottom") || lower.contains("scroll to bottom") || lower.contains("scroll to the bottom")) {
            return ParsedCommand.Scroll(ScrollDirection.BOTTOM)
        }
        if (lower.contains("go to the top") || lower.contains("scroll to top") || lower.contains("scroll to the top")) {
            return ParsedCommand.Scroll(ScrollDirection.TOP)
        }
        if (lower.contains("scroll down")) return ParsedCommand.Scroll(ScrollDirection.DOWN)
        if (lower.contains("scroll up")) return ParsedCommand.Scroll(ScrollDirection.UP)

        if (lower.contains("swipe left")) return ParsedCommand.Swipe(SwipeDirection.LEFT)
        if (lower.contains("swipe right")) return ParsedCommand.Swipe(SwipeDirection.RIGHT)
        if (lower.contains("swipe up")) return ParsedCommand.Swipe(SwipeDirection.UP)
        if (lower.contains("swipe down")) return ParsedCommand.Swipe(SwipeDirection.DOWN)

        if (lower.contains("first result") || lower.contains("1st result")) {
            return ParsedCommand.ClickResultIndex(0)
        }
        if (lower.contains("second result") || lower.contains("2nd result")) {
            return ParsedCommand.ClickResultIndex(1)
        }
        if (lower.contains("third result") || lower.contains("3rd result")) {
            return ParsedCommand.ClickResultIndex(2)
        }

        val clickMatch = Regex("^(?:click|tap|press|select)\\s+(?:on\\s+)?(?:the\\s+)?(.+)$", RegexOption.IGNORE_CASE)
            .find(text)
        if (clickMatch != null) {
            val target = clickMatch.groupValues[1].trim()
            if (target.isNotBlank()) return ParsedCommand.ClickElement(target)
        }

        // Open app
        val openMatch = Regex("^(?:open|launch|start|run)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(text)
        if (openMatch != null) {
            val appName = openMatch.groupValues[1].trim()
            if (appName.isNotBlank()) return ParsedCommand.OpenApp(appName)
        }

        return null
    }

    private fun executeParsedCommand(command: ParsedCommand): ControlResult {
        return when (command) {
            is ParsedCommand.OpenApp -> controlManager.appLauncher.openAppByName(command.appName)
            is ParsedCommand.ClickElement -> controlManager.clickUiElement(command.query)
            is ParsedCommand.ClickResultIndex -> controlManager.clickNthResult(command.zeroBasedIndex)
            is ParsedCommand.Scroll -> controlManager.scroll(command.direction)
            is ParsedCommand.Swipe -> controlManager.swipe(command.direction)
            is ParsedCommand.GlobalNav -> controlManager.navigateGlobal(command.action)
            ParsedCommand.InspectScreen -> controlManager.inspectScreenElements()
            ParsedCommand.OpenWifiPanel -> controlManager.quickSettings.openWifiPanel()
            ParsedCommand.OpenInternetPanel -> controlManager.quickSettings.openInternetConnectivityPanel()
            ParsedCommand.OpenBluetoothSettings -> controlManager.quickSettings.openBluetoothSettings()
            ParsedCommand.OpenLocationSettings -> controlManager.quickSettings.openLocationSettings()
            ParsedCommand.OpenDisplaySettings -> controlManager.quickSettings.openDisplaySettings()
            ParsedCommand.OpenVolumePanel -> controlManager.quickSettings.openVolumePanel()
            is ParsedCommand.Flashlight -> {
                if (command.enabled == null) controlManager.flashlight.toggleFlashlight()
                else controlManager.flashlight.setFlashlight(command.enabled)
            }
            is ParsedCommand.VolumeAdjust -> {
                if (command.delta > 0) controlManager.volume.increaseVolume(command.stream)
                else controlManager.volume.decreaseVolume(command.stream)
            }
            is ParsedCommand.VolumeSet -> controlManager.volume.setVolumePercent(command.percent, command.stream)
            is ParsedCommand.VolumeMute -> {
                if (command.mute) controlManager.volume.mute(command.stream)
                else controlManager.volume.unmute(command.stream)
            }
            is ParsedCommand.BrightnessAdjust -> {
                if (command.delta > 0) controlManager.brightness.increaseBrightness(command.delta)
                else controlManager.brightness.decreaseBrightness(-command.delta)
            }
            is ParsedCommand.BrightnessSet -> controlManager.brightness.setBrightnessPercent(command.percent)
            ParsedCommand.BrightnessRead -> {
                val info = controlManager.brightness.getBrightnessInfo()
                ControlResult.Success(
                    spokenResponse = "Current screen brightness is ${info.levelPercent} percent${if (info.isAutoMode) " in adaptive mode" else ""}.",
                    detail = "${info.levelPercent}%"
                )
            }
            ParsedCommand.BatteryReport -> controlManager.battery.getSpokenBatteryReport()
            ParsedCommand.StorageReport -> controlManager.storage.getSpokenStorageReport()
            ParsedCommand.NetworkReport -> controlManager.network.getSpokenNetworkReport()
            ParsedCommand.DeviceReport -> controlManager.device.getSpokenDeviceReport()
        }
    }

    private fun badgeFor(command: ParsedCommand): String = when (command) {
        is ParsedCommand.OpenApp -> "App Launch"
        is ParsedCommand.ClickElement, is ParsedCommand.ClickResultIndex, ParsedCommand.InspectScreen -> "Screen Click"
        is ParsedCommand.Scroll, is ParsedCommand.Swipe, is ParsedCommand.GlobalNav -> "Navigation"
        ParsedCommand.OpenWifiPanel, ParsedCommand.OpenInternetPanel, ParsedCommand.OpenBluetoothSettings,
        ParsedCommand.OpenLocationSettings, ParsedCommand.OpenDisplaySettings, ParsedCommand.OpenVolumePanel -> "Quick Settings"
        is ParsedCommand.Flashlight -> "Flashlight"
        is ParsedCommand.VolumeAdjust, is ParsedCommand.VolumeSet, is ParsedCommand.VolumeMute -> "Volume"
        is ParsedCommand.BrightnessAdjust, is ParsedCommand.BrightnessSet, ParsedCommand.BrightnessRead -> "Brightness"
        ParsedCommand.BatteryReport -> "Battery Info"
        ParsedCommand.StorageReport -> "Storage Info"
        ParsedCommand.NetworkReport -> "Network Info"
        ParsedCommand.DeviceReport -> "Device Info"
    }
}
