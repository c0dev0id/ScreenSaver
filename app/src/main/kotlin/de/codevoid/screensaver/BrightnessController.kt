package de.codevoid.screensaver

import android.content.ContentResolver
import android.provider.Settings
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10

class BrightnessController(private val resolver: ContentResolver) {

    var alpha: Float = DEFAULT_ALPHA
    var capFraction: Float = 1.0f
    var darkLux: Float = 10f
    var brightLux: Float = 50_000f

    private var latestLux = -1f
    private var deviationTicks = 0

    var smoothedLux = -1f
        private set
    var targetBrightness = -1
        private set

    companion object {
        const val MIN_ALPHA = 0.001f
        const val MAX_ALPHA = 0.01f
        const val DEFAULT_ALPHA = 0.003f
        private const val MIN_BRIGHTNESS = 5
        private const val MAX_BRIGHTNESS = 255
        // Keep the curve's log-scale span from collapsing if dark/bright points cross
        private const val MIN_LOG_SPAN = 0.3f
        private const val CATCHUP_DEVIATION = 0.3f     // log10 decades, ~2x
        private const val CATCHUP_DELAY_TICKS = 50     // 10 s at 200 ms ticks
        private const val CATCHUP_FACTOR = 20f
        private const val MAX_CATCHUP_ALPHA = 0.05f
    }

    // Light sensors only report on change, so this may not be called for long
    // stretches. The EMA is advanced in tick() instead, from the latest reading.
    fun onLuxReading(lux: Float) {
        latestLux = lux
        if (smoothedLux < 0f) {
            smoothedLux = lux
            targetBrightness = luxToBrightness(lux)
        }
    }

    fun onCurveChanged() {
        if (smoothedLux >= 0f) targetBrightness = luxToBrightness(smoothedLux)
    }

    fun tick() {
        if (latestLux >= 0f && smoothedLux >= 0f) {
            val a = effectiveAlpha()
            smoothedLux = a * latestLux + (1f - a) * smoothedLux
            targetBrightness = luxToBrightness(smoothedLux)
        }
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

    // A pure EMA makes "slow reaction" mean slow even for permanent changes:
    // walking into a dark room would take 15+ minutes to reach the dark target.
    // When the raw reading stays far from the smoothed value (>2x, i.e. 0.3
    // log-decades) for more than 10 s, the change is not a passing shadow -
    // speed the filter up until it has caught up.
    private fun effectiveAlpha(): Float {
        val deviation = abs(log10(latestLux + 1f) - log10(smoothedLux + 1f))
        if (deviation > CATCHUP_DEVIATION) deviationTicks++ else deviationTicks = 0
        return if (deviationTicks > CATCHUP_DELAY_TICKS)
            (alpha * CATCHUP_FACTOR).coerceAtMost(MAX_CATCHUP_ALPHA)
        else alpha
    }

    private fun maxBrightness() =
        (MAX_BRIGHTNESS * capFraction).toInt().coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)

    private fun luxToBrightness(lux: Float): Int {
        val logDark = log10(darkLux.coerceAtLeast(1f))
        val logBright = log10(brightLux.coerceAtLeast(1f)).coerceAtLeast(logDark + MIN_LOG_SPAN)
        val fraction = ((log10(lux.coerceAtLeast(1f)) - logDark) / (logBright - logDark)).coerceIn(0f, 1f)
        val max = maxBrightness()
        return (MIN_BRIGHTNESS + fraction * (max - MIN_BRIGHTNESS)).toInt()
    }

    private fun readBrightness(): Int =
        Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)

    private fun writeBrightness(value: Int) {
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
    }
}
