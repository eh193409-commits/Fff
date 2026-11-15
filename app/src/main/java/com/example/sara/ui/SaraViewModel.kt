package com.example.sara.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sara.ai.AiRepository
import com.example.sara.ai.PlaceholderAiRepository
import com.example.sara.ai.SaraVoiceEngine
import com.example.sara.ai.VoiceEngineState
import com.example.sara.control.AudioStreamType
import com.example.sara.control.BatteryInfo
import com.example.sara.control.BrightnessInfo
import com.example.sara.control.ControlResult
import com.example.sara.control.DeviceInfo
import com.example.sara.control.GlobalNavAction
import com.example.sara.control.InstalledAppInfo
import com.example.sara.control.NetworkInfo
import com.example.sara.control.PendingSensitiveAction
import com.example.sara.control.PermissionStatusSnapshot
import com.example.sara.control.PermissionType
import com.example.sara.control.ScrollDirection
import com.example.sara.control.SmartphoneControlManager
import com.example.sara.control.StorageInfo
import com.example.sara.control.SwipeDirection
import com.example.sara.control.VisibleUiElement
import com.example.sara.control.VoiceCommandRouter
import com.example.sara.control.VolumeSnapshot
import com.example.sara.memory.CommandLogEntity
import com.example.sara.memory.ConversationMessageEntity
import com.example.sara.memory.MemoryFactEntity
import com.example.sara.memory.MemoryRepository
import com.example.sara.memory.SaraDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiEvent {
    data class RequestRuntimePermission(val permissionType: PermissionType) : UiEvent
    data class ShowFeedback(val message: String) : UiEvent
}

data class TelemetryState(
    val battery: BatteryInfo,
    val storage: StorageInfo,
    val network: NetworkInfo,
    val device: DeviceInfo,
    val volume: VolumeSnapshot,
    val brightness: BrightnessInfo,
    val permissions: PermissionStatusSnapshot
)

class SaraViewModel(application: Application) : AndroidViewModel(application) {

    val controlManager = SmartphoneControlManager(application)
    private val database = SaraDatabase.getInstance(application)
    private val memoryRepository = MemoryRepository(database.memoryDao())
    private val aiRepository: AiRepository = PlaceholderAiRepository()
    private val commandRouter = VoiceCommandRouter(controlManager, memoryRepository, aiRepository)

