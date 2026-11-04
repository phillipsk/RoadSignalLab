package io.roadsignal.core

import org.junit.Assert.*
import org.junit.Test

class PolicyTest {
    @Test
    fun policyRejectsReplayAndExpiryWithoutAdvancingVersion() {
        val gate = PolicyGate()
        assertEquals(
            "expired_policy",
            gate.validate(CapturePolicy(2, true, PowerProfile.ECONOMY, 9), 10),
        )
        assertNull(gate.validate(CapturePolicy(1, true, PowerProfile.ECONOMY, 20), 10))
        assertEquals(
            "stale_revision",
            gate.validate(CapturePolicy(1, true, PowerProfile.BALANCED, 20), 10),
        )
    }

    @Test
    fun excessivelyLongPolicyTtlFailsClosed() {
        assertEquals(
            "ttl_too_long",
            PolicyGate().validate(CapturePolicy(1, true, PowerProfile.ECONOMY, Long.MAX_VALUE), 10),
        )
    }

    @Test
    fun retryIsBoundedAndPermanentFailuresDoNotSpin() {
        repeat(4) {
            assertEquals(DeliveryAction.RETRY, RetryPolicy.decide(DeliveryResult.RETRYABLE, it))
        }
        assertEquals(DeliveryAction.DEAD_LETTER, RetryPolicy.decide(DeliveryResult.RETRYABLE, 4))
        assertEquals(
            DeliveryAction.DEAD_LETTER,
            RetryPolicy.decide(DeliveryResult.PERMANENT_FAILURE, 0),
        )
        assertEquals(DeliveryAction.ACKNOWLEDGE, RetryPolicy.decide(DeliveryResult.ACK, 4))
    }

    @Test
    fun powerComparisonDoesNotClaimBatterySavings() {
        val m = PowerModel.compare(180_000, PowerProfile.ECONOMY)
        assertEquals(180_000, m.legacyOwnedMillis)
        assertEquals(0, m.proposedOwnedMillis)
        assertEquals(10, m.requestedSensorHz)
        assertEquals(10_000_000, m.requestedBatchLatencyUs)
    }
}
