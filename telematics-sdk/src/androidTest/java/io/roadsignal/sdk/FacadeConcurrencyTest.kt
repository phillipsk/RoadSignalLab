package io.roadsignal.sdk

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.roadsignal.core.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FacadeConcurrencyTest {
    private val sdk = RoadSignal.get(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun finalRevocationWinsConcurrentReplayAndConfiguration() = runBlocking {
        sdk.awaitReady()
        sdk.revokeConsentAndDelete()
        sdk.grantConsent()
        coroutineScope {
            (0..7)
                .map { index ->
                    async(Dispatchers.Default) {
                        repeat(3) {
                            sdk.startReplay(ReplayScenario.COMMUTE, 1)
                            sdk.stop()
                            if (index % 2 == 0) {
                                sdk.revokeConsentAndDelete()
                                sdk.grantConsent()
                            }
                        }
                    }
                }
                .awaitAll()
        }
        sdk.revokeConsentAndDelete()
        delay(300)
        sdk.refresh()
        assertFalse(sdk.state.value.consent)
        assertTrue(sdk.state.value.trips.isEmpty())
        assertEquals(0, sdk.state.value.pendingUploads)
        assertEquals(RecordingState.STOPPED, sdk.state.value.recording)
    }

    @Test
    fun activePolicyExpiryStopsCollectionWithoutIdlePolling() = runBlocking {
        sdk.awaitReady()
        sdk.grantConsent()
        assertTrue(
            sdk.applyPolicy(
                    CapturePolicy(
                        sdk.state.value.policyRevision + 1,
                        true,
                        PowerProfile.BALANCED,
                        System.currentTimeMillis() + 150,
                    ),
                )
                .accepted,
        )
        sdk.startReplay(ReplayScenario.COMMUTE, 100)
        delay(500)
        assertEquals(RecordingState.STOPPED, sdk.state.value.recording)
        assertFalse(sdk.startReplay(ReplayScenario.COMMUTE).accepted)
        sdk.applyPolicy(
            CapturePolicy(
                sdk.state.value.policyRevision + 1,
                true,
                PowerProfile.BALANCED,
                System.currentTimeMillis() + 60_000,
            ),
        )
        sdk.revokeConsentAndDelete()
        Unit
    }

    @Test
    fun disabledPolicyStopsAnActiveReplayAndRejectsTheNextStart() = runBlocking {
        sdk.awaitReady()
        sdk.grantConsent()
        sdk.startReplay(ReplayScenario.COMMUTE, 100)
        assertTrue(
            sdk.applyPolicy(
                    CapturePolicy(
                        sdk.state.value.policyRevision + 1,
                        false,
                        PowerProfile.ECONOMY,
                        System.currentTimeMillis() + 60_000,
                    ),
                )
                .accepted,
        )
        delay(200)
        assertEquals(RecordingState.STOPPED, sdk.state.value.recording)
        assertFalse(sdk.startReplay(ReplayScenario.COMMUTE).accepted)
        sdk.applyPolicy(
            CapturePolicy(
                sdk.state.value.policyRevision + 1,
                true,
                PowerProfile.BALANCED,
                System.currentTimeMillis() + 60_000,
            ),
        )
        sdk.revokeConsentAndDelete()
        Unit
    }
}
