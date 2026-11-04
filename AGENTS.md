# RoadSignal Lab: instructions for coding agents

Read [README.md](README.md), [docs/IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md), and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) before modifying code. This is an original educational telematics SDK and Compose host, not CMT software.

## Non-negotiable contracts

- Never invent DriveWell/MobileAPI coordinates, methods, credentials, licenses or production results. `cmt-bridge` is an original mock integration port until sanctioned partner material is supplied.
- No sensing on construction or consent grant. Only explicit replay or a visible-Activity device-start action may arm capture. No SDK capture boot receiver, background-location request, secret auto-start, real emergency action or network telemetry.
- Keep synthetic, device, modeled and measured evidence labeled. Zero explicit SDK wake locks is not a battery-savings percentage. WorkManager/system locks are separate.
- Consent revocation must stop producers, invalidate collection generations, advance the durable consent epoch and delete trips/outbox/receipts. A queued callback must never resurrect deleted data.
- Preserve off-main disk/crypto work, bounded queues, bounded retention, monotonic sample timing, accuracy validation and resource cleanup. SDK code must never retain an Activity.
- Do not silently destroy encrypted data or fall back to plaintext after a key/schema error. SQLite metadata is not encrypted. Do not log coordinates or user identifiers.
- The current SDK is single-process. Finalized trips are durable; an active partial trip is not checkpointed. Do not claim production automatic trip discovery, validated crash detection or ML classification.

## Module map

- `telematics-core`: pure JVM reducer, replay fixtures, quality gates, policy and retry decisions.
- `telematics-sdk`: Android facade, bounded callback channel, location FGS, sensor capture, encrypted payload store, local-only WorkManager outbox.
- `cmt-bridge`: mock adapter and explicit unavailable real-vendor boundary.
- `sample-app`: Compose host, consent/permission UI, trip journal, workbench.
- `distribution-consumer`: independent shrinker smoke test; `-PconsumePublishedSdk=true` must resolve external Maven modules, not project substitutions.

## Required checks

Use JDK 21 to run Gradle; Java/Kotlin bytecode targets 17. Android compile/target SDK 36, min SDK 26. Versions are intentionally pinned in `gradle/libs.versions.toml`.

```sh
scripts/verify.sh
scripts/distribute.sh
# With one Android emulator attached and adb on PATH:
scripts/instrumented.sh
```

API baseline updates are review operations: `./gradlew :telematics-core:updateLegacyAbi` and `python3 scripts/check_api.py --update`. Never auto-update baselines merely to make CI green. Explain source, binary, behavior, data and configuration compatibility separately. Review Kotlin default parameters, data-class constructors/copy methods, Java signatures and persisted WorkManager class names.

## Working conventions

Public documentation covers SDK setup, behavior, validation and distribution. Store personal notes and draft material outside this repository. Keep local history archives outside the checkout and publish explicit current refs only.

Small cohesive commits; no unrelated edits. Keep research facts in `docs/RESEARCH.md` with primary-source links and review date. Update implementation status, user journeys and tests when behavior changes. Run the instrumented classes in the order in `scripts/instrumented.sh`: cold-start testing intentionally consumes the showcase fixture. Never run these destructive fixture resets against a personal phone. No subagent/delegation requirement is imposed by this file.

## Release

Treat published versions as immutable. Version source is `sdkVersion` in `gradle.properties`; tags must match it. The local build repository is disposable output; GitHub artifacts are the contained distribution. See [docs/DISTRIBUTION.md](docs/DISTRIBUTION.md) and [docs/RELEASE_PLAYBOOK.md](docs/RELEASE_PLAYBOOK.md). Do not claim signed provenance from the unsigned local manifest. CI tag attestations must actually succeed before claiming they exist.
