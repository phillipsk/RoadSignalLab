package io.roadsignal.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.roadsignal.core.RecordingState
import io.roadsignal.sdk.RoadSignal
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColdStartTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test
    fun coldProcessLoadsDurableJournalWithoutRestartingCapture() {
        val sdk = RoadSignal.get(InstrumentationRegistry.getInstrumentation().targetContext)
        runBlocking { sdk.awaitReady() }
        assertTrue(sdk.state.value.consent)
        assertTrue(sdk.state.value.trips.size >= 4)
        assertEquals(RecordingState.STOPPED, sdk.state.value.recording)
        ui.onNodeWithText("Explore the lab").performClick()
        ui.onNodeWithText("Trips").performClick()
        ui.onNodeWithText("Every trip tells\na different story.").assertIsDisplayed()
    }
}
