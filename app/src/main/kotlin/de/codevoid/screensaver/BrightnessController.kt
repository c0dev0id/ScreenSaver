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

    val targetBrightness: Int
        get() = if (smoothedLux < 0f) -1 else luxToBrightness(smoothedLux)

    companion object {
        const val TICK_MS = 5_000L
        // Alpha applies once per 5 s tick; time constant = TICK_MS / alpha,
        // i.e. ~20 s at MAX_ALPHA (Fast) and ~200 s at MIN_ALPHA (Slow).
        const val MIN_ALPHA = 0.025f
        const val MAX_ALPHA = 0.25f
        const val DEFAULT_ALPHA = 0.075f
        // The Android brightness slider is gamma-corrected: linear value 5/255
        // already sits at ~20% slider position and is visibly bright on some
        // panels. 1 is the framework slider's own minimum (0 can mean "off"
        // or be rejected depending on the display driver).
        private const val MIN_BRIGHTNESS = 1
        private const val MAX_BRIGHTNESS = 255
        // Keep the curve's log-scale span from collapsing if dark/bright points cross
        private const val MIN_LOG_SPAN = 0.3f
        private const val CATCHUP_DEVIATION = 0.3f  // log10 decades, ~2x
        private const val CATCHUP_DELAY_MS = 10_000L
        private val CATCHUP_DELAY_TICKS = (CATCHUP_DELAY_MS / TICK_MS).toInt()
        private const val CATCHUP_FACTOR = 20f
        private const val MAX_CATCHUP_ALPHA = 0.5f
    }

    // Light sensors only report on change, so this may not be called for long
    // stretches. The EMA is advanced in tick() instead, from the latest reading.
    fun onLuxReading(lux: Float) {
        latestLux = lux
        if (smoothedLux < 0f) smoothedLux = lux
    }

    fun tick() {
        if (smoothedLux >= 0f) {
            // A pure EMA makes "slow reaction" mean slow even for permanent
            // changes: walking into a dark room would take 15+ minutes to reach
            // the dark target. When the raw reading stays far from the smoothed
            // value (>2x, i.e. 0.3 log-decades) for more than 10 s, the change
            // is not a passing shadow - speed the filter up until caught up.
            val deviated = abs(logLux(latestLux) - logLux(smoothedLux)) > CATCHUP_DEVIATION
            deviationTicks = if (deviated) deviationTicks + 1 else 0
            val a = if (deviationTicks >= CATCHUP_DELAY_TICKS)
                (alpha * CATCHUP_FACTOR).coerceAtMost(MAX_CATCHUP_ALPHA)
            else alpha
            smoothedLux = a * latestLux + (1f - a) * smoothedLux
        }
        val target = targetBrightness
        if (target < 0) return
        val current = readBrightness()
        if (current == target) return
        // Half the remaining distance per 5 s tick: target reached in ~4 ticks
        val distance = target - current
        val step = ceil(abs(distance) * 0.5f).toInt().coerceAtLeast(1)
        val next = if (distance > 0) (current + step).coerceAtMost(target)
                   else (current - step).coerceAtLeast(target)
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

    private fun logLux(lux: Float) = log10(lux.coerceAtLeast(1f))

    private fun luxToBrightness(lux: Float): Int {
        val logDark = logLux(darkLux)
        val logBright = logLux(brightLux).coerceAtLeast(logDark + MIN_LOG_SPAN)
        val fraction = ((logLux(lux) - logDark) / (logBright - logDark)).coerceIn(0f, 1f)
        val max = maxBrightness()
        return (MIN_BRIGHTNESS + fraction * (max - MIN_BRIGHTNESS)).toInt()
    }

    private fun readBrightness(): Int =
        Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)

    private fun writeBrightness(value: Int) {
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
    }
}
