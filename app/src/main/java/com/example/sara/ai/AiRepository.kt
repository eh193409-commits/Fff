package com.example.sara.ai

import com.example.sara.memory.MemoryFactEntity

data class AiConversationReply(
    val text: String,
    val isPlaceholderNotice: Boolean = false
)

interface AiRepository {
    val isCloudGeminiConnected: Boolean
    suspend fun generateConversationalReply(
        userMessage: String,
        memories: List<MemoryFactEntity>,
        deviceSummary: String
    ): AiConversationReply
}

/**
 * Local fallback implementation used when Firebase AI Logic is not provisioned.
 * Keeps Sara's warm female persona responsive offline while clearly labeling
 * open-ended AI responses as local placeholders that require Firebase for cloud Gemini.
 */
class PlaceholderAiRepository : AiRepository {
    override val isCloudGeminiConnected: Boolean = false

    override suspend fun generateConversationalReply(
        userMessage: String,
        memories: List<MemoryFactEntity>,
        deviceSummary: String
    ): AiConversationReply {
        val normalized = userMessage.lowercase().trim()
        val userNameMemory = memories.firstOrNull {
            it.content.contains("name is", ignoreCase = true)
        }?.content?.substringAfter("name is", "")?.trim()?.takeIf { it.isNotBlank() }

        val greetingPrefix = if (userNameMemory != null) "Hi $userNameMemory! " else ""

        return when {
            normalized.contains("who are you") || normalized.contains("your name") -> {
                AiConversationReply(
                    text = "${greetingPrefix}I'm Sara, your personal voice assistant and Smart Smartphone Control companion. I can open apps, control your flashlight, volume, and brightness, automate screen clicks and scrolling, and read your live battery, storage, network, and device stats.",
                    isPlaceholderNotice = false
                )
            }
            normalized.contains("hello") || normalized == "hi" || normalized.contains("hey sara") || normalized.contains("good morning") || normalized.contains("good evening") -> {
                AiConversationReply(
                    text = "${greetingPrefix}I'm right here and listening! Try asking me to open an app, adjust brightness or volume, toggle your flashlight, or check your battery and storage.",
                    isPlaceholderNotice = false
                )
            }
            normalized.contains("thank") -> {
                AiConversationReply(
                    text = "You're always welcome! Just say \"Hey Sara\" whenever you need a hand with your phone.",
                    isPlaceholderNotice = false
                )
            }
            normalized.contains("help") || normalized.contains("what can you do") -> {
                AiConversationReply(
                    text = "I can launch installed apps, click or scroll on screen via Accessibility, open Quick Settings, toggle the flashlight, adjust media/ringtone/notification volume and screen brightness, report live battery, storage, network, and device telemetry, and remember personal notes for you.",
                    isPlaceholderNotice = false
                )
            }
            else -> {
                val memoryHint = if (memories.isNotEmpty()) {
                    " I still have ${memories.size} saved memory item${if (memories.size == 1) "" else "s"} ready locally."
                } else ""
                AiConversationReply(
                    text = "[Offline Placeholder Response — Enable Firebase to connect cloud Gemini] I heard: \"$userMessage\".$memoryHint Your device status right now: $deviceSummary. For hands-free control, try commands like \"Open YouTube\", \"Turn on flashlight\", \"Set brightness to 80 percent\", or \"Scroll down\".",
                    isPlaceholderNotice = true
                )
            }
        }
    }
}
