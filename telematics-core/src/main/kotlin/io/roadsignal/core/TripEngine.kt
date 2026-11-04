package io.roadsignal.core

import java.util.UUID
import kotlin.math.*

/**
 * Deterministic single-writer reducer. The Android facade serializes access; this class owns no
 * threads.
 */
class TripEngine(
    private val config: EngineConfig = EngineConfig(),
    private val source: SourceMode = SourceMode.REPLAY,
) {
    var state: RecordingState = RecordingState.STOPPED
        private set

    var acceptedSamples: Long = 0
        private set

    var rejectedSamples: Long = 0
        private set

    private var previous: MotionSample? = null
    private var candidateCount = 0
    private var firstCandidate: MotionSample? = null
    private var stationarySince: Long? = null
    private var start: Long? = null
    private var distance = 0.0
    private var maxSpeed = 0.0
    private val events = mutableListOf<DrivingEvent>()
    private val route = mutableListOf<RoutePoint>()
    private val lastEvent = mutableMapOf<EventKind, Long>()

    fun arm() {
        reset()
        state = RecordingState.ARMED
    }

    fun accept(sample: MotionSample): EngineUpdate {
        if (state == RecordingState.STOPPED)
            return EngineUpdate(state, rejectedReason = "not_armed")
        val reason =
            when {
                sample.elapsedMs < 0 -> "negative_timestamp"
                !sample.speedMps.isFinite() ||
                    sample.speedMps < 0 ||
                    sample.speedMps > config.maxSpeedMps -> "invalid_speed"
                !sample.accuracyMeters.isFinite() ||
                    sample.accuracyMeters < 0 ||
                    sample.accuracyMeters > config.maxAccuracyMeters -> "poor_accuracy"
                !sample.linearAccelerationMps2.isFinite() || sample.linearAccelerationMps2 < 0 ->
                    "invalid_acceleration"
                !sample.angularVelocityRadPerSec.isFinite() ||
                    sample.angularVelocityRadPerSec < 0 -> "invalid_rotation"
                (sample.latitude == null) != (sample.longitude == null) -> "incomplete_coordinates"
                sample.latitude != null &&
                    (!sample.latitude.isFinite() ||
                        sample.latitude !in -90.0..90.0 ||
                        !sample.longitude!!.isFinite() ||
                        sample.longitude !in -180.0..180.0) -> "invalid_coordinates"
                previous != null && sample.elapsedMs <= previous!!.elapsedMs -> "out_of_order"
                else -> null
            }
        if (reason != null) {
            rejectedSamples++
            return EngineUpdate(state, rejectedReason = reason)
        }
        var completed: TripSummary? = null
        val prev = previous
        if (prev != null && sample.elapsedMs - prev.elapsedMs > config.maxGapMs) {
            completed = finish(TripEnd.DATA_GAP, prev.elapsedMs)
            clearTrip()
            candidateCount = 0
            firstCandidate = null
            previous = null
            state = RecordingState.ARMED
        }
        acceptedSamples++
        if (state in listOf(RecordingState.ARMED, RecordingState.CANDIDATE)) {
            if (sample.speedMps >= config.startSpeedMps) {
                if (candidateCount == 0) firstCandidate = sample
                candidateCount++
                state = RecordingState.CANDIDATE
                if (candidateCount >= config.startSamples) {
                    state = RecordingState.RECORDING
                    // Start at confirmed detection; pre-confirmation distance is intentionally
                    // omitted.
                    start = sample.elapsedMs
                    maxSpeed = sample.speedMps
                }
            } else {
                candidateCount = 0
                firstCandidate = null
                state = RecordingState.ARMED
            }
        } else {
            val last = previous
            if (last != null) {
                val seconds = (sample.elapsedMs - last.elapsedMs) / 1000.0
                distance += (last.speedMps + sample.speedMps) * 0.5 * seconds
                val acceleration = (sample.speedMps - last.speedMps) / seconds
                if (seconds in 0.5..10.0) {
                    if (acceleration <= -3.5 && last.speedMps >= 5)
                        event(EventKind.HARD_BRAKE, sample, acceleration)
                    if (acceleration >= 3.0)
                        event(EventKind.RAPID_ACCELERATION, sample, acceleration)
                }
            }
            maxSpeed = max(maxSpeed, sample.speedMps)
            if (sample.simulatedPhoneHandling) event(EventKind.PHONE_HANDLING_FIXTURE, sample, 1.0)
            if (sample.linearAccelerationMps2 >= 35.0 && sample.speedMps >= 5)
                event(EventKind.IMPACT_CANDIDATE, sample, sample.linearAccelerationMps2)
            if (sample.speedMps <= config.stopSpeedMps) {
                if (stationarySince == null) stationarySince = sample.elapsedMs
                state = RecordingState.SETTLING
                if (sample.elapsedMs - stationarySince!! >= config.stopDwellMs) {
                    completed = finish(TripEnd.STATIONARY, sample.elapsedMs)
                    clearTrip()
                    candidateCount = 0
                    firstCandidate = null
                    state = RecordingState.ARMED
                }
            } else {
                stationarySince = null
                state = RecordingState.RECORDING
            }
        }
        if (start != null && sample.latitude != null && route.size < 500)
            route += RoutePoint(sample.latitude, sample.longitude!!)
        previous = sample
        return EngineUpdate(state, completed)
    }

    fun stop(): TripSummary? {
        val trip = finish(TripEnd.USER_STOP, previous?.elapsedMs ?: 0)
        reset()
        state = RecordingState.STOPPED
        return trip
    }

    private fun event(kind: EventKind, s: MotionSample, magnitude: Double) {
        if (events.size >= 200) return
        if (lastEvent[kind]?.let { s.elapsedMs - it < config.eventCooldownMs } == true) return
        events += DrivingEvent(kind, s.elapsedMs, magnitude)
        lastEvent[kind] = s.elapsedMs
    }

    private fun finish(reason: TripEnd, end: Long): TripSummary? = start?.let {
        TripSummary(
            UUID.randomUUID().toString(),
            it,
            end,
            distance,
            maxSpeed,
            events.toList(),
            route.toList(),
            reason,
            source = source,
        )
    }

    private fun clearTrip() {
        start = null
        stationarySince = null
        distance = 0.0
        maxSpeed = 0.0
        events.clear()
        route.clear()
        lastEvent.clear()
    }

    private fun reset() {
        clearTrip()
        candidateCount = 0
        firstCandidate = null
        previous = null
    }
}
