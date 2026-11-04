# Research, evidence and design implications

Reviewed **November 4, 2026**. Public material establishes the problem space; it does not disclose CMT's complete SDK architecture, unpublished algorithms or licensed integration contract. Links are primary sources unless explicitly marked as a discovery result. Proposed RoadSignal designs below are our decisions, not assertions about CMT internals.

## Telematics SDK context

[DriveWell's public SDK page](https://www.cmtelematics.com/cmt-mobile-sdk/) describes automatic recording, driver/passenger and transport classification, risky-event insights, trip details and App+Tag programs. RoadSignal demonstrates the surrounding engineering contracts and a simpler detector. It does not reproduce those validated capabilities.

The [November 3 wake-lock article](https://www.cmtelematics.com/engineering/cutting-partial-wake-locks-by-up-to-99-in-the-insurance-apps-our-sdk-powers/) discusses both DriveWell and MobileAPI, independently controlled lock acquisition paths, reversible configuration, monitoring recording quality, and coordinating partner adoption. The reported improvements are reductions in individual apps' excessive wake-lock session rates, not portfolio-average battery savings. Our implementation uses no explicit SDK lock; its displayed comparison is a model. The operational lesson is to track shipping, activation and verified adoption separately.

[Modernizing Android Development](https://www.cmtelematics.com/engineering/modernizing-android-development/) describes a modular Kotlin/Compose/Flow foundation, shared partner UI, typed DataStore persistence and centralized versions. We use modularity and reactive host state, but choose SQLite transactions for trip/outbox atomicity. A vendor's white-label UI architecture is not proof that its proprietary sensing engine has identical internals.

[Commercial-vehicle risk measurement](https://www.cmtelematics.com/mobility-ai-research/accurately-measuring-risk-across-commercial-vehicles/) explains why event interpretation depends on vehicle class. RoadSignal's fixed thresholds are illustrative, with no vehicle-normalized risk claim. [CMT's experimentation article](https://www.cmtelematics.com/mobility-ai-research/ab-testing-mobile-apps/) discusses correlated repeated observations and rollout/measurement pitfalls. Our proposed power experiment uses driver-level assignment and quality guardrails rather than treating each trip as an independent subject.

[CMT's validated-studies collection](https://www.cmtelematics.com/get-cmts-validated-studies/) points to peer-reviewed and government research. That collection is a reading index, not validation of RoadSignal's detector. [CMT's privacy position](https://www.cmtelematics.com/news/stop-covert-data-collection-for-better-consumer-trust-and-road-safety/) emphasizes consumer choice. This lab makes consent and deletion visible and keeps collection off by default.

## Public SDK availability and identity checks

Public search included company/SDK names and GitHub organization/repository searches. No official Android Maven coordinates or ungated CMT integration guide were verified. Absence from this search is not proof that no public repository exists. The company [support policy](https://www.cmtelematics.com/support-policy/) and SDK page provide a legitimate route to partner material; we did not register an account, accept partner terms, guess credentials or reverse engineer binaries.

**Do not confuse companies:** [Mobile-Telematics on GitHub](https://github.com/Mobile-Telematics) is Damoov. Its [Android integration guide](https://docs.damoov.com/docs/add-the-sdk-to-your-app-android) publishes its own artifact and settings. It is useful as a peer comparison, not evidence of a CMT API. The independent `api-evangelist/cambridge-mobile-telematics` profile is third-party discovery material, not official SDK documentation, and is not used to implement vendor calls.

## Platform and distribution source ledger

| Primary reference | What it constrains in this project |
| --- | --- |
| [Android location permissions](https://developer.android.com/develop/sensors-and-location/location/permissions) | Foreground/approximate access and explicit runtime UX |
| [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types) | `location` type and foreground-service permissions |
| [Background FGS-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) | Visible user initiation; no assumption of unrestricted background starts |
| [Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby) | Deferred background CPU/network; field coverage beyond an emulator |
| [Sensors overview](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview) | Availability, timestamps, background restrictions and listener lifecycle |
| [SensorManager reference](https://developer.android.com/reference/android/hardware/SensorManager) | Sampling/batching request parameters and unregistration |
| [AOSP sensor batching](https://source.android.com/docs/core/interaction/sensors/batching) | FIFO-dependent batching and wake-up/non-wake-up distinctions |
| [Android excessive wake locks](https://developer.android.com/google/play/vitals/excessive-wakelock) | Current vitals definition; do not substitute modeled lock duration for this metric |
| [Wake-lock alternatives](https://developer.android.com/develop/background-work/background-tasks/awake/wakelock) | Prefer suitable platform mechanisms; explicit ownership must be justified |
| [WorkManager releases and guide](https://developer.android.com/jetpack/androidx/releases/work) | Durable deferrable work; pinned dependency, not a real-time capture loop |
| [Android Keystore](https://developer.android.com/privacy-and-security/keystore) | Non-exportable key API; hardware backing is device-dependent |
| [SDK user-safety guidance](https://developer.android.com/guide/practices/sdk-best-practices) | Host disclosure and policy responsibilities |
| [Data-use declaration](https://developer.android.com/privacy-and-security/declare-data-use) | Real SDK data collection must be represented in host disclosures |
| [Kotlin/AGP/R8 support](https://developer.android.com/build/kotlin-support) | Verified Kotlin metadata support; AGP 8.13.2 replaced an incompatible older shrinker |
| [Android library publication](https://developer.android.com/build/publish-library/configure-pub-variants) | Release variant, POM, Gradle metadata and dependency scopes |
| [Kotlin ABI validation](https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html) | API review; pinned Kotlin 2.3.10 exposes `checkLegacyAbi`/`updateLegacyAbi`, unlike newer documentation names |
| [Gradle dependency verification](https://docs.gradle.org/current/userguide/dependency_verification.html) | Checksums are a reviewed trust-on-first-use input, not automatic proof |
| [Gradle Maven publishing](https://docs.gradle.org/current/userguide/publishing_maven.html) | Actual publication metadata and consumer resolution |
| [SemVer 2.0](https://semver.org/spec/v2.0.0.html) | Explicit API, immutable releases, and honest prerelease expectations |
| [GitHub artifact attestations](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations) | CI provenance is distinct from our unsigned local manifest |
| [SLSA current specification](https://slsa.dev/spec/v1.2/) | Supply-chain goals; no certification/level claim is made |

## Peer/industry comparison

| Surface | Public evidence | Lesson for an SDK designer |
| --- | --- | --- |
| CMT DriveWell / MobileAPI | Product capability material and selected engineering explanations; partner contract not verified | Keep integration claims bounded by available evidence |
| Damoov Android SDK | Public Maven/settings integration guide | Initialization, permissions and enable/disable contracts must be explicit; vendor identities matter |
| Google Driver SDK | [Official release notes](https://developers.google.com/maps/documentation/mobility/services/resources/relnotes_driver_sdk_android) describe support/dependency changes | A consumer's app can inherit compatibility changes through dependency upgrades |
| Android framework / Jetpack | Public execution, storage and scheduling contracts | OS policy and host-app target SDK are part of the SDK's effective runtime environment |

The common challenges are not solved by importing one library: noisy/incomplete data, battery cost, permission abandonment, device diversity, upgrade lag, backend contracts and observability all cross organizational boundaries. RoadSignal implements a tractable slice and documents the remaining production work.
