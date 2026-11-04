package io.roadsignal.sdk.internal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.*
import android.location.*
import android.os.*
import androidx.core.content.ContextCompat
import io.roadsignal.core.*
import kotlin.math.sqrt

/**
 * Copies callback values immediately, owns registrations, and requests batching without an explicit
 * SDK wake lock.
 */
internal class DeviceCapture(
    private val context: Context,
    private val offer: (MotionSample) -> Unit,
    private val failure: () -> Unit,
) : SensorEventListener, LocationListener {
    private val thread = HandlerThread("RoadSignal-sensors").apply { start() }
    private val handler = Handler(thread.looper)
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val location = context.getSystemService(LocationManager::class.java)
    private var peakLinear = 0.0
    private var lastLinearTimestampMs = 0L
    private var peakRotation = 0.0
    private var lastRotationTimestampMs = 0L

    // The host declares location permission. Both grants are checked here; revocation races are
    // caught below.
    @android.annotation.SuppressLint("MissingPermission")
    fun start(profile: PowerProfile) {
        try {
            val sensor = sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            if (sensor != null)
                sensors.registerListener(
                    this,
                    sensor,
                    1_000_000 / profile.sensorHz,
                    profile.batchLatencyUs,
                    handler,
                )
            sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
                sensors.registerListener(
                    this,
                    it,
                    1_000_000 / profile.sensorHz,
                    profile.batchLatencyUs,
                    handler,
                )
            }
            val fine =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
            val coarse =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
            if (!fine && !coarse) {
                failure()
                return
            }
            val providers = location.getProviders(true)
            val provider =
                if (fine && LocationManager.GPS_PROVIDER in providers) LocationManager.GPS_PROVIDER
                else if (LocationManager.NETWORK_PROVIDER in providers)
                    LocationManager.NETWORK_PROVIDER
                else null
            if (provider == null) {
                failure()
                return
            }
            location.requestLocationUpdates(
                provider,
                profile.locationIntervalMs,
                0f,
                this,
                thread.looper,
            )
        } catch (_: SecurityException) {
            failure()
        } catch (_: RuntimeException) {
            failure()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        val magnitude = sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble())
        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                peakLinear = maxOf(peakLinear, magnitude)
                lastLinearTimestampMs = event.timestamp / 1_000_000
            }
            Sensor.TYPE_GYROSCOPE -> {
                peakRotation = maxOf(peakRotation, magnitude)
                lastRotationTimestampMs = event.timestamp / 1_000_000
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onLocationChanged(fix: Location) {
        val now = fix.elapsedRealtimeNanos / 1_000_000
        // Without provider speed we wait; inventing zero would falsely terminate a trip.
        if (!fix.hasSpeed() || !fix.hasAccuracy()) return
        val acceleration = if (now - lastLinearTimestampMs in 0..15_000) peakLinear else 0.0
        val rotation = if (now - lastRotationTimestampMs in 0..15_000) peakRotation else 0.0
        val copied =
            MotionSample(
                now,
                fix.speed.toDouble(),
                fix.accuracy.toDouble(),
                fix.latitude,
                fix.longitude,
                acceleration,
                angularVelocityRadPerSec = rotation,
            )
        peakLinear = 0.0
        peakRotation = 0.0
        offer(copied)
    }

    override fun onProviderDisabled(provider: String) {
        failure()
    }

    override fun onProviderEnabled(provider: String) {}

    @Deprecated("Platform compatibility callback")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    fun stop() {
        sensors.unregisterListener(this)
        runCatching { location.removeUpdates(this) }
        thread.quitSafely()
    }
}
