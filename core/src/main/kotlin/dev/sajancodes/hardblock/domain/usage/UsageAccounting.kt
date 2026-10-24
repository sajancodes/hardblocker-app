package dev.sajancodes.hardblock.domain.usage

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

data class UsageWindow(val start: Long, val end: Long) {
    companion object {
        fun containing(now: Instant, zone: ZoneId, reset: LocalTime = LocalTime.MIDNIGHT): UsageWindow {
            val local = now.atZone(zone)
            var date = local.toLocalDate()
            if (now < date.atTime(reset).atZone(zone).toInstant()) date = date.minusDays(1)
            return UsageWindow(date.atTime(reset).atZone(zone).toInstant().toEpochMilli(),
                date.plusDays(1).atTime(reset).atZone(zone).toInstant().toEpochMilli())
        }
    }
}

enum class UsageEventType { RESUME, PAUSE, STOP_ALL }
data class ForegroundEvent(val at: Long, val type: UsageEventType, val packageName: String = "", val activity: String = "")
data class UsageInterval(val packageName: String, val start: Long, val end: Long)
data class UsageReplay(val intervals: List<UsageInterval>, val foregroundPackage: String?)

/** Single top-resumed activity model. Screen-off, keyguard and shutdown close a session.
 * This intentionally does not pretend to measure attention or split-screen interaction.
 */
object UsageAccounting {
    fun replay(events: List<ForegroundEvent>, until: Long): UsageReplay {
        val intervals = mutableListOf<UsageInterval>()
        var active: ForegroundEvent? = null
        fun close(at: Long) {
            active?.let { if (at > it.at) intervals += UsageInterval(it.packageName, it.at, at) }
            active = null
        }
        events.asSequence().filter { it.at <= until }.distinct().sortedBy { it.at }.forEach { event ->
            when (event.type) {
                UsageEventType.RESUME -> {
                    if (active?.packageName != event.packageName || active?.activity != event.activity) {
                        close(event.at)
                        if (event.packageName.isNotBlank()) active = event
                    }
                }
                UsageEventType.PAUSE -> if (active?.packageName == event.packageName && active?.activity == event.activity) close(event.at)
                UsageEventType.STOP_ALL -> close(event.at)
            }
        }
        val foreground = active?.packageName
        close(until)
        return UsageReplay(intervals, foreground)
    }

    /** Union persisted and replayed intervals, never sum overlapping snapshots. */
    fun totals(intervals: List<UsageInterval>, window: UsageWindow, until: Long): Map<String, Long> =
        intervals.groupBy { it.packageName }.mapValues { (_, entries) ->
            var total = 0L
            var end = window.start
            entries.sortedBy { it.start }.forEach { interval ->
                val left = maxOf(window.start, interval.start, end)
                val right = minOf(window.end, until, interval.end)
                if (right > left) { total += right - left; end = right }
            }
            total
        }
}
