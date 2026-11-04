package io.roadsignal.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.roadsignal.core.*
import io.roadsignal.sdk.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShowcaseJourneyTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val sdk
        get() = RoadSignal.get(context)

    private fun tap(text: String) {
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text, substring = false))
        ui.onAllNodesWithText(text, substring = false).onFirst().performScrollTo().performClick()
        ui.waitForIdle()
    }

    private fun nav(text: String) {
        ui.waitUntil(5000) {
            ui.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithText(text, substring = false).assertIsDisplayed().performClick()
        ui.waitForIdle()
    }

    private fun shot(name: String) {
        ui.waitForIdle()
        ui.mainClock.advanceTimeBy(800)
        val folder = File(context.getExternalFilesDir(null), "showcase").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(folder, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    @Test
    fun completeSyntheticJourneysAndCapture() {
        runBlocking {
            sdk.awaitReady()
            sdk.revokeConsentAndDelete()
        }
        shot("01-welcome")
        nav("Explore the lab")
        shot("02-overview-consent-gate")
        nav("Controls")
        tap("Enable local SDK")
        ui.waitUntil(5000) { sdk.state.value.consent }
        shot("03-consent-and-device-controls")
        nav("Overview")
        tap("Replay commute")
        ui.waitUntil(15000) {
            sdk.state.value.trips.isNotEmpty() &&
                sdk.state.value.recording == RecordingState.STOPPED
        }
        nav("Trips")
        shot("04-trip-journal")
        tap("UNCLASSIFIED · Open trip details →")
        shot("05-trip-detail")
        tap("Passenger")
        ui.waitUntil(5000) { sdk.state.value.trips.first().role == TripRole.PASSENGER }
        tap("Event timeline")
        shot("06-event-timeline-and-role")
        nav("Lab")
        shot("07-power-model")
        tap("Apply economy policy")
        ui.waitUntil(5000) { sdk.state.value.profile == PowerProfile.ECONOMY }
        shot("08-sampling-policy")
        tap("Outbox")
        tap("OFFLINE")
        tap("Schedule local sync")
        ui.waitUntil(10000) { sdk.state.value.pendingUploads > 0 }
        tap("Durable by design")
        shot("09-offline-outbox")
        tap("ACKNOWLEDGE")
        tap("Schedule local sync")
        ui.waitUntil(20000) { sdk.state.value.localReceipts == 1 }
        tap("Durable by design")
        shot("10-local-acknowledgment")
        tap("Pipeline")
        shot("11-distribution-pipeline")
        nav("Partners")
        shot("12-cmt-mock-boundary")
        tap("Check real SDK setup")
        assertFalse(
            runBlocking { io.roadsignal.cmt.UnconfiguredCmtAdapter().demonstrateTrip() }.accepted,
        )
        nav("Overview")
        tap("Tunnel & bad GPS")
        val before = sdk.state.value.trips.size
        ui.waitUntil(15000) {
            sdk.state.value.trips.size >= before + 2 &&
                sdk.state.value.recording == RecordingState.STOPPED
        }
        assertTrue(sdk.state.value.rejectedSamples > 0)
        nav("Trips")
        shot("13-gap-recovery-trips")
        nav("Overview")
        tap("Impact candidate")
        val impactBefore = sdk.state.value.trips.size
        ui.waitUntil(15000) {
            sdk.state.value.trips.size > impactBefore &&
                sdk.state.value.recording == RecordingState.STOPPED
        }
        assertTrue(
            sdk.state.value.trips.first().events.any { it.kind == EventKind.IMPACT_CANDIDATE },
        )
        nav("Trips")
        tap("UNCLASSIFIED · Open trip details →")
        tap("Event timeline")
        shot("14-impact-candidate-only")
        nav("Lab")
        tap("Diagnostics")
        shot("15-bounded-diagnostics")
        val count = sdk.state.value.trips.size
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        assertEquals(count, sdk.state.value.trips.size)
        // Leave persisted fixtures for an independent cold-process launch check.
    }
}
