package io.roadsignal.sdk

import android.app.*
import android.content.Intent
import android.os.IBinder
import io.roadsignal.sdk.internal.DeviceCapture

/** User-started, visible location FGS. Not sticky and never started at boot or by a replay. */
class CaptureService : Service() {
    private var capture: DeviceCapture? = null
    private var serviceGeneration: Long = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sdk = RoadSignal.get(this)
        if (intent?.action == STOP) {
            sdk.stopFromNotification()
            stopSelf()
            return START_NOT_STICKY
        }
        if (
            !sdk.state.value.consent ||
                sdk.state.value.recording == io.roadsignal.core.RecordingState.STOPPED
        ) {
            stopSelf()
            return START_NOT_STICKY
        }
        val channel = "roadsignal-capture"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    channel,
                    "RoadSignal live capture",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        val stop =
            PendingIntent.getService(
                this,
                0,
                Intent(this, CaptureService::class.java).setAction(STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            Notification.Builder(this, channel)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("RoadSignal is capturing this session")
                .setContentText("Local device data. Tap Stop to end capture.")
                .setOngoing(true)
                .addAction(Notification.Action.Builder(null, "Stop", stop).build())
                .build()
        try {
            startForeground(301, notification)
        } catch (_: RuntimeException) {
            sdk.captureFailed()
            stopSelf()
            return START_NOT_STICKY
        }
        capture?.stop()
        val generation = sdk.captureGeneration
        serviceGeneration = generation
        capture =
            DeviceCapture(this, { sdk.offerDeviceSample(it, generation) }, sdk::captureFailed)
                .also { it.start(sdk.state.value.profile) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        capture?.stop()
        capture = null
        RoadSignal.get(this).serviceDestroyed(serviceGeneration)
        super.onDestroy()
    }

    companion object {
        internal const val RECONFIGURE = "io.roadsignal.RECONFIGURE"
        private const val STOP = "io.roadsignal.STOP"
    }
}
