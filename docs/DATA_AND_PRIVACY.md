# Data ownership, privacy and threat model

## Actual data flow

Replay creates fictional coordinates, speed and event fixtures. Device mode reads location/provider speed and scalar motion features only during an explicitly started session. Completed trip payloads are encrypted locally. The mock transport stores local acknowledgments and never sends a network request. The sample has no account, advertising ID, contact access, phone-content inspection or CMT token.

| Data | Storage | Lifetime / exposure |
| --- | --- | --- |
| Raw high-frequency sensor callbacks | Bounded in-memory peaks only | Reset on location processing; not raw-sensor history |
| Current trip samples/state | Memory | Lost if the process dies before finalization |
| Finalized route/events/role/distance | AES-GCM payload in app-private SQLite | Up to 50 retained trip rows; route/event caps apply |
| UUID trip IDs, sequence, wall time, queue attempts, consent epoch | Plain SQLite metadata | Not whole-database encryption |
| Local mock receipts | SQLite IDs | At most 100 retained receipts |
| Diagnostics | Memory, 30 fixed messages | No raw coordinates/tokens; not remotely collected |
| Location permission | Android OS | Host/user controlled; separate from SDK consent |

## Consent is an execution invariant

Granting consent only changes the local consent record. It does not start collection. Replay and device capture require a separate action. Revocation stops producers, advances the collection generation, updates durable consent/epoch and deletes trip/outbox/receipt rows transactionally. Pending saves using the old epoch fail. Scheduled work is canceled and rechecks consent before local processing. Tests exercise revocation amid concurrent replay commands.

For a future real transport, revocation must abort work where possible and fence subsequent acknowledgments. Data already transmitted cannot be made unsent; the backend needs deletion/retention contracts and reconciliation. The lab does not pretend that deleting local rows erases a remote system.

## Encryption scope and recovery

Android Keystore creates the AES key, with authenticated encryption and unique provider-generated IVs. Hardware/StrongBox backing is not asserted. Plaintext is necessarily present briefly in process memory during encoding/rendering. Database metadata and the app's own in-memory UI are not encrypted by this payload scheme. Rooted/compromised-device access and a malicious host process are outside the protection it provides.

The sample disables app backup and supplies explicit legacy and Android 12+ cloud/device-transfer exclusions. A real integrating host must exclude SDK ciphertext and related state from unsupported restores or design a reviewed restore/key strategy. Ciphertext without its Keystore key is not recoverable merely by reinstalling. Key errors never trigger plaintext writes. Logical row deletion is not a claim of physical flash sanitization or cryptographic erasure; the current implementation does not destroy and rotate the Keystore key on every delete.

## Threats and mitigations

| Threat | Current mitigation | Remaining work |
| --- | --- | --- |
| Accidental sensing before opt-in | Explicit setup, consent/start guards | Review host disclosure and consent provenance |
| Callback/save races after deletion | Collection generation + durable epoch + transaction | Multi-process coordination is unsupported |
| Local payload disclosure | App sandbox + Keystore-backed AES-GCM | Metadata exposure, memory, compromised host/device |
| Forged/expired configuration | Local revision/TTL validation | Authenticated transport, signatures, anti-rollback persistence |
| Duplicate sends / backend abuse | Local unique trip IDs and receipt mock | Authenticated server idempotency, user/tenant authorization, quotas |
| SDK logs leak route/token | Bounded fixed messages | Audit future logging, crash attachments and support bundles |
| Malicious dependency/update | Pinned versions/actions, wrapper checksum, CI checks | Reviewed dependency verification/SBOM and independent artifact provenance |
| Host integration conflicts | Minimal manifest surface and published consumer | More partner, Android, target-SDK and permission combinations |

The [Android SDK safety guidance](https://developer.android.com/guide/practices/sdk-best-practices) makes host-app disclosure/policy effects an SDK concern. This is an engineering lab, not a compliance attestation. Before production collection, define the product purpose, necessary fields, retention/deletion, consent UX, host disclosures, authorized recipients and ownership with the appropriate product/privacy teams.

## Merged manifest, not just our source manifest

WorkManager transitively adds `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, its initializer, job services and rescheduling receivers. These do not start RoadSignal capture at boot. Its scheduled mock delivery can be rescheduled by the dependency. The host adds coarse/fine location and notification permissions; the SDK adds foreground-service/type permissions. There is no background-location or internet permission in this sample. Debug tooling/test activities are separate from the release surface.

Review the final merged manifest and dependency closure for every release. An SDK's source manifest and AAR size alone do not describe the resulting host's permissions, startup cost or package size. `scripts/check_manifest.py` verifies this project's intended release boundary.
