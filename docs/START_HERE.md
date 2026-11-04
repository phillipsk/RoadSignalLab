# Getting started with RoadSignal Lab

RoadSignal Lab explores the contracts between an Android SDK and its host application: capture, consent, durable work, state observation and artifact distribution.

## Build and run

Open the project in Android Studio with JDK 21 for Gradle. Install Android SDK platform 36 and run `sample-app` on Android 8+ (API 26+).

```sh
./gradlew :sample-app:assembleDebug
```

Choose **Explore the lab → Controls → Enable local SDK → Overview → Replay commute**. Replay needs no account, API key, network endpoint or location permission. Open Trips to inspect the persisted route, events and driver/passenger correction. Device capture is a separate explicit action with runtime permission handling.

## Explore the implementation

| Area | Entry points |
| --- | --- |
| Trip state machine | `TripEngine.accept` and core reducer tests |
| SDK ownership | `RoadSignal.get`, `startReplay`, `startLiveFromVisibleActivity` |
| Android capture | `CaptureService` and `DeviceCapture` |
| Durability and privacy | `TripStore.save`, `revokeAndDelete`, `sync` |
| Deferred work | `UploadWorker.doWork` |
| Host integration | `LabViewModel`, lifecycle-aware state collection, `JavaRoadSignalClient` |
| Artifact integration | `distribution-consumer`, publication and verification scripts |

Follow a sample's timestamp, thread, collection generation, consent epoch and storage boundary. Finalized trips are durable; an active partial trip is not checkpointed. The Partners screen uses an original mock adapter and explicitly reports unavailable real-vendor setup.

## Reproduce the journeys

[USER_JOURNEYS.md](USER_JOURNEYS.md) describes all 16 flows. [SCREENSHOT_GALLERY.md](SCREENSHOT_GALLERY.md) shows native captures. Lab contains Power, Outbox, Pipeline and Diagnostics views. Controls exposes consent, device start/stop and local deletion.

## Verify and distribute

```sh
scripts/verify.sh
scripts/distribute.sh
# Disposable emulator only; this resets the lab fixture data:
scripts/instrumented.sh
```

See [architecture](ARCHITECTURE.md), [SDK API contracts](SDK_API.md), [distribution](DISTRIBUTION.md), and [verification](VERIFICATION.md). The [research ledger](RESEARCH.md) links primary sources. [Implementation status](IMPLEMENTATION_STATUS.md) distinguishes implemented behavior, mocks and production gaps.
