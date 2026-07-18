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

    var emaAlpha: Float
        get() = prefs.getFloat("ema_alpha", 0.05f)
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
