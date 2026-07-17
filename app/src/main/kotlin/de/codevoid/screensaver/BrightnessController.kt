package de.codevoid.screensaver

import android.content.ContentResolver
import android.provider.Settings
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10

class BrightnessController(private val resolver: ContentResolver) {

    var alpha: Float = 0.05f
    var capFraction: Float = 1.0f

    private var smoothedLux = -1f
    private var targetBrightness = -1

    companion object {
        private const val MIN_BRIGHTNESS = 5
        private const val MAX_BRIGHTNESS = 255
        private const val LOG_MAX_LUX = 4.699f  // log10(50_000)
    }

    fun onLuxReading(lux: Float) {
        smoothedLux = if (smoothedLux < 0f) lux
                      else alpha * lux + (1f - alpha) * smoothedLux
        targetBrightness = luxToBrightness(smoothedLux)
    }

    fun onCapChanged() {
        if (smoothedLux >= 0f) targetBrightness = luxToBrightness(smoothedLux)
    }

    fun tick() {
        if (targetBrightness < 0) return
        val current = readBrightness()
        if (current == targetBrightness) return
        val distance = targetBrightness - current
        val step = ceil(abs(distance) * 0.1f).toInt().coerceAtLeast(1)
        val next = if (distance > 0) (current + step).coerceAtMost(targetBrightness)
                   else (current - step).coerceAtLeast(targetBrightness)
        writeBrightness(next.coerceIn(MIN_BRIGHTNESS, maxBrightness()))
    }

    fun setManualMode() {
        Settings.System.putInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
    }

    private fun maxBrightness() =
        (MAX_BRIGHTNESS * capFraction).toInt().coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)

    private fun luxToBrightness(lux: Float): Int {
        val fraction = (log10(lux.coerceAtLeast(1f)) / LOG_MAX_LUX).coerceIn(0f, 1f)
        val max = maxBrightness()
        return (MIN_BRIGHTNESS + fraction * (max - MIN_BRIGHTNESS)).toInt()
    }

    private fun readBrightness(): Int =
        Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)

    private fun writeBrightness(value: Int) {
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
    }
}
