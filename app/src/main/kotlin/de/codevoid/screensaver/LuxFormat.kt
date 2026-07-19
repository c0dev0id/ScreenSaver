package de.codevoid.screensaver

import kotlin.math.roundToInt

// Lux spans orders of magnitude; keep the display compact at every scale.
fun formatLux(lux: Float): String = when {
    lux >= 10_000f -> "${(lux / 1_000f).roundToInt()}k lx"
    lux >= 1_000f -> "%.1fk lx".format(lux / 1_000f)
    lux >= 10f -> "${lux.roundToInt()} lx"
    else -> "%.1f lx".format(lux)
}
