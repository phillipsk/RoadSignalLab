package io.roadsignal.sdk

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.roadsignal.core.*
import io.roadsignal.sdk.internal.*
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Application-scoped, consent-gated facade. Initialize explicitly with application context. No
 * sensing starts during construction. Single-process SDK; the host owns runtime permission UX. Call
 * [revokeConsentAndDelete] to stop sensing, invalidate pending callbacks and delete local data.
 */
class RoadSignal private constructor(private val context: Context) {
    private val backgroundErrors = CoroutineExceptionHandler { _, _ ->
        generation.incrementAndGet()
        replay?.cancel()
        context.stopService(Intent(context, CaptureService::class.java))
        mutable.update {
            it.copy(
                recording = RecordingState.STOPPED,
                notice = "SDK background operation failed. Capture stopped; no plaintext fallback.",
            )
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + backgroundErrors)
    private val mutex = Mutex()
    private val store = TripStore(context)
    private var engine = TripEngine()
    private var replay: Job? = null
    private var expiry: Job? = null
    private val generation = AtomicLong()
    internal val captureGeneration: Long
        get() = generation.get()

    private data class Envelope(val generation: Long, val sample: MotionSample)

    private val samples = Channel<Envelope>(64)
    private val dropped = AtomicLong()
    private val policyGate = PolicyGate()
    private var policyExpiresElapsedMs = Long.MAX_VALUE
    private var policy = CapturePolicy(0, true, PowerProfile.BALANCED, Long.MAX_VALUE)
    private val mutable = MutableStateFlow(SdkSnapshot())
    val state: StateFlow<SdkSnapshot> = mutable.asStateFlow()

    init {
        scope.launch { StoreChanges.revision.collect { mutex.withLock { refreshLocked() } } }
        scope.launch {
            for (envelope in samples) mutex.withLock {
                if (
                    envelope.generation == generation.get() &&
                        mutable.value.source == SourceMode.DEVICE &&
                        mutable.value.recording != RecordingState.STOPPED
                )
                    acceptLocked(envelope.sample)
            }
        }
    }

    suspend fun awaitReady() {
        serialized { refreshLocked() }
    }

    suspend fun grantConsent(): SdkResult = serialized {
        store.grantConsent()
        refreshLocked()
        notice("Consent granted. Replay is synthetic; live capture is a separate choice.")
        SdkResult(true, mutable.value.notice)
    }

    suspend fun revokeConsentAndDelete(): SdkResult = serialized {
        generation.incrementAndGet()
        expiry?.cancel()
        replay?.cancel()
        replay = null
        engine.stop()
        mutable.update { it.copy(recording = RecordingState.STOPPED) }
        context.stopService(Intent(context, CaptureService::class.java))
        // The same SQLite transaction flips consent, advances the epoch and deletes
        // trips/outbox/receipts.
        store.revokeAndDelete()
        UploadWorker.cancel(context)
        while (samples.tryReceive().isSuccess) {}
        dropped.set(0)
        mutable.value =
            SdkSnapshot(
                profile = policy.profile,
                policyRevision = policy.revision,
                notice =
                    "Consent revoked. Recording stopped; local trips, outbox and receipts deleted.",
            )
        refreshLocked()
        SdkResult(true, mutable.value.notice)
    }

    suspend fun startReplay(scenario: ReplayScenario, frameDelayMillis: Long = 65): SdkResult =
        serialized {
            require(frameDelayMillis >= 0)
            if (!store.read().consent)
                return@serialized SdkResult(false, "Grant consent before replaying.")
            if (
                !policy.enabled ||
                    android.os.SystemClock.elapsedRealtime() >= policyExpiresElapsedMs
            )
                return@serialized SdkResult(false, "Capture policy is disabled or expired.")
            stopLocked()
            engine = TripEngine(source = SourceMode.REPLAY)
            engine.arm()
            val epoch = store.token().second
            val replayGeneration = generation.get()
            mutable.update {
                it.copy(
                    recording = engine.state,
                    source = SourceMode.REPLAY,
                    acceptedSamples = 0,
                    rejectedSamples = 0,
                )
            }
            scheduleExpiryLocked()
            notice("Synthetic replay: ${scenario.title}")
            replay = scope.launch {
                try {
                    for (sample in scenario.samples()) {
                        delay(frameDelayMillis)
                        mutex.withLock {
                            if (
                                generation.get() != replayGeneration ||
                                    !store.token().first ||
                                    store.token().second != epoch
                            )
                                return@launch
                            acceptLocked(sample)
                        }
                    }
                    mutex.withLock {
                        if (generation.get() != replayGeneration) return@launch
                        engine.stop()?.let { store.save(it, epoch) }
                        mutable.update { it.copy(recording = RecordingState.STOPPED) }
                        refreshLocked()
                        notice("Replay complete. Inspect the trip, event timeline and outbox.")
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutex.withLock {
                        engine.stop()
                        mutable.update { it.copy(recording = RecordingState.STOPPED) }
                        notice(
                            "Replay stopped after a storage or processing error; no plaintext fallback.",
                        )
                    }
                }
            }
            SdkResult(true, "Replay started")
        }

    /**
     * Call only from the user's action in a visible Activity, after consent and location
     * permission.
     */
    suspend fun startLiveFromVisibleActivity(activity: Activity): SdkResult = serialized {
        if (!store.read().consent) return@serialized SdkResult(false, "Consent is required.")
        if (!policy.enabled || android.os.SystemClock.elapsedRealtime() >= policyExpiresElapsedMs)
            return@serialized SdkResult(false, "Capture policy is disabled or expired.")
        if (activity.isFinishing || activity.isDestroyed || !activity.hasWindowFocus())
            return@serialized SdkResult(
                false,
                "Start live capture from a visible, focused Activity.",
            )
        val fine =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarse =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse)
            return@serialized SdkResult(
                false,
                "Location permission was not granted. Replay needs no permission.",
            )
        stopLocked()
        engine = TripEngine(source = SourceMode.DEVICE)
        engine.arm()
        mutable.update {
            it.copy(
                source = SourceMode.DEVICE,
                recording = RecordingState.ARMED,
                acceptedSamples = 0,
                rejectedSamples = 0,
            )
        }
        scheduleExpiryLocked()
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CaptureService::class.java),
            )
            notice(
                "Device capture started. GPS quality gates may keep the detector armed while stationary.",
            )
            SdkResult(true, mutable.value.notice)
        } catch (_: RuntimeException) {
            engine.stop()
            mutable.update { it.copy(recording = RecordingState.STOPPED) }
            notice("Android blocked foreground capture. Start again while the app is visible.")
            SdkResult(false, mutable.value.notice)
        }
    }

    suspend fun stop(): SdkResult = serialized {
        stopLocked()
        refreshLocked()
        notice("Capture stopped. Completed trips remain local.")
        SdkResult(true, mutable.value.notice)
    }

    suspend fun applyPolicy(candidate: CapturePolicy): SdkResult = serialized {
        val error = policyGate.validate(candidate, System.currentTimeMillis())
        if (error != null) return@serialized SdkResult(false, "Policy rejected: $error")
        policy = candidate
        policyExpiresElapsedMs =
            android.os.SystemClock.elapsedRealtime() +
                (candidate.expiresAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0)
        mutable.update { it.copy(profile = policy.profile, policyRevision = policy.revision) }
        if (!policy.enabled) stopLocked()
        else if (
            mutable.value.source == SourceMode.DEVICE &&
                mutable.value.recording != RecordingState.STOPPED
        )
            context.startService(
                Intent(context, CaptureService::class.java).setAction(CaptureService.RECONFIGURE),
            )
        if (policy.enabled && mutable.value.recording != RecordingState.STOPPED)
            scheduleExpiryLocked()
        notice(
            "Local policy ${policy.revision} applied: ${policy.profile.name}. No remote server was contacted.",
        )
        SdkResult(true, mutable.value.notice)
    }

    suspend fun classifyTrip(id: String, role: TripRole) = serialized {
        store.classify(id, role)
        refreshLocked()
        notice("Role is user-confirmed, not inferred by a model.")
    }

    suspend fun setUploadSimulation(mode: UploadSimulation) = serialized {
        store.setSimulation(mode)
        refreshLocked()
        notice("Local-only transport scenario: ${mode.name}")
    }

    suspend fun retryDeadLetters() = serialized {
        store.retryDeadLetters()
        refreshLocked()
    }

    fun enqueueUpload() {
        UploadWorker.enqueue(context)
    }

    suspend fun refresh() = serialized { refreshLocked() }

    internal fun offerDeviceSample(sample: MotionSample, expectedGeneration: Long) {
        if (expectedGeneration != generation.get()) return
        if (samples.trySend(Envelope(expectedGeneration, sample)).isFailure)
            dropped.incrementAndGet()
    }

    internal fun stopFromNotification() {
        scope.launch { stop() }
    }

    internal fun captureFailed() {
        scope.launch {
            mutex.withLock {
                stopLocked()
                notice("Capture unavailable or permission revoked. Collection stopped safely.")
            }
        }
    }

    internal fun serviceDestroyed(expectedGeneration: Long) {
        scope.launch {
            mutex.withLock {
                if (
                    generation.get() == expectedGeneration &&
                        mutable.value.source == SourceMode.DEVICE &&
                        mutable.value.recording != RecordingState.STOPPED
                ) {
                    engine.stop()?.let { store.save(it, store.read().epoch) }
                    mutable.update { it.copy(recording = RecordingState.STOPPED) }
                    refreshLocked()
                }
            }
        }
    }

    private fun acceptLocked(sample: MotionSample) {
        if (android.os.SystemClock.elapsedRealtime() >= policyExpiresElapsedMs) {
            stopLocked()
            notice("Policy expired; collection stopped.")
            return
        }
        val token = store.token()
        if (!token.first) return
        val update = engine.accept(sample)
        update.completed?.let {
            if (!store.save(it, token.second))
                notice("Storage quota reached; sync acknowledged history or delete local data.")
        }
        mutable.update {
            it.copy(
                recording = engine.state,
                latestRotationRadPerSec = sample.angularVelocityRadPerSec,
                acceptedSamples = engine.acceptedSamples,
                rejectedSamples = engine.rejectedSamples,
                droppedSamples = dropped.get(),
            )
        }
        if (update.rejectedReason != null) notice("Sample rejected: ${update.rejectedReason}")
        if (update.completed != null) refreshLocked()
    }

    private fun stopLocked() {
        generation.incrementAndGet()
        expiry?.cancel()
        expiry = null
        replay?.cancel()
        replay = null
        engine.stop()?.let { if (store.token().first) store.save(it, store.token().second) }
        mutable.update { it.copy(recording = RecordingState.STOPPED) }
        context.stopService(Intent(context, CaptureService::class.java))
    }

    fun sensorCapabilities(): List<SensorCapability> {
        val manager = context.getSystemService(android.hardware.SensorManager::class.java)
        return listOf(
                "Linear acceleration" to android.hardware.Sensor.TYPE_LINEAR_ACCELERATION,
                "Gyroscope" to android.hardware.Sensor.TYPE_GYROSCOPE,
            )
            .map { (label, type) ->
                val sensor = manager.getDefaultSensor(type)
                SensorCapability(
                    label,
                    sensor != null,
                    sensor?.isWakeUpSensor ?: false,
                    sensor?.fifoMaxEventCount ?: 0,
                )
            }
    }

    private fun scheduleExpiryLocked() {
        expiry?.cancel()
        if (policyExpiresElapsedMs == Long.MAX_VALUE) return
        val revision = policy.revision
        val deadline = policyExpiresElapsedMs
        expiry = scope.launch {
            // A timer can resume near a millisecond boundary. Recompute remaining elapsed time.
            while (true) {
                val remaining = deadline - android.os.SystemClock.elapsedRealtime()
                if (remaining <= 0) break
                delay(remaining)
            }
            mutex.withLock {
                if (
                    policy.revision == revision &&
                        android.os.SystemClock.elapsedRealtime() >= deadline
                ) {
                    stopLocked()
                    notice("Policy expired; collection stopped. Apply a fresh local policy.")
                }
            }
        }
    }

    private suspend fun <T> serialized(block: () -> T): T =
        withContext(Dispatchers.IO) { mutex.withLock { block() } }

    private fun refreshLocked() {
        val s = store.read()
        mutable.update {
            it.copy(
                consent = s.consent,
                consentEpoch = s.epoch,
                trips = s.trips,
                pendingUploads = s.pending,
                deadLetters = s.dead,
                localReceipts = s.receipts,
                unreadableRecords = s.unreadable,
                uploadSimulation = s.simulation,
            )
        }
    }

    private fun notice(message: String) {
        mutable.update {
            it.copy(notice = message, diagnostics = (it.diagnostics + message).takeLast(30))
        }
    }

    companion object {
        const val VERSION = BuildConfig.SDK_VERSION
        // Constructor is private and get() passes only context.applicationContext, never an
        // Activity.
        @android.annotation.SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: RoadSignal? = null

        @JvmStatic
        fun get(context: Context): RoadSignal =
            instance
                ?: synchronized(this) {
                    instance ?: RoadSignal(context.applicationContext).also { instance = it }
                }
    }
}
