package com.frost.dungeon

/** When Key Pickup's notification shows and fades, and when a pickup is the same one again. */
object KeyPickupTiming {
    /** Hypixel can announce one pickup twice ("has obtained" and "was picked up"), so a second
     * announcement of the same key this soon is taken to be the same pickup. */
    const val SAME_PICKUP_MILLIS = 1500L

    /** The notification fades out over its last this long. */
    const val FADE_MILLIS = 300L

    fun isSamePickup(lastMillis: Long?, nowMillis: Long): Boolean =
        lastMillis != null && nowMillis - lastMillis in 0 until SAME_PICKUP_MILLIS

    /** How opaque the notification is [elapsedMillis] after it appeared, 0 once it's gone. */
    fun opacity(elapsedMillis: Long, durationMillis: Long): Float {
        if (elapsedMillis < 0 || elapsedMillis >= durationMillis) return 0f
        val left = durationMillis - elapsedMillis
        val fade = minOf(FADE_MILLIS, durationMillis)
        return if (left >= fade) 1f else left.toFloat() / fade
    }

    /** [argb] at [opacity] times its own alpha. */
    fun withOpacity(argb: Int, opacity: Float): Int {
        val alpha = ((argb ushr 24) * opacity.coerceIn(0f, 1f)).toInt()
        return (alpha shl 24) or (argb and 0xFFFFFF)
    }
}
