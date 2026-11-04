# Verification evidence

Verified locally on **November 4, 2026**. There are **34 distinct test cases**: 20 JVM behavior tests, 11 Android SDK tests, and 3 host tests. For version 0.1.1, the Android suite passed on the API 36 device below; do not count repeated runs as additional distinct tests.

| Check | Observed result |
| --- | --- |
| Core reducer/policy/retry tests | 20 passed, zero failures/errors |
| Android 16 / API 36 / ARM64 / 16 KB pages | 11 SDK tests + showcase, cold process and device/privacy tests passed |
| Presentation refresh / Android 16 / 16 KB pages | 14 Android cases passed after the host footer update; 18 native captures refreshed at 1080×2424 |
| Native screenshot capture | 18 final PNGs; no generated or altered UI images |
| Actual location FGS | Became foreground with runtime permissions, then stopped; confirmed separately from SDK state |
| Cold-process restart | Finalized journal reloaded; capture remained stopped |
| Encryption/schema | Actual Keystore/SQLite round trip, encrypted-payload inspection and v1→v2 fixture migration passed |
| Privacy/concurrency | Final revocation wins concurrent replay/config commands; old consent epochs cannot save |
| Delivery | Retry-once, bounded offline retry, permanent failure, explicit dead-letter recovery and local idempotent receipts passed |
| Policy | Revision/TTL admission, active expiry, disable and subsequent-start rejection passed |
| Debug and minified release | Built with AGP 8.13.2/Kotlin 2.3.10; earlier incompatible shrinker was replaced |
| Lint | Release checks pass; intentional old-target warning is tracked below |
| Public contract | Core ABI and Android/bridge public JVM descriptor baselines pass |
| Merged manifest | Location FGS boundary, no background-location/internet permission, WorkManager rescheduling surface checked |
| Published consumer | All three components resolved as external Maven modules; debug and minified builds pass |
| Contained publication | Core JAR, two AARs, POM/module metadata/source artifacts, Maven ZIP/checksums/unsigned local manifest produced |
| Documentation | Local Markdown/image links verified; portable reading copy generated separately |

The project intentionally targets SDK 36 while testing on newer hosts where available; lint can flag this relative to the latest platform. This is an explicit pinned prototype support decision, not a claim of the newest target-SDK policy compliance for a future Play submission. The manifest includes explicit backup/transfer exclusions. See [machine-readable evidence](verification-evidence.json) and [capture provenance](screenshots/capture-manifest.json).

## Remote execution

The [main run](https://github.com/phillipsk/RoadSignalLab/actions/runs/41864024780) and [v0.1.1 tag run](https://github.com/phillipsk/RoadSignalLab/actions/runs/41864273168) target source commit `d1c8f9aa44d8fe3eed377bcaf6150e74e56e05be`. They were queued at publication on November 4, 2026, while GitHub reported an [Actions/Pages incident](https://www.githubstatus.com/incidents/xr3myzj7ms51). Queued or configured jobs are not counted as passed tests.

The workflow builds debug/minified apps, checks APIs/manifests, consumes the published Maven artifacts, and runs all 14 Android cases on API 31/35/36 x86_64 emulators. Its version-tag job builds and attests the exact release ZIP/APK before attaching them to the matching release. Inspect the current run result before claiming provenance exists for a CI artifact.

The [local example bundle](https://github.com/phillipsk/RoadSignalLab/releases/download/v0.1.1/RoadSignalLab-0.1.1-local-example.zip) is available separately. It contains locally verified source, native screenshots, technical docs, a debug APK and an 80-file Maven repository ZIP with checksums. It is a local build, not a signed CI attestation. CI-produced assets use separate canonical filenames.

## UI-test stability observation

An API 36 showcase run previously failed with Compose's `Detected multithreaded access to SnapshotStateObserver` exception after all 11 SDK cases passed. A targeted retry passed all 14 Android cases. The root cause remains unestablished; a successful retry is not a fix or a measured flake rate. Further isolation should examine test-clock/draw synchronization and Compose/platform versions.

## Scope limits

No licensed CMT SDK transaction or backend call was run. No physical road test, energy measurement, Android vitals collection, sensor-FIFO validation, validated safety model or production deployment is claimed. The default walkthrough is synthetic. Device testing verifies actual foreground-service lifecycle separately from physical capture quality. Finalized-state restart tests do not establish continuity of an active trip across process death. Core descriptor checks and a recompiled/shrunk consumer are not a frozen-old-binary compatibility test.
