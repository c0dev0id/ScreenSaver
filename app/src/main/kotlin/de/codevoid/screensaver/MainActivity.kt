package de.codevoid.screensaver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        setContentView(R.layout.activity_main)
        setupSliders()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        if (!Settings.System.canWrite(this)) {
            Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
        }
    }

    private fun setupSliders() {
        setupCapSlider()
        setupSensitivitySlider()
        setupAutoOffSlider()
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

    private fun setupSensitivitySlider() {
        val slider = findViewById<SeekBar>(R.id.slider_sensitivity)
        val label = findViewById<TextView>(R.id.label_sensitivity)
        slider.max = 100
        slider.progress = ((Prefs.emaAlpha - 0.01f) / 0.29f * 100).toInt()
        updateSensitivityLabel(label, Prefs.emaAlpha)
        slider.setOnSeekBarChangeListener(onChange { progress ->
            val alpha = 0.01f + progress / 100f * 0.29f
            Prefs.emaAlpha = alpha
            updateSensitivityLabel(label, alpha)
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

    private fun updateCapLabel(tv: TextView, cap: Float) {
        tv.text = "Battery brightness cap: ${(cap * 100).toInt()}%"
    }

    private fun updateSensitivityLabel(tv: TextView, alpha: Float) {
        val desc = when {
            alpha < 0.05f -> "Slow"
            alpha < 0.15f -> "Medium"
            else -> "Fast"
        }
        tv.text = "Reaction speed: $desc"
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
}
