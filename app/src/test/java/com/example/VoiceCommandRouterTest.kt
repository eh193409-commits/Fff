package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sara.ai.PlaceholderAiRepository
import com.example.sara.control.AudioStreamType
import com.example.sara.control.GlobalNavAction
import com.example.sara.control.ScrollDirection
import com.example.sara.control.SmartphoneControlManager
import com.example.sara.control.SwipeDirection
import com.example.sara.control.VoiceCommandRouter
import com.example.sara.memory.MemoryRepository
import com.example.sara.memory.SaraDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceCommandRouterTest {

    private lateinit var context: Context
    private lateinit var database: SaraDatabase
    private lateinit var memoryRepo: MemoryRepository
    private lateinit var controlManager: SmartphoneControlManager
    private lateinit var router: VoiceCommandRouter

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, SaraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        memoryRepo = MemoryRepository(database.memoryDao())
        controlManager = SmartphoneControlManager(context)
        router = VoiceCommandRouter(controlManager, memoryRepo, PlaceholderAiRepository())
    }

    @After
    fun tearDown() {
        controlManager.release()
        database.close()
    }

    @Test
    fun `parseSingleCommand recognizes core smartphone control intents`() {
        assertEquals(
            VoiceCommandRouter.ParsedCommand.OpenApp("YouTube"),
            router.parseSingleCommand("open YouTube")
        )
        assertEquals(
            VoiceCommandRouter.ParsedCommand.Flashlight(true),
            router.parseSingleCommand("turn on flashlight")
        )
        assertEquals(
            VoiceCommandRouter.ParsedCommand.BrightnessSet(80),
            router.parseSingleCommand("increase brightness to 80 percent")
        )
        assertEquals(
            VoiceCommandRouter.ParsedCommand.VolumeSet(AudioStreamType.MEDIA, 60),
            router.parseSingleCommand("set media volume to 60%")
        )
        assertEquals(
            VoiceCommandRouter.ParsedCommand.Scroll(ScrollDirection.BOTTOM),
            router.parseSingleCommand("go to the bottom")
        )
        assertEquals(
            VoiceCommandRouter.ParsedCommand.Swipe(SwipeDirection.LEFT),
            router.parseSingleCommand("swipe left")
        )
        assertEquals(
            VoiceCommandRouter.ParsedCommand.ClickElement("Search"),
            router.parseSingleCommand("click Search")
        )
        assertEquals(
            VoiceCommandRouter.ParsedCommand.ClickResultIndex(0),
            router.parseSingleCommand("select the first result")
        )
        assertEquals(
            VoiceCommandRouter.ParsedCommand.GlobalNav(GlobalNavAction.QUICK_SETTINGS),
            router.parseSingleCommand("open quick settings")
        )
    }

    @Test
    fun `routeAndExecute handles battery storage network and device telemetry`() = runTest {
        val battery = router.routeAndExecute("Hey Sara, how much battery is left?")
        assertTrue(battery.succeeded)
        assertEquals("Battery Info", battery.actionBadge)
        assertTrue(battery.spokenResponse.contains("percent", ignoreCase = true))

        val storage = router.routeAndExecute("Sara, check storage")
        assertTrue(storage.succeeded)
        assertEquals("Storage Info", storage.actionBadge)

        val network = router.routeAndExecute("Am I connected to wifi?")
        assertTrue(network.succeeded)
        assertEquals("Network Info", network.actionBadge)

        val device = router.routeAndExecute("What phone model and android version is this?")
        assertTrue(device.succeeded)
        assertEquals("Device Info", device.actionBadge)
        assertTrue(device.spokenResponse.contains("SDK", ignoreCase = true))
    }

    @Test
    fun `routeAndExecute persists and recalls long term memory`() = runTest {
        val saveOutcome = router.routeAndExecute("Hey Sara, remember that my favorite app is Chrome")
        assertTrue(saveOutcome.succeeded)
        assertEquals("Memory Saved", saveOutcome.actionBadge)

        val recallOutcome = router.routeAndExecute("What do you remember about me?")
        assertTrue(recallOutcome.succeeded)
        assertTrue(recallOutcome.spokenResponse.contains("Chrome"))
    }

    @Test
    fun `routeAndExecute labels placeholder AI chat transparently`() = runTest {
        val chatOutcome = router.routeAndExecute("Tell me a bedtime story about stars")
        assertTrue(chatOutcome.isPlaceholderAi)
        assertFalse(chatOutcome.spokenResponse.isBlank())
    }
}
