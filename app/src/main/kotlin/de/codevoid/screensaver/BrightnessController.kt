package de.codevoid.screensaver

import android.content.ContentResolver
import android.provider.Settings
import kotlin.math.log10
import kotlin.math.pow

class BrightnessController(private val resolver: ContentResolver) {

    var windowSize: Int = DEFAULT_WINDOW
    var capFraction: Float = 1.0f
    var darkLux: Float = 10f
    var brightLux: Float = 50_000f

    private var latestLux = -1f
    private val luxBuffer = FloatArray(MAX_WINDOW)
    private var bufferHead = 0
    private var bufferFilled = 0
    private var lastWritten = -1

    var medianLux = -1f
        private set

    val targetBrightness: Int
        get() = if (medianLux < 0f) -1 else luxToBrightness(medianLux)

    companion object {
        const val TICK_MS = 500L
        const val MIN_WINDOW = 10
        const val MAX_WINDOW = 50
        const val DEFAULT_WINDOW = 25
        private const val MIN_BRIGHTNESS = 1
        private const val MAX_BRIGHTNESS = 255
        private const val MIN_LOG_SPAN = 0.3f
        private const val GAMMA = 2.2f
    }

    fun onLuxReading(lux: Float) {
        latestLux = lux
    }

    // Call when the screen turns on so stale off-screen readings don't delay
    // the first correct brightness write.
    fun resetBuffer() {
        bufferFilled = 0
        bufferHead = 0
        lastWritten = -1
    }

    fun tick() {
        if (latestLux < 0f) return
        if (bufferFilled == 0) {
            // Pre-fill so the very first median equals the current reading,
            // giving instant correct brightness instead of a slow ramp-in.
            luxBuffer.fill(latestLux)
            bufferFilled = MAX_WINDOW
            bufferHead = 0
        } else {
            luxBuffer[bufferHead] = latestLux
            bufferHead = (bufferHead + 1) % MAX_WINDOW
        }
        medianLux = median(windowSize)
        val target = luxToBrightness(medianLux)
        if (target != lastWritten) {
            writeBrightness(target)
            lastWritten = target
        }
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
        return (MIN_BRIGHTNESS + fraction.pow(GAMMA) * (max - MIN_BRIGHTNESS))
            .toInt().coerceIn(MIN_BRIGHTNESS, max)
    }

    private fun median(count: Int): Float {
        val n = count.coerceAtMost(MAX_WINDOW)
        val samples = FloatArray(n) { i ->
            luxBuffer[(bufferHead - 1 - i + MAX_WINDOW) % MAX_WINDOW]
        }
        samples.sort()
        return if (n % 2 == 1) samples[n / 2]
               else (samples[n / 2 - 1] + samples[n / 2]) / 2f
    }

    private fun writeBrightness(value: Int) {
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
    }
}
