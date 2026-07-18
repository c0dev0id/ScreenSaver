package de.codevoid.screensaver

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("screensaver", Context.MODE_PRIVATE)
    }

    var brightnessCap: Float
        get() = prefs.getFloat("brightness_cap", 0.8f)
        set(v) = prefs.edit().putFloat("brightness_cap", v).apply()

    // Clamped so values stored by older versions (range was 0.01-0.30) stay valid
    var emaAlpha: Float
        get() = prefs.getFloat("ema_alpha", BrightnessController.DEFAULT_ALPHA)
            .coerceIn(BrightnessController.MIN_ALPHA, BrightnessController.MAX_ALPHA)
        set(v) = prefs.edit().putFloat("ema_alpha", v).apply()

    var darkLux: Float
        get() = prefs.getFloat("dark_lux", 10f)
        set(v) = prefs.edit().putFloat("dark_lux", v).apply()

    var brightLux: Float
        get() = prefs.getFloat("bright_lux", 50_000f)
        set(v) = prefs.edit().putFloat("bright_lux", v).apply()

    var autoOffMinutes: Int
        get() = prefs.getInt("auto_off_minutes", 5)
        set(v) = prefs.edit().putInt("auto_off_minutes", v).apply()
}
