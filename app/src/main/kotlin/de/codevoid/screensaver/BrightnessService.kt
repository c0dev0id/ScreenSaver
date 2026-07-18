package de.codevoid.screensaver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.core.app.NotificationCompat

class BrightnessService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var handler: Handler
    private lateinit var controller: BrightnessController
    private var lightSensor: Sensor? = null
    private var onAc = false
    private var started = false
    private var previousTimeout = -1

    private val acReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> onAcConnected()
                Intent.ACTION_POWER_DISCONNECTED -> onAcDisconnected()
            }
        }
    }

    private var tickCount = 0

    private val tickRunnable = object : Runnable {
        override fun run() {
            applyPrefs()
            controller.tick()
            if (++tickCount % NOTIF_UPDATE_TICKS == 0) updateNotification()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val autoOffRunnable = Runnable { turnScreenOff() }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        handler = Handler(Looper.getMainLooper())
        controller = BrightnessController(contentResolver)
        sensorManager = getSystemService(SensorManager::class.java)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            controller.setManualMode()
            detectAcState()
            registerReceiver(acReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }, RECEIVER_EXPORTED)
            lightSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            handler.post(tickRunnable)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        handler.removeCallbacks(autoOffRunnable)
        if (started) {
            sensorManager.unregisterListener(this)
            try { unregisterReceiver(acReceiver) } catch (_: Exception) {}
        }
        restoreTimeout()
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        controller.onLuxReading(event.values[0])
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    override fun onBind(intent: Intent?): IBinder? = null

    private fun detectAcState() {
        val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        onAc = plugged != 0
        applyPrefs()
    }

    private fun onAcConnected() {
        onAc = true
        handler.removeCallbacks(autoOffRunnable)
        restoreTimeout()
        applyPrefs()
        wakeScreen()
        updateNotification()
    }

    private fun onAcDisconnected() {
        onAc = false
        applyPrefs()
        scheduleAutoOff()
        updateNotification()
    }

    // Light sensors only report on change, so pref edits are picked up here
    // (every tick) rather than waiting for the next sensor event.
    private fun applyPrefs() {
        controller.alpha = Prefs.emaAlpha
        controller.darkLux = Prefs.darkLux
        controller.brightLux = Prefs.brightLux
        controller.capFraction = if (onAc) 1.0f else Prefs.brightnessCap
        controller.onCurveChanged()
    }

    private fun scheduleAutoOff() {
        val minutes = Prefs.autoOffMinutes
        if (minutes <= 0) return
        handler.postDelayed(autoOffRunnable, minutes * 60_000L)
    }

    private fun turnScreenOff() {
        val current = AndroidSettings.System.getInt(
            contentResolver, AndroidSettings.System.SCREEN_OFF_TIMEOUT, 30_000)
        if (previousTimeout < 0) previousTimeout = current
        AndroidSettings.System.putInt(contentResolver, AndroidSettings.System.SCREEN_OFF_TIMEOUT, 1_000)
        // Restore after the screen has gone dark
        handler.postDelayed({ restoreTimeout() }, 5_000L)
    }

    private fun restoreTimeout() {
        if (previousTimeout < 0) return
        AndroidSettings.System.putInt(
            contentResolver, AndroidSettings.System.SCREEN_OFF_TIMEOUT, previousTimeout)
        previousTimeout = -1
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        val pm = getSystemService(PowerManager::class.java)
        pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "ScreenSaver:wake"
        ).acquire(3_000L)
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Brightness Service", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, BrightnessService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val power = if (onAc) "On AC — full brightness"
                    else "On battery — cap ${(Prefs.brightnessCap * 100).toInt()}%"
        val text = if (controller.targetBrightness >= 0)
            "$power · ${"%.1f".format(controller.smoothedLux)} lx → ${controller.targetBrightness}/255"
        else power
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Dimmer")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    companion object {
        const val ACTION_STOP = "de.codevoid.screensaver.STOP"
        private const val NOTIF_ID = 1
        private const val CHANNEL_ID = "brightness_service"
        private const val TICK_MS = 200L
        private const val NOTIF_UPDATE_TICKS = 25  // refresh status line every 5 s
    }
}
