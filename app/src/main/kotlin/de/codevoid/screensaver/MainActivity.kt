package de.codevoid.screensaver

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var sensorMaxLux = 0f  // 0 = unknown
    private var lightMaxLux = LIGHT_MAX_LUX  // light-point slider cap

    private val luxListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val range = if (sensorMaxLux > 0f) " (sensor max ${formatLux(sensorMaxLux)})" else ""
            findViewById<TextView>(R.id.label_current_lux).text =
                "Light sensor: ${formatLux(event.values[0])}$range"
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        setContentView(R.layout.activity_main)
        sensorManager = getSystemService(SensorManager::class.java)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        lightSensor?.let {
            // A light point above what the sensor can report would make max
            // brightness unreachable - cap the slider (and any stored value).
            sensorMaxLux = it.maximumRange
            lightMaxLux = sensorMaxLux.coerceIn(LIGHT_MIN_LUX * 2f, LIGHT_MAX_LUX)
            if (Prefs.brightLux > lightMaxLux) Prefs.brightLux = lightMaxLux
        }
        setupSliders()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        updateServiceState()
        lightSensor?.let {
            sensorManager.registerListener(luxListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        // One permission flow per resume; the next onResume picks up the other.
        if (!Settings.System.canWrite(this)) {
            Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
        } else if (!getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(packageName)) {
            // Doze must not throttle the service's tick loop on battery
            @Suppress("BatteryLife")
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(luxListener)
        super.onPause()
    }

    private fun setupSliders() {
        setupDarkPointSlider()
        setupLightPointSlider()
        setupCapSlider()
        setupWindowSlider()
        setupAutoOffSlider()
    }

    private fun setupDarkPointSlider() {
        val slider = findViewById<SeekBar>(R.id.slider_dark_point)
        val label = findViewById<TextView>(R.id.label_dark_point)
        slider.max = 100
        slider.progress = luxToProgress(Prefs.darkLux, DARK_MIN_LUX, DARK_MAX_LUX)
        updateDarkLabel(label, Prefs.darkLux)
        slider.setOnSeekBarChangeListener(onChange { progress ->
            val lux = progressToLux(progress, DARK_MIN_LUX, DARK_MAX_LUX)
            Prefs.darkLux = lux
            updateDarkLabel(label, lux)
        })
    }

    private fun setupLightPointSlider() {
        val slider = findViewById<SeekBar>(R.id.slider_light_point)
        val label = findViewById<TextView>(R.id.label_light_point)
        slider.max = 100
        slider.progress = luxToProgress(Prefs.brightLux, LIGHT_MIN_LUX, lightMaxLux)
        updateLightLabel(label, Prefs.brightLux)
        slider.setOnSeekBarChangeListener(onChange { progress ->
            val lux = progressToLux(progress, LIGHT_MIN_LUX, lightMaxLux)
            Prefs.brightLux = lux
            updateLightLabel(label, lux)
        })
    }

    private fun setupCapSlider() {
        val slider = findViewById<SeekBar>(R.id.slider_brightness_cap)
        val label = findViewById<TextView>(R.id.label_brightness_cap)
        slider.max = 50
        slider.progress = (Prefs.brightnessCap * 100).toInt() - 50
        updateCapLabel(label, Prefs.brightnessCap)
        slider.setOnSeekBarChangeListener(onChange { progress ->
            val cap = (progress + 50) / 100f
            Prefs.brightnessCap = cap
            updateCapLabel(label, cap)
        })
    }

    private fun setupWindowSlider() {
        val slider = findViewById<SeekBar>(R.id.slider_sensitivity)
        val label = findViewById<TextView>(R.id.label_sensitivity)
        val range = BrightnessController.MAX_WINDOW - BrightnessController.MIN_WINDOW
        slider.max = range
        slider.progress = Prefs.reactionWindow - BrightnessController.MIN_WINDOW
        updateWindowLabel(label, Prefs.reactionWindow)
        slider.setOnSeekBarChangeListener(onChange { progress ->
            val window = progress + BrightnessController.MIN_WINDOW
            Prefs.reactionWindow = window
            updateWindowLabel(label, window)
        })
    }

    private fun setupAutoOffSlider() {
        val slider = findViewById<SeekBar>(R.id.slider_auto_off)
        val label = findViewById<TextView>(R.id.label_auto_off)
        slider.max = 60
        slider.progress = Prefs.autoOffMinutes
        updateAutoOffLabel(label, Prefs.autoOffMinutes)
        slider.setOnSeekBarChangeListener(onChange { progress ->
            Prefs.autoOffMinutes = progress
            updateAutoOffLabel(label, progress)
        })
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btn_start).setOnClickListener {
            if (!Settings.System.canWrite(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
                return@setOnClickListener
            }
            startForegroundService(Intent(this, BrightnessService::class.java))
        }
        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            startService(Intent(this, BrightnessService::class.java).setAction(BrightnessService.ACTION_STOP))
        }
    }

    private fun updateDarkLabel(tv: TextView, lux: Float) {
        tv.text = "Min brightness below: ${formatLux(lux)}"
    }

    private fun updateLightLabel(tv: TextView, lux: Float) {
        tv.text = "Max brightness above: ${formatLux(lux)}"
    }

    private fun updateCapLabel(tv: TextView, cap: Float) {
        tv.text = "Battery brightness cap: ${(cap * 100).toInt()}%"
    }

    private fun updateWindowLabel(tv: TextView, window: Int) {
        val secs = (window * BrightnessController.TICK_MS / 1_000L).toInt()
        val desc = when {
            window <= 15 -> "Fast"
            window <= 32 -> "Medium"
            else -> "Slow"
        }
        tv.text = "Smoothing: $desc (${secs}s)"
    }

    private fun updateServiceState() {
        val tv = findViewById<TextView>(R.id.label_service_state)
        if (BrightnessService.isRunning) {
            tv.text = "● Running"
            tv.setTextColor(ContextCompat.getColor(this, R.color.service_running))
        } else {
            tv.text = "○ Stopped"
            tv.setTextColor(ContextCompat.getColor(this, R.color.service_stopped))
        }
    }

    private fun updateAutoOffLabel(tv: TextView, minutes: Int) {
        tv.text = if (minutes == 0) "Auto-off: disabled"
                  else "Auto-off after $minutes min on battery"
    }

    private fun onChange(block: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) block(progress)
        }
        override fun onStartTrackingTouch(bar: SeekBar) = Unit
        override fun onStopTrackingTouch(bar: SeekBar) = Unit
    }

    // Lux spans orders of magnitude, so the sliders work on a log10 scale.
    private fun luxToProgress(lux: Float, minLux: Float, maxLux: Float): Int =
        (100 * (log10(lux.coerceIn(minLux, maxLux)) - log10(minLux)) /
            (log10(maxLux) - log10(minLux))).roundToInt()

    private fun progressToLux(progress: Int, minLux: Float, maxLux: Float): Float =
        10f.pow(log10(minLux) + progress / 100f * (log10(maxLux) - log10(minLux)))

    companion object {
        private const val DARK_MIN_LUX = 1f
        private const val DARK_MAX_LUX = 1_000f
        private const val LIGHT_MIN_LUX = 100f
        private const val LIGHT_MAX_LUX = 50_000f
    }
}
