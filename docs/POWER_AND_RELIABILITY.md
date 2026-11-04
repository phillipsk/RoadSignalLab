# Power, capture quality and reliability

## The three questions that must stay separate

1. **Is the process eligible to run?** Foreground service priority and execution rules affect this.
2. **Is the CPU awake right now?** Explicit or platform-owned wake locks and sensor wake-up behavior affect this.
3. **Is the product receiving useful data?** Accuracy, gaps, batching, missing hardware and classification affect this.

A visible service is not an immortal process or a continuous CPU guarantee. Removing an owned wake lock is not proof of a measured energy improvement. A lower platform metric with missing trips is not success.

[Android vitals](https://developer.android.com/google/play/vitals/excessive-wakelock) currently defines excessive use using aggregated non-exempt partial wake-lock duration and affected sessions, with a 28-day bad-behavior threshold. Its current wording differs from a casual description of screen-off time; use the platform definition and date-stamp any dashboard analysis. Framework exemptions and attribution need to be understood before interpreting an SDK-owned tag. The lab does not generate an Android vitals report.

## What runs here

- No explicit partial-wake-lock acquisition or custom wake-lock permission is written by this SDK. WorkManager and system APIs may independently use locks.
- No polling thread runs while idle. Active capture registers sensors once, uses one handler and sends copied location samples through a bounded channel.
- Requested linear-acceleration and gyro rates are 10/20/50 Hz. Requested batching is 10/5/0 seconds for economy/balanced/responsive. Actual rate and latency depend on sensor hardware/OS.
- Location request intervals are 10/5/2 seconds. These are requests, not hard real-time deadlines or guarantees of fix quality.
- The host workbench's 180 seconds versus zero seconds is a counterfactual ownership model. It is neither a before/after device experiment nor a savings claim.

[AOSP batching](https://source.android.com/docs/core/interaction/sensors/batching) depends on a real FIFO/sensor hub and distinguishes wake-up from non-wake-up delivery. A non-wake-up stream can lose data when the CPU sleeps and buffers fill. A requested maximum report latency does not create hardware buffering that the device lacks. `sensorCapabilities()` exposes advertised capacity so the lab can discuss this constraint honestly.

## Measurement plan for a production SDK

Use a preregistered device/OS/host-target matrix and paired repeatable drives. Capture baseline and treatment with matched route, screen state, network, charge state, thermal conditions, sensor configuration and model version. Include app-only and permission-limited cases. Emulator correctness tests cannot measure real power consumption or hardware FIFO reliability.

| Dimension | Proposed measurement | Guardrail |
| --- | --- | --- |
| Capture | Matched trip count/duration, start delay, distance error, sensor-window coverage | No meaningful decline relative to labeled ground truth |
| Gaps | Inter-fix distribution, longest gap, dropped/FIFO-overflow rate | Stratify screen-off, device, OS and thermal state |
| Power | Energy/battery drain over controlled windows, CPU residency, wake-lock ownership/duration, wakeups | Report units and uncertainty; normalize duration and environment |
| Stability | Crash/ANR rate, memory, callback backlog, initialization latency | Observe the host app, not just SDK process code |
| Network | Bytes, batch count, retry amplification and radio wakeups | Cost per active recording hour and completed trip |
| Adoption | SDK/app version, compatible config, activation time, observed behavior | Publish, activate and verify are distinct milestones |

Use Perfetto/system traces, device power tooling, Android vitals supplied by host owners, redacted custom counters and field comparisons. Do not collect raw routes merely to monitor runtime health. Choose a real power instrument and validation methodology before promising a percentage.

## Failure and bottleneck matrix

| Failure mode | Current behavior | Production design work |
| --- | --- | --- |
| No location permission / Android blocks start | Explicit failure; replay still usable | Partner UX, denial funnel, platform/version validation |
| Approximate/poor/missing speed fix | Quality gate or wait; no invented zero speed | Confidence-aware fallback and documented degraded mode |
| Long gap / duplicate timestamp | Segment or reject deterministically | Sensor-window recovery and gap-attributed metrics |
| Missing linear/gyro sensor | Location path can continue | Capability-specific feature models and monitoring |
| CPU asleep / FIFO unsupported | No explicit ownership lock | Hardware validation; scoped fallback only if justified by evidence |
| Queue overload | Drop counter; fixed bound | Backpressure budget and quality impact attribution |
| Database/Keystore error | No plaintext fallback; expose failure/unreadable state | Key lifecycle, recovery UX and carefully scoped operational signals |
| Process killed during drive | Active partial trip is lost; no auto restart | Checkpoints and OS-compliant restart contracts |
| Host force-stop / user revokes access | Collection cannot assume automatic recovery | Respect user intent and explain limits |
| Offline / server failure | Local mock outbox with finite retries | Auth, HTTP classifications, jitter, Retry-After and replay protection |
| Partner remains on old SDK | No code-side fix can update their installed binary | Adoption ownership, support windows and version-specific controls |

## Rollout experiment design

Assign consistently at a driver/app-install level, stratify by SDK/host/OS/hardware, and analyze repeated trips with the correct clustering. Monitor recording completeness and errors alongside runtime cost. Avoid unrelated model changes in the same experiment where possible; otherwise track them as confounders. Define stopping rules and confidence requirements before rollout. A remote rollback can change only code paths already shipped; it cannot repair a binary that does not understand the setting. This is a proposed design, not a deployed experiment.
