package io.roadsignal.app

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.roadsignal.cmt.*
import io.roadsignal.core.*
import io.roadsignal.sdk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LabViewModel(app: Application) : AndroidViewModel(app) {
    val sdk = RoadSignal.get(app)
    val state = sdk.state
    private val mutableMessage = MutableStateFlow("")
    val message = mutableMessage.asStateFlow()

    init {
        viewModelScope.launch { sdk.awaitReady() }
    }

    private fun run(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (_: Exception) {
                mutableMessage.value =
                    "Action unavailable. Review consent and device permissions, then retry."
            }
        }
    }

    fun consent() = run { sdk.grantConsent() }

    fun revoke() = run { sdk.revokeConsentAndDelete() }

    fun replay(scenario: ReplayScenario) = run {
        mutableMessage.value = sdk.startReplay(scenario).message
    }

    fun stop() = run { sdk.stop() }

    fun policy(profile: PowerProfile, enabled: Boolean = true) = run {
        mutableMessage.value =
            sdk.applyPolicy(
                    CapturePolicy(
                        state.value.policyRevision + 1,
                        enabled,
                        profile,
                        System.currentTimeMillis() + 3_600_000,
                    ),
                )
                .message
    }

    fun uploadMode(mode: UploadSimulation) = run { sdk.setUploadSimulation(mode) }

    fun sync() {
        sdk.enqueueUpload()
        mutableMessage.value =
            "Scheduled a local-only WorkManager sync. No data is sent to a server."
    }

    fun retry() = run { sdk.retryDeadLetters() }

    fun classify(id: String, role: TripRole) = run { sdk.classifyTrip(id, role) }

    fun live(activity: Activity) = run {
        mutableMessage.value = sdk.startLiveFromVisibleActivity(activity).message
    }

    fun mockPartner() = run { mutableMessage.value = MockCmtAdapter(sdk).demonstrateTrip().message }

    fun unavailablePartner() = run {
        mutableMessage.value = UnconfiguredCmtAdapter().demonstrateTrip().message
    }
}
