# CMT integration boundary

This module contains RoadSignal's original `PartnerTelematicsPort`, a working `MockCmtAdapter` and an explicit `UnconfiguredCmtAdapter`. No vendor binary or invented API signature is included.

| Public CMT surface | This prototype | Required before real integration |
| --- | --- | --- |
| DriveWell SDK family | Generic local fixture adapter | Sanctioned artifact, supported API/version, credentials and partner terms |
| MobileAPI SDK family | Research context, no claimed API compatibility | Family-specific integration and migration guidance |
| DriveWell Risk / trip insights | Illustrative local event/coaching view | Authorized backend contract and validated model outputs |
| Crash & Claims | Synthetic impact candidate only | Validated detection, response ownership and authorized service integration |
| Engage / family/rewards | Not implemented | Product-specific APIs and privacy/identity contracts |
| App+Tag programs | Not implemented | Licensed BLE protocol, hardware, permissions and validation |

The public SDK page and engineering material are linked in [the source ledger](../docs/RESEARCH.md). The available material did not establish official Android coordinates or a complete integration contract. This is a deliberate, reviewable mock boundary rather than an unrelated vendor SDK presented as CMT.
