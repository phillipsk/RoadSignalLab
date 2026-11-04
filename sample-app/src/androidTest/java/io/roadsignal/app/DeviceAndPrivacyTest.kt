package io.roadsignal.app

import android.app.ActivityManager
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
class DeviceAndPrivacyTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val inst
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = inst.targetContext

    private val sdk
        get() = RoadSignal.get(context)

    private fun tap(text: String) {
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
        ui.onNodeWithText(text).performScrollTo().performClick()
        ui.waitForIdle()
    }

    private fun serviceRunning(): Boolean =
        context.getSystemService(ActivityManager::class.java).getRunningServices(100).any {
            it.service.className == "io.roadsignal.sdk.CaptureService" && it.foreground
        }

    private fun screenshot(name: String) {
        ui.waitForIdle()
        ui.mainClock.advanceTimeBy(800)
        val p = File(context.getExternalFilesDir(null), "showcase/$name.png")
        p.parentFile!!.mkdirs()
        p.outputStream().use {
            inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun foregroundServiceStartsStopsAndConsentDeletionWins() {
        runBlocking {
            sdk.awaitReady()
            sdk.grantConsent()
        }
        for (permission in
            listOf(
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.POST_NOTIFICATIONS",
            )) {
            inst.uiAutomation
                .executeShellCommand("pm grant ${context.packageName} $permission")
                .close()
        }
        ui.onNodeWithText("Explore the lab").performClick()
        ui.onNodeWithText("Controls").performClick()
        tap("Start device session")
        ui.waitUntil(15000) { serviceRunning() }
        assertEquals(SourceMode.DEVICE, sdk.state.value.source)
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("ENGINE · DEVICE"))
        screenshot("16-device-session-active")
        tap("Stop capture")
        ui.waitUntil(10000) { !serviceRunning() }
        assertEquals(RecordingState.STOPPED, sdk.state.value.recording)
        tap("Revoke consent & delete data")
        screenshot("17-revoke-confirmation")
        ui.onNodeWithText("Delete local data").performClick()
        ui.waitUntil(10000) {
            !sdk.state.value.consent &&
                sdk.state.value.trips.isEmpty() &&
                sdk.state.value.pendingUploads == 0
        }
        assertEquals(0, sdk.state.value.localReceipts)
        assertFalse(serviceRunning())
        screenshot("18-revoked-and-cleared")
    }
}
