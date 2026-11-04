# SDK API and host integration

All APIs below are RoadSignal's original API. They are not CMT method names.

## Kotlin quick start

```kotlin
val sdk = RoadSignal.get(applicationContext)

// In a host Activity. Flow collection is independent of command callbacks.
lifecycleScope.launch {
    sdk.awaitReady()
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        sdk.state.collect { snapshot -> /* render */ }
    }
}

// Wire each callback to its own disclosed, explicit user action.
// Launch suspend callbacks from the host's lifecycle-owned scope.
suspend fun onConsentAccepted() = sdk.grantConsent()  // does not start sensors
suspend fun onReplayRequested() = sdk.startReplay(ReplayScenario.COMMUTE)
suspend fun onStopRequested() = sdk.stop()            // releases producers
suspend fun onDeleteConfirmed() = sdk.revokeConsentAndDelete()
```

The Compose host uses `collectAsStateWithLifecycle`. Production hosts should collect with their lifecycle as well; an Activity collector should not control the lifetime of a running SDK capture.

| API | Contract |
| --- | --- |
| `RoadSignal.get(Context)` | Application-scoped singleton; no sensing on construction |
| `awaitReady()` / `refresh()` | Load encrypted local state; IO dispatched off main |
| `state: StateFlow<SdkSnapshot>` | Current consent, recording state, provenance, journal, counters and diagnostics |
| `grantConsent()` | Persists consent, does not request permissions or start capture |
| `startReplay(scenario, frameDelayMillis)` | Stops previous session, arms synthetic stream, returns start acceptance |
| `startLiveFromVisibleActivity(Activity)` | Requires consent, current policy, focused/visible Activity and location permission; Android remains final authority |
| `stop()` | Stops current producer/service; finalizes valid active trip if consent remains |
| `applyPolicy(CapturePolicy)` | Rejects stale/expired/oversized TTL; applies local enable/profile settings |
| `sensorCapabilities()` | Local sensor presence, wake-up flag and advertised FIFO capacity; does not prove actual batching |
| `classifyTrip(id, role)` | Persists a user-confirmed local role; no automatic driver inference |
| `setUploadSimulation(mode)` | Selects entirely local ACK/offline/retry-once/permanent-reject behavior |
| `enqueueUpload()` | Schedules unique constrained WorkManager work; not immediate completion |
| `retryDeadLetters()` | Explicitly returns failed entries to a fresh attempt budget |
| `revokeConsentAndDelete()` | Stops recording, cancels work, invalidates epochs/generations and deletes local data |

## Java interoperability

`RoadSignal.get(Context)` is `@JvmStatic`. `JavaRoadSignalClient` offers callbacks for consent, replay and deletion without exposing `Continuation` to a Java host. Callbacks run on main. Close the adapter when its host owner ends; closing the adapter cancels that adapter's pending operations and observers, not the SDK's application-scoped recording. Stop collection explicitly.

```java
JavaRoadSignalClient client = new JavaRoadSignalClient(context);
client.grantConsent(result -> {
    if (result.getAccepted()) {
        client.replay(ReplayScenario.COMMUTE, started -> {
            // Accepted means the replay started. Observe state for completion.
        });
    }
});
// Later: client.close();
```

## Permission and execution boundary

The host declares `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` in its own manifest, then explains location use before prompting. The SDK does not force those host declarations. Request coarse/fine together, accept the user's approximate choice, and handle denial without loops. Notification permission is a distinct UX choice. A notification permission grant is not the same as permission to start an FGS, and a missing notification grant does not remove the FGS's own platform obligations.

The sample requests no background-location permission and performs no automatic background start. User-started live recording can continue after the Activity is hidden under the platform's location FGS rules. A foreground service does not guarantee that the CPU remains awake, that every fix is delivered or that the process survives.

## Results, exceptions and cancellation

`SdkResult.accepted` reports a command's admission, not the success of an entire trip, upload or safety outcome. Guard failures use a result/message. Unexpected persistence/Keystore errors can still surface from suspending calls; hosts must handle them, as `LabViewModel` does. The Java adapter converts unexpected operational exceptions to a failed callback. Coroutine cancellation is propagated; it is not reclassified as a retry or a successful command.

`startReplay` starts work in the application scope. Canceling the caller's UI coroutine after command acceptance does not cancel collection; call `stop` or revoke consent. Snapshots conflate updates; durable summaries/outbox are the source of truth for finalized work. Diagnostics are bounded operational messages, not an audit log or production analytics pipeline.

## Compatibility review

Keep source, binary, behavior, storage and configuration compatibility separate. Adding a default parameter to a public Kotlin constructor can still change JVM descriptors. Adding an enum case can break exhaustive host logic. Changing `StateFlow` emission semantics or policy defaults can break apps without changing signatures. Persisted WorkManager class names are a deployment contract. Core ABI and Android descriptor baselines are necessary gates, not proof of complete compatibility.
