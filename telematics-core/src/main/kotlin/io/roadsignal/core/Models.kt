package io.roadsignal.core

/** All timestamps are monotonic elapsed milliseconds, never wall-clock deltas. */
data class MotionSample(
    val elapsedMs: Long,
    val speedMps: Double,
    val accuracyMeters: Double = 5.0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val linearAccelerationMps2: Double = 0.0,
    val simulatedPhoneHandling: Boolean = false,
    val angularVelocityRadPerSec: Double = 0.0,
)

enum class RecordingState {
    STOPPED,
    ARMED,
    CANDIDATE,
    RECORDING,
    SETTLING,
}

enum class EventKind {
    HARD_BRAKE,
    RAPID_ACCELERATION,
    PHONE_HANDLING_FIXTURE,
    IMPACT_CANDIDATE,
}

enum class TripEnd {
    STATIONARY,
    USER_STOP,
    DATA_GAP,
}

enum class TripRole {
    UNCLASSIFIED,
    DRIVER,
    PASSENGER,
}

enum class SourceMode {
    REPLAY,
    DEVICE,
}

enum class PowerProfile(val sensorHz: Int, val batchLatencyUs: Int, val locationIntervalMs: Long) {
    ECONOMY(10, 10_000_000, 10_000),
    BALANCED(20, 5_000_000, 5_000),
    RESPONSIVE(50, 0, 2_000),
}

data class DrivingEvent(val kind: EventKind, val elapsedMs: Long, val magnitude: Double)

data class RoutePoint(val latitude: Double, val longitude: Double)

data class TripSummary(
    val id: String,
    val startElapsedMs: Long,
    val endElapsedMs: Long,
    val distanceMeters: Double,
    val maxSpeedMps: Double,
    val events: List<DrivingEvent>,
    val route: List<RoutePoint>,
    val endReason: TripEnd,
    val role: TripRole = TripRole.UNCLASSIFIED,
    val source: SourceMode = SourceMode.REPLAY,
) {
    /** Illustrative coaching index; deliberately not an insurance-risk or crash model. */
    val coachingScore: Int
        get() =
            (100 - events.sumOf { if (it.kind == EventKind.IMPACT_CANDIDATE) 25 else 8 }).coerceIn(
                0,
                100,
            )
}

data class EngineConfig(
    val startSpeedMps: Double = 3.0,
    val startSamples: Int = 3,
    val stopSpeedMps: Double = 1.0,
    val stopDwellMs: Long = 15_000,
    val maxGapMs: Long = 30_000,
    val maxAccuracyMeters: Double = 50.0,
    val maxSpeedMps: Double = 80.0,
    val eventCooldownMs: Long = 3_000,
) {
    init {
        require(startSpeedMps > stopSpeedMps && stopSpeedMps >= 0)
        require(startSamples >= 2 && stopDwellMs > 0 && maxGapMs > 0)
        require(maxAccuracyMeters > 0 && maxSpeedMps > startSpeedMps && eventCooldownMs >= 0)
    }
}

data class EngineUpdate(
    val state: RecordingState,
    val completed: TripSummary? = null,
    val rejectedReason: String? = null,
)
