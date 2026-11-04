package io.roadsignal.sdk

import io.roadsignal.core.*

enum class UploadSimulation {
    ACKNOWLEDGE,
    OFFLINE,
    RETRY_ONCE,
    REJECT,
}

data class SdkSnapshot(
    val consent: Boolean = false,
    val consentEpoch: Long = 0,
    val recording: RecordingState = RecordingState.STOPPED,
    val source: SourceMode = SourceMode.REPLAY,
    val trips: List<TripSummary> = emptyList(),
    val latestRotationRadPerSec: Double = 0.0,
    val acceptedSamples: Long = 0,
    val rejectedSamples: Long = 0,
    val droppedSamples: Long = 0,
    val pendingUploads: Int = 0,
    val deadLetters: Int = 0,
    val localReceipts: Int = 0,
    val unreadableRecords: Int = 0,
    val profile: PowerProfile = PowerProfile.BALANCED,
    val policyRevision: Long = 0,
    val uploadSimulation: UploadSimulation = UploadSimulation.ACKNOWLEDGE,
    val diagnostics: List<String> = emptyList(),
    val notice: String = "SDK ready. Collection requires explicit consent.",
)

data class SdkResult(val accepted: Boolean, val message: String)

data class SyncReport(
    val acknowledged: Int,
    val retryable: Int,
    val deadLetters: Int,
    val consentBlocked: Boolean = false,
)

data class SensorCapability(
    val type: String,
    val available: Boolean,
    val wakeUp: Boolean,
    val fifoMaxEvents: Int,
)
