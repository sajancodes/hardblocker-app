package dev.sajancodes.hardblock.domain.time

data class TimeCheckpoint(val trustedMillis: Long, val wallMillis: Long, val elapsedMillis: Long, val boot: Int)
data class TimeReading(val checkpoint: TimeCheckpoint, val suspicious: Boolean)

/** Offline protection, not a trusted network clock. Same-boot deadlines advance by elapsed time.
 * Across boots, retain a backwards floor and accept wall time so recovery never requires a server.
 */
object TimeIntegrity {
    fun read(previous: TimeCheckpoint?, wall: Long, elapsed: Long, boot: Int): TimeReading {
        if (previous == null) return TimeReading(TimeCheckpoint(wall, wall, elapsed, boot), false)
        val sameBoot = previous.boot == boot && elapsed >= previous.elapsedMillis
        val expected = if (sameBoot) previous.trustedMillis + elapsed - previous.elapsedMillis else maxOf(wall, previous.trustedMillis)
        val suspicious = if (sameBoot) kotlin.math.abs(wall - expected) > 120_000 else wall < previous.trustedMillis - 120_000
        return TimeReading(TimeCheckpoint(expected, wall, elapsed, boot), suspicious)
    }
}
