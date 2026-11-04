package io.roadsignal.core

/** Locally supplied configuration contract, not a CMT API or a deployed config service. */
data class CapturePolicy(
    val revision: Long,
    val enabled: Boolean,
    val profile: PowerProfile,
    val expiresAtEpochMs: Long,
)

class PolicyGate(private var currentRevision: Long = 0) {
    fun validate(candidate: CapturePolicy, nowEpochMs: Long): String? =
        when {
            candidate.revision <= currentRevision -> "stale_revision"
            candidate.expiresAtEpochMs <= nowEpochMs -> "expired_policy"
            candidate.expiresAtEpochMs - nowEpochMs > 7 * 24 * 60 * 60 * 1000L -> "ttl_too_long"
            else -> {
                currentRevision = candidate.revision
                null
            }
        }
}

data class WakeLockComparison(
    val legacyOwnedMillis: Long,
    val proposedOwnedMillis: Long,
    val requestedSensorHz: Int,
    val requestedBatchLatencyUs: Int,
)

/**
 * A counterfactual model, not CPU residency, battery consumption, Android vitals or device
 * measurements.
 */
object PowerModel {
    @JvmStatic
    fun compare(recordingMillis: Long, profile: PowerProfile): WakeLockComparison {
        require(recordingMillis >= 0)
        return WakeLockComparison(recordingMillis, 0, profile.sensorHz, profile.batchLatencyUs)
    }
}

enum class DeliveryResult {
    ACK,
    RETRYABLE,
    PERMANENT_FAILURE,
}

enum class DeliveryAction {
    ACKNOWLEDGE,
    RETRY,
    DEAD_LETTER,
}

object RetryPolicy {
    @JvmStatic
    fun decide(result: DeliveryResult, previousAttempts: Int): DeliveryAction {
        require(previousAttempts >= 0)
        return when (result) {
            DeliveryResult.ACK -> DeliveryAction.ACKNOWLEDGE
            DeliveryResult.PERMANENT_FAILURE -> DeliveryAction.DEAD_LETTER
            DeliveryResult.RETRYABLE ->
                if (previousAttempts >= 4) DeliveryAction.DEAD_LETTER else DeliveryAction.RETRY
        }
    }
}