    val voiceEngine = SaraVoiceEngine(
        context = application,
        onCommandRecognized = { spoken ->
            submitCommand(spoken, speakResponse = true)
        },
        onVoiceError = { errorMsg ->
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.ShowFeedback(errorMsg))
            }
        }
    )

    val voiceState: StateFlow<VoiceEngineState> = voiceEngine.state
    val partialTranscript: StateFlow<String> = voiceEngine.partialTranscript
    val rmsLevel: StateFlow<Float> = voiceEngine.rmsLevel
    val isWakeWordEnabled: StateFlow<Boolean> = voiceEngine.isWakeWordEnabled
    val isTtsEnabled: StateFlow<Boolean> = voiceEngine.isTtsEnabled

    val isFlashlightOn: StateFlow<Boolean> = controlManager.isFlashlightOn
    val isFlashlightAvailable: StateFlow<Boolean> = controlManager.isFlashlightAvailable
    val isAccessibilityConnected: StateFlow<Boolean> = controlManager.isAccessibilityConnected
    val activeWindowPackage: StateFlow<String?> = controlManager.activeWindowPackage
    val pendingSensitiveAction: StateFlow<PendingSensitiveAction?> = controlManager.pendingSensitiveAction
    val visibleElements: StateFlow<List<VisibleUiElement>> = controlManager.visibleElements

    val memories: StateFlow<List<MemoryFactEntity>> = memoryRepository.memories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLogs: StateFlow<List<CommandLogEntity>> = memoryRepository.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val messages: StateFlow<List<ConversationMessageEntity>> = memoryRepository.messages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _telemetry = MutableStateFlow(readTelemetry())
    val telemetry: StateFlow<TelemetryState> = _telemetry.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _lastPermissionNeeded = MutableStateFlow<PermissionType?>(null)
    val lastPermissionNeeded: StateFlow<PermissionType?> = _lastPermissionNeeded.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    init {
        refreshInstalledApps()
        seedGreetingIfNeeded()
    }

    private fun seedGreetingIfNeeded() {
        viewModelScope.launch {
            val existing = memoryRepository.getMemoriesSnapshot()
            // Only add welcome message once when conversation is empty
            database.memoryDao().observeMessages().collect { list ->
                if (list.isEmpty()) {
                    memoryRepository.addMessage(
                        isFromSara = true,
                        text = "Hi, I'm Sara! Your Smart Smartphone Control System is ready. Tap the mic, enable \"Hey Sara\" wake-up detection, or open the Smart Control tab to launch apps, automate screen actions, and manage hardware.",
                        actionBadge = "Ready"
                    )
                }
                return@collect
            }
        }
    }

    fun refreshTelemetry() {
        _telemetry.value = readTelemetry()
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                controlManager.appLauncher.getInstalledLaunchableApps()
            }
            _installedApps.value = apps
        }
    }

    private fun readTelemetry(): TelemetryState {
        return TelemetryState(
            battery = controlManager.battery.getBatteryInfo(),
            storage = controlManager.storage.getStorageInfo(),
            network = controlManager.network.getNetworkInfo(),
            device = controlManager.device.getDeviceInfo(),
            volume = controlManager.volume.getSnapshot(),
            brightness = controlManager.brightness.getBrightnessInfo(),
            permissions = controlManager.getPermissionSnapshot()
        )
    }

    fun submitCommand(rawText: String, speakResponse: Boolean = true) {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            _isProcessing.value = true
            _lastPermissionNeeded.value = null
            memoryRepository.addMessage(isFromSara = false, text = trimmed)

            val outcome = commandRouter.routeAndExecute(trimmed)
            _lastPermissionNeeded.value = outcome.permissionRequired

            memoryRepository.addMessage(
                isFromSara = true,
                text = outcome.spokenResponse,
                actionBadge = outcome.actionBadge,
                isPlaceholderAi = outcome.isPlaceholderAi
            )
            refreshTelemetry()
            _isProcessing.value = false

            if (speakResponse) {
                voiceEngine.speak(outcome.spokenResponse)
            }
        }
    }

    fun toggleWakeWord(desiredEnabled: Boolean) {
        if (desiredEnabled && !telemetry.value.permissions.recordAudioGranted) {
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.RequestRuntimePermission(PermissionType.RECORD_AUDIO))
            }
            return
        }
        voiceEngine.setWakeWordEnabled(desiredEnabled)
    }

    fun startVoiceCommand() {
        if (!telemetry.value.permissions.recordAudioGranted) {
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.RequestRuntimePermission(PermissionType.RECORD_AUDIO))
            }
            return
        }
        voiceEngine.startPushToTalkCommand()
    }

    fun toggleTts() {
        voiceEngine.setTtsEnabled(!voiceEngine.isTtsEnabled.value)
    }

    // Direct Smart Control UI actions
    fun launchApp(app: InstalledAppInfo) {
        executeDirectControl("Open ${app.appName}") {
            controlManager.appLauncher.launchPackage(app.packageName, app.appName)
        }
    }

    fun toggleFlashlight(enabled: Boolean) {
        executeDirectControl(if (enabled) "Flashlight ON" else "Flashlight OFF") {
            val res = controlManager.flashlight.setFlashlight(enabled)
            if (res is ControlResult.PermissionRequired && res.permission == PermissionType.CAMERA) {
                _uiEvents.emit(UiEvent.RequestRuntimePermission(PermissionType.CAMERA))
            }
            res
        }
    }

    fun setVolume(stream: AudioStreamType, percent: Int) {
        executeDirectControl("Set ${stream.label} volume to $percent%", speak = false) {
            controlManager.volume.setVolumePercent(percent, stream)
        }
    }

    fun adjustVolume(stream: AudioStreamType, increase: Boolean) {
        executeDirectControl(if (increase) "Increase ${stream.label}" else "Decrease ${stream.label}", speak = false) {
            if (increase) controlManager.volume.increaseVolume(stream)
            else controlManager.volume.decreaseVolume(stream)
        }
    }

    fun toggleMuteStream(stream: AudioStreamType, mute: Boolean) {
        executeDirectControl(if (mute) "Mute ${stream.label}" else "Unmute ${stream.label}") {
            if (mute) controlManager.volume.mute(stream)
            else controlManager.volume.unmute(stream)
        }
    }

    fun setBrightness(percent: Int) {
        executeDirectControl("Set brightness to $percent%", speak = false) {
            controlManager.brightness.setBrightnessPercent(percent)
        }
    }

    fun inspectScreen() {
        executeDirectControl("Scan visible screen elements") {
            controlManager.inspectScreenElements()
        }
    }

    fun clickUiQuery(query: String) {
        executeDirectControl("Click \"$query\"") {
            controlManager.clickUiElement(query)
        }
    }

    fun clickFirstResult() {
        executeDirectControl("Select first result") {
            controlManager.clickNthResult(0)
        }
    }

    fun performScroll(direction: ScrollDirection) {
        executeDirectControl("Scroll ${direction.name.lowercase()}") {
            controlManager.scroll(direction)
        }
    }

    fun performSwipe(direction: SwipeDirection) {
        executeDirectControl("Swipe ${direction.name.lowercase()}") {
            controlManager.swipe(direction)
        }
    }

    fun performGlobalNav(action: GlobalNavAction) {
        executeDirectControl("Global nav: ${action.name.lowercase()}") {
            controlManager.navigateGlobal(action)
        }
    }

    fun openQuickSettingAction(action: () -> ControlResult, label: String) {
        executeDirectControl(label) { action() }
    }

    fun confirmSensitiveAction() {
        viewModelScope.launch {
            val res = controlManager.confirmPendingAction() ?: return@launch
            memoryRepository.logCommand("Confirmed sensitive action", res.spokenResponse, res is ControlResult.Success)
            memoryRepository.addMessage(isFromSara = true, text = res.spokenResponse, actionBadge = "Confirmed")
            voiceEngine.speak(res.spokenResponse)
            _uiEvents.emit(UiEvent.ShowFeedback(res.spokenResponse))
        }
    }

    fun dismissSensitiveAction() {
        controlManager.dismissPendingAction()
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowFeedback("Action cancelled for your safety."))
        }
    }

    fun requestPermission(type: PermissionType) {
        viewModelScope.launch {
            when (type) {
                PermissionType.RECORD_AUDIO, PermissionType.CAMERA -> {
                    _uiEvents.emit(UiEvent.RequestRuntimePermission(type))
                }
                PermissionType.WRITE_SETTINGS, PermissionType.ACCESSIBILITY_SERVICE, PermissionType.NOTIFICATION_POLICY -> {
                    controlManager.openPermissionSettings(type)
                }
            }
        }
    }

    fun addMemory(category: String, content: String) {
        viewModelScope.launch {
            memoryRepository.rememberFact(category, content)
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryRepository.forgetFact(id)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            memoryRepository.clearCommandLogs()
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            memoryRepository.clearConversation()
            memoryRepository.addMessage(
                isFromSara = true,
                text = "Conversation cleared. I'm ready whenever you need me!",
                actionBadge = "Ready"
            )
        }
    }

    private fun executeDirectControl(
        label: String,
        speak: Boolean = false,
        block: suspend () -> ControlResult
    ) {
        viewModelScope.launch {
            val result = block()
            val ok = result is ControlResult.Success
            memoryRepository.logCommand(label, result.spokenResponse, ok)
            refreshTelemetry()
            if (result is ControlResult.PermissionRequired) {
                _lastPermissionNeeded.value = result.permission
                _uiEvents.emit(UiEvent.ShowFeedback(result.spokenResponse))
            } else if (!ok) {
                _uiEvents.emit(UiEvent.ShowFeedback(result.spokenResponse))
            }
            if (speak) {
                voiceEngine.speak(result.spokenResponse)
            }
        }
    }

    fun formatStorageSize(bytes: Long): String = controlManager.storage.formatBytes(bytes)

    override fun onCleared() {
        super.onCleared()
        voiceEngine.release()
        controlManager.release()
    }
}
