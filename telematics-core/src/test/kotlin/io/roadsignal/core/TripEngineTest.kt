package io.roadsignal.core

import org.junit.Assert.*
import org.junit.Test

class TripEngineTest {
    private fun movingEngine(): TripEngine =
        TripEngine().apply {
            arm()
            listOf(4.0, 6.0, 8.0).forEachIndexed { i, s -> accept(MotionSample(i * 2000L, s)) }
        }

    @Test
    fun doesNotCollectUntilArmed() {
        val e = TripEngine()
        assertEquals("not_armed", e.accept(MotionSample(1, 5.0)).rejectedReason)
        assertEquals(0, e.acceptedSamples)
    }

    @Test
    fun requiresConsecutiveEvidenceAndResetsCandidate() {
        val e = TripEngine()
        e.arm()
        e.accept(MotionSample(0, 4.0))
        e.accept(MotionSample(1000, 0.0))
        e.accept(MotionSample(2000, 4.0))
        e.accept(MotionSample(3000, 4.0))
        assertEquals(RecordingState.CANDIDATE, e.state)
        e.accept(MotionSample(4000, 4.0))
        assertEquals(RecordingState.RECORDING, e.state)
    }

    @Test
    fun ignoresDuplicateAndReorderedSamples() {
        val e = movingEngine()
        val accepted = e.acceptedSamples
        assertEquals("out_of_order", e.accept(MotionSample(4000, 9.0)).rejectedReason)
        assertEquals("out_of_order", e.accept(MotionSample(2, 9.0)).rejectedReason)
        assertEquals(accepted, e.acceptedSamples)
        assertEquals(2, e.rejectedSamples)
    }

    @Test
    fun rejectsNonfiniteAndOutOfRangeInputs() {
        val e = TripEngine()
        e.arm()
        listOf(
                MotionSample(0, Double.NaN),
                MotionSample(1, Double.POSITIVE_INFINITY),
                MotionSample(2, -1.0),
                MotionSample(3, 100.0),
                MotionSample(4, 5.0, Double.NaN),
                MotionSample(5, 5.0, 400.0),
                MotionSample(6, 5.0, latitude = 91.0, longitude = 0.0),
                MotionSample(7, 5.0, latitude = 3.0),
                MotionSample(8, 5.0, linearAccelerationMps2 = Double.NaN),
            )
            .forEach { assertNotNull(e.accept(it).rejectedReason) }
        assertEquals(0, e.acceptedSamples)
        assertEquals(9, e.rejectedSamples)
    }

    @Test
    fun briefTrafficStopDoesNotSplitTrip() {
        val e = movingEngine()
        e.accept(MotionSample(6000, 0.0))
        e.accept(MotionSample(8000, 0.0))
        e.accept(MotionSample(10000, 6.0))
        assertEquals(RecordingState.RECORDING, e.state)
        assertNotNull(e.stop())
    }

    @Test
    fun completesOnStationaryDwell() {
        val e = movingEngine()
        e.accept(MotionSample(6000, 0.0))
        val t = e.accept(MotionSample(22000, 0.0)).completed!!
        assertEquals(TripEnd.STATIONARY, t.endReason)
        assertEquals(RecordingState.ARMED, e.state)
    }

    @Test
    fun gapClosesAtLastValidFixAndDoesNotAddMissingDistance() {
        val e = movingEngine()
        e.accept(MotionSample(6000, 10.0))
        val t = e.accept(MotionSample(50000, 10.0)).completed!!
        assertEquals(TripEnd.DATA_GAP, t.endReason)
        assertEquals(6000, t.endElapsedMs)
        assertEquals(18.0, t.distanceMeters, .001)
        assertEquals(RecordingState.CANDIDATE, e.state)
    }

    @Test
    fun stationaryNoiseDoesNotProduceTrip() {
        val e = TripEngine()
        e.arm()
        repeat(500) { e.accept(MotionSample(it * 1000L, 0.3)) }
        assertNull(e.stop())
    }

    @Test
    fun highAccelerationHasARefractoryPeriod() {
        val e = movingEngine()
        e.accept(MotionSample(5000, 13.0))
        e.accept(MotionSample(6000, 18.0))
        assertEquals(1, e.stop()!!.events.count { it.kind == EventKind.RAPID_ACCELERATION })
    }

    @Test
    fun impactIsOnlyACandidateAndRequiresMovement() {
        val e = movingEngine()
        e.accept(MotionSample(6000, 10.0, linearAccelerationMps2 = 40.0))
        e.accept(MotionSample(8000, 0.0, linearAccelerationMps2 = 80.0))
        assertEquals(1, e.stop()!!.events.count { it.kind == EventKind.IMPACT_CANDIDATE })
    }

    @Test
    fun phoneHandlingExistsOnlyForAnExplicitFixture() {
        val e = movingEngine()
        e.accept(MotionSample(6000, 10.0))
        assertFalse(e.stop()!!.events.any { it.kind == EventKind.PHONE_HANDLING_FIXTURE })
    }

    @Test
    fun completedListsAreImmutableSnapshots() {
        val e = movingEngine()
        e.accept(MotionSample(6000, 12.0, latitude = 42.0, longitude = -71.0))
        val t = e.stop()!!
        e.arm()
        assertEquals(1, t.route.size)
    }

    @Test
    fun routeRetentionIsBounded() {
        val e = movingEngine()
        repeat(800) {
            e.accept(MotionSample(6000 + it * 2000L, 10.0, latitude = 42.0, longitude = -71.0))
        }
        assertEquals(500, e.stop()!!.route.size)
    }

    @Test
    fun replayScenariosProduceTripsWithCorrectProvenance() {
        ReplayScenario.entries.forEach { scenario ->
            val e = TripEngine()
            e.arm()
            val trips = scenario.samples().mapNotNull { e.accept(it).completed }
            assertTrue(scenario.name, trips.isNotEmpty())
            assertTrue(
                trips.all {
                    it.source == SourceMode.REPLAY &&
                        it.role == TripRole.UNCLASSIFIED &&
                        it.coachingScore in 0..100
                },
            )
        }
    }

    @Test
    fun scenarioGapProducesRejectionAndTwoSegments() {
        val e = TripEngine()
        e.arm()
        val trips = ReplayScenario.SENSOR_GAP.samples().mapNotNull { e.accept(it).completed }
        assertEquals(2, trips.size)
        assertEquals(1, e.rejectedSamples)
        assertEquals(TripEnd.DATA_GAP, trips.first().endReason)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidConfigurationFailsEarly() {
        EngineConfig(startSamples = 0)
    }
}
