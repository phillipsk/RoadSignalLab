package io.roadsignal.sdk

import android.content.Context
import io.roadsignal.core.ReplayScenario
import java.io.Closeable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/**
 * Optional Java callback adapter. Callbacks run on main; close cancels this adapter's pending
 * operations.
 */
class JavaRoadSignalClient(context: Context) : Closeable {
    fun interface Callback {
        fun onComplete(result: SdkResult)
    }

    private val sdk = RoadSignal.get(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun grantConsent(callback: Callback) = call(callback) { sdk.grantConsent() }

    fun replay(scenario: ReplayScenario, callback: Callback) =
        call(callback) { sdk.startReplay(scenario) }

    fun revokeConsentAndDelete(callback: Callback) = call(callback) { sdk.revokeConsentAndDelete() }

    fun interface StateListener {
        fun onState(snapshot: SdkSnapshot)
    }

    fun observe(listener: StateListener): Closeable {
        val job = scope.launch { sdk.state.collect { listener.onState(it) } }
        return Closeable { job.cancel() }
    }

    private fun call(callback: Callback, operation: suspend () -> SdkResult) {
        scope.launch {
            val result =
                try {
                    operation()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    SdkResult(false, "SDK operation failed; no plaintext fallback.")
                }
            callback.onComplete(result)
        }
    }

    override fun close() {
        scope.cancel()
    }
}
