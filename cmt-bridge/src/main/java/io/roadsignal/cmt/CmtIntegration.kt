package io.roadsignal.cmt

import io.roadsignal.core.ReplayScenario
import io.roadsignal.sdk.RoadSignal
import io.roadsignal.sdk.SdkResult

/** Public research boundary. These are OUR integration ports, not signatures from a CMT SDK. */
interface PartnerTelematicsPort {
    val integrationMode: String

    suspend fun demonstrateTrip(): SdkResult
}

object CmtIntegrationInfo {
    const val MODE = "MOCK — no CMT binary or API connection"
    const val PUBLIC_SDK_PAGE = "https://www.cmtelematics.com/cmt-mobile-sdk/"
    const val ACCESS_NOTE =
        "No official Android artifact coordinates or ungated integration guide were verified in the public material reviewed on 2026-11-04. Obtain licensed binaries, exact APIs, credentials and permitted redistribution terms from CMT before implementing the vendor adapter."
}

/**
 * Exercises the host/adapter boundary using our SDK's deterministic fixtures; sends no data to CMT.
 */
class MockCmtAdapter(private val sdk: RoadSignal) : PartnerTelematicsPort {
    override val integrationMode: String = CmtIntegrationInfo.MODE

    override suspend fun demonstrateTrip(): SdkResult = sdk.startReplay(ReplayScenario.COMMUTE)
}

/** Fails closed rather than compiling a fictional vendor API into the sample. */
class UnconfiguredCmtAdapter : PartnerTelematicsPort {
    override val integrationMode: String = "CMT integration unavailable — partner setup required"

    override suspend fun demonstrateTrip(): SdkResult =
        SdkResult(false, CmtIntegrationInfo.ACCESS_NOTE)
}
