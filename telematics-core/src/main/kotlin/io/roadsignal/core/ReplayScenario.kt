package io.roadsignal.core

/** Synthetic, geographically fictitious replay; never a recording of the user's driving. */
enum class ReplayScenario(val title: String, val description: String) {
    COMMUTE("City commute", "Automatic start/stop, braking and illustrative coaching"),
    SENSOR_GAP("Tunnel & bad GPS", "Reject poor fixes, close on a long data gap, recover"),
    IMPACT("Impact candidate", "Synthetic spike for an event pipeline; no emergency response"),
    PASSENGER("Passenger ride", "A trip remains unclassified until the user labels it");

    fun samples(): List<MotionSample> {
        val speeds =
            listOf(
                0.0,
                0.0,
                4.0,
                6.0,
                8.0,
                12.0,
                16.0,
                18.0,
                18.0,
                18.0,
                9.0,
                7.0,
                12.0,
                18.0,
                18.0,
                16.0,
                12.0,
                8.0,
                4.0,
                0.0,
            ) + List(10) { 0.0 }
        return speeds.mapIndexed { index, speed ->
            val gap = if (this == SENSOR_GAP && index >= 13) 45_000 else 0
            MotionSample(
                index * 2000L + gap,
                speed,
                accuracyMeters = if (this == SENSOR_GAP && index == 8) 300.0 else 5.0,
                latitude = 42.0 + index * 0.00012,
                longitude = -71.0 + index * 0.00016,
                linearAccelerationMps2 = if (this == IMPACT && index == 8) 42.0 else 0.2,
                simulatedPhoneHandling = this == COMMUTE && index == 7,
            )
        }
    }
}
