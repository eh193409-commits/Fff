package com.example.sara.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class VoiceEngineState {
    IDLE,
    LISTENING_WAKE_WORD,
    LISTENING_COMMAND,
    SPEAKING
}

class SaraVoiceEngine(
    private val context: Context,
    private val onCommandRecognized: (String) -> Unit,
    private val onVoiceError: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(VoiceEngineState.IDLE)
    val state: StateFlow<VoiceEngineState> = _state.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _isWakeWordEnabled = MutableStateFlow(false)
    val isWakeWordEnabled: StateFlow<Boolean> = _isWakeWordEnabled.asStateFlow()

    private val _isTtsEnabled = MutableStateFlow(true)
    val isTtsEnabled: StateFlow<Boolean> = _isTtsEnabled.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null

    init {
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                // Select a natural female English voice when available on device
                val femaleVoice = tts?.voices?.firstOrNull { voice ->
                    val name = voice.name.lowercase()
                    voice.locale.language == "en" &&
                        !voice.isNetworkConnectionRequired &&
                        (name.contains("female") || name.contains("sfg") || name.contains("tpf") ||
                            name.contains("iol") || name.contains("sara") || name.contains("karen") ||
                            name.contains("samantha"))
                } ?: tts?.voices?.firstOrNull { voice ->
                    voice.locale.language == "en" && !voice.isNetworkConnectionRequired
                }
                if (femaleVoice != null) {
                    tts?.voice = femaleVoice
                }
                tts?.setPitch(1.08f)
                tts?.setSpeechRate(1.02f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.value = VoiceEngineState.SPEAKING
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            if (_state.value == VoiceEngineState.SPEAKING) {
                                _state.value = VoiceEngineState.IDLE
                            }
                            if (_isWakeWordEnabled.value) {
                                startListeningCycle(isWakeMode = true)
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            _state.value = VoiceEngineState.IDLE
                        }
                    }
                })
                ttsReady = true
            }
        }
    }

    fun setTtsEnabled(enabled: Boolean) {
        _isTtsEnabled.value = enabled
        if (!enabled) {
            tts?.stop()
            if (_state.value == VoiceEngineState.SPEAKING) {
                _state.value = VoiceEngineState.IDLE
            }
        }
    }

    fun speak(text: String) {
        if (!_isTtsEnabled.value || !ttsReady || text.isBlank()) {
            if (_isWakeWordEnabled.value && _state.value == VoiceEngineState.IDLE) {
                startListeningCycle(isWakeMode = true)
            }
            return
        }
        val cleanSpeech = text
            .replace("[Offline Placeholder Response — Enable Firebase to connect cloud Gemini]", "")
            .trim()
        tts?.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, null, "sara_${System.currentTimeMillis()}")
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        _isWakeWordEnabled.value = enabled
        if (enabled) {
            startListeningCycle(isWakeMode = true)
        } else if (_state.value == VoiceEngineState.LISTENING_WAKE_WORD) {
            stopListening()
        }
    }

    fun startPushToTalkCommand() {
        tts?.stop()
        startListeningCycle(isWakeMode = false)
    }

    fun stopListening() {
        mainHandler.post {
            runCatching { speechRecognizer?.stopListening() }
            runCatching { speechRecognizer?.cancel() }
            _partialTranscript.value = ""
            _rmsLevel.value = 0f
            if (_state.value != VoiceEngineState.SPEAKING) {
                _state.value = VoiceEngineState.IDLE
            }
        }
    }

    private fun startListeningCycle(isWakeMode: Boolean) {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _isWakeWordEnabled.value = false
                _state.value = VoiceEngineState.IDLE
                if (!isWakeMode) {
                    onVoiceError("On-device Speech Recognition service is not available on this device. You can type commands directly below.")
                }
                return@post
            }

            runCatching { speechRecognizer?.destroy() }
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.value = if (isWakeMode) {
                        VoiceEngineState.LISTENING_WAKE_WORD
                    } else {
                        VoiceEngineState.LISTENING_COMMAND
                    }
                    _partialTranscript.value = ""
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    _rmsLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _rmsLevel.value = 0f
                }

                override fun onError(error: Int) {
                    _rmsLevel.value = 0f
                    _partialTranscript.value = ""
                    _state.value = VoiceEngineState.IDLE
                    if (isWakeMode && _isWakeWordEnabled.value) {
                        // Gently restart wake-word listening after timeout/no-match
                        if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        ) {
                            mainHandler.postDelayed({
                                if (_isWakeWordEnabled.value && _state.value == VoiceEngineState.IDLE) {
                                    startListeningCycle(isWakeMode = true)
                                }
                            }, 450L)
                        } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                            _isWakeWordEnabled.value = false
                            onVoiceError("Microphone permission is required for Hey Sara wake-up detection.")
                        }
                    } else if (!isWakeMode && error != SpeechRecognizer.ERROR_CLIENT) {
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice commands."
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that. Tap the mic and try again."
                            else -> "Voice input error ($error). You can also type your command."
                        }
                        onVoiceError(msg)
                    }
                }

                override fun onResults(results: Bundle?) {
                    _rmsLevel.value = 0f
                    _partialTranscript.value = ""
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()

                    _state.value = VoiceEngineState.IDLE

                    if (spoken.isBlank()) {
                        if (isWakeMode && _isWakeWordEnabled.value) {
                            startListeningCycle(isWakeMode = true)
                        }
                        return
                    }

                    if (isWakeMode) {
                        val lower = spoken.lowercase()
                        if (lower.contains("sara")) {
                            val afterWake = lower.substringAfter("sara").trim()
                            if (afterWake.isNotBlank()) {
                                onCommandRecognized(spoken)
                            } else {
                                speak("Yes, I'm listening!")
                            }
                        } else if (_isWakeWordEnabled.value) {
                            mainHandler.postDelayed({
                                if (_isWakeWordEnabled.value && _state.value == VoiceEngineState.IDLE) {
                                    startListeningCycle(isWakeMode = true)
                                }
                            }, 350L)
                        }
                    } else {
                        onCommandRecognized(spoken)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    _partialTranscript.value = partial
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            runCatching { recognizer.startListening(intent) }.onFailure {
                _state.value = VoiceEngineState.IDLE
                if (!isWakeMode) {
                    onVoiceError("Could not start voice recognition: ${it.localizedMessage}")
                }
            }
        }
    }

    fun release() {
        mainHandler.post {
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
            runCatching {
                tts?.stop()
                tts?.shutdown()
            }
            tts = null
        }
    }
}
