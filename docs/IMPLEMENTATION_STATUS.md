# What this project actually does

This is an original educational SDK, not a port, implementation, licensed distribution or performance equivalent of CMT DriveWell/MobileAPI. The public API is version 0.1.1 and remains a prototype. Review the implementation and verification evidence for the supported behavior and remaining work.

| Capability | Status | Practical boundary |
| --- | --- | --- |
| Kotlin/Compose host with multiple journeys | Implemented | Synthetic data is the default |
| Independent pure JVM SDK engine | Implemented | Rule-based thresholds, not ML |
| Automatic trip start/stop inside an armed stream | Implemented | Three valid moving fixes and stationary dwell; no always-on discovery |
| GPS/location + linear acceleration + gyroscope | Implemented | User-started location foreground service; registration and batching requests, not guaranteed sensor delivery |
| Live permission/foreground-service handling | Implemented | Start from visible host UI; approximate/poor data may not meet quality gates |
| Sensor gap, bad fix, ordering and finite-value gates | Implemented | Conservative segmentation; distance during missing data is omitted |
| Braking/acceleration events, impact candidate | Implemented heuristics | No validated safety, insurance-risk or emergency inference |
| Driver/passenger classification | User correction | No automatic classifier; label is local |
| Phone handling | Explicit replay fixture only | No real distraction inference or phone-content access |
| Encrypted durable trip payloads | Implemented | AES-GCM/Android Keystore; metadata plaintext; finalized trips only |
| Local outbox, retries, permanent failure, receipts | Implemented | Mock server responses; no HTTP upload |
| WorkManager scheduling | Implemented | Connected-network constraint + exponential backoff; system scheduling is not immediate |
| Local capture policy with revision and TTL | Implemented | Local fixture; no deployed remote transport, signature or targeting service |
| No explicit SDK-owned partial wake lock | Implemented | Not a measured battery improvement; dependencies/platform may acquire their own locks |
| CMT integration | Mock + unavailable vendor adapter | No sanctioned artifact/API guide was verified; partner setup required |
| Core ABI + Android/bridge public descriptor checks | Implemented | Baselines are review gates; behavior compatibility still needs tests |
| Project-local Maven publishing + external consumer | Implemented | POM/AAR/JAR/module metadata and source artifacts, no commercial registry |
| Pinned GitHub Actions CI and tag attestation job | Configured | API 31/35/36, release upload and two-subject provenance; current-source results are tracked in verification evidence; no SLSA level claim |
| Long-lived recording/process-death continuation | Not implemented | Partial active trip can be lost; no silent restart |
| Always-on low-power discovery, activity recognition, BLE Tag | Design only | Permission, policy, hardware and field-validation work remains |
| Authentication, cloud scoring, crash response, remote config service | Not implemented | No tokens, backend account or external telemetry |

## Deliberate prototype limits

The SDK uses one process, one application-scoped instance and one collection at a time. Starting a new replay/device session stops the old one. It retains at most 50 trip summaries, 500 route points and 200 events per trip, plus a 64-element device sample queue. Route points after the cap are omitted rather than sampled; production would need a reviewed decimation policy. Gyroscope peaks are diagnostics, not a driver-behavior classifier. Sensor peaks and location speed are not synchronized sensor fusion.

The outbox stores unique finalized-trip IDs. Acknowledgments are local receipt rows, not a server-side exactly-once protocol. Role corrections are persisted locally and do not enqueue a new revision after acknowledgment. Retries have a finite attempt budget; dead-letter recovery is explicit. Real backend implementation requires payload revisions, authentication, authorization, idempotency at the server, timeout/rate-limit handling, consent-aware cancellation and contract tests.

There is no 99% battery claim. The workbench compares a hypothetical 180-second explicit lock with an explicit-lock-free design. It cannot infer battery consumption, wakelock-vitals session rate, capture completeness, or CMT's production results.
