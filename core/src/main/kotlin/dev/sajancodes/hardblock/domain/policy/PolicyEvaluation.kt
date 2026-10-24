package dev.sajancodes.hardblock.domain.policy

import dev.sajancodes.hardblock.domain.usage.UsageWindow
import java.time.*

data class BlockReason(val message: String, val availableAt: Instant?, val usedMillis: Long = 0, val limitMillis: Long? = null)

object PolicyEvaluation {
    fun active(policy: RestrictionPolicy, now: Instant): Boolean =
        policy.activatedAt != null && policy.activatedAt <= now &&
            (policy.expiresAt == null || now < policy.expiresAt) && policy.state != PolicyState.DISABLED

    fun reason(policy: RestrictionPolicy, pkg: String, usedMillis: Long, now: Instant): BlockReason? {
        if (pkg !in policy.packages || !active(policy, now)) return null
        if (policy.alwaysBlocked) return BlockReason("Committed block", policy.expiresAt)
        policy.blockedUntil?.let { if (now < it) return BlockReason("Focus session active", minEnd(it, policy.expiresAt)) }
        val zone = ZoneId.of(policy.zoneId)
        val local = now.atZone(zone)
        val minute = local.hour * 60 + local.minute
        val day = local.dayOfWeek.value - 1
        val scheduled = policy.schedule.any { w ->
            if (w.endMinute > w.startMinute) day == w.day && minute in w.startMinute until w.endMinute
            else (day == w.day && minute >= w.startMinute) || (day == (w.day + 1) % 7 && minute < w.endMinute)
        }
        if (scheduled) {
            // Find the end of the union, including overlapping and adjacent windows, across DST.
            var end = now.truncatedTo(java.time.temporal.ChronoUnit.MINUTES).plusSeconds(60)
            repeat(7 * 1440 + 1) {
                val t = end.atZone(zone)
                val d = t.dayOfWeek.value - 1
                val m = t.hour * 60 + t.minute
                val still = policy.schedule.any { w -> if (w.endMinute > w.startMinute) d == w.day && m in w.startMinute until w.endMinute else (d == w.day && m >= w.startMinute) || (d == (w.day + 1) % 7 && m < w.endMinute) }
                if (!still) return BlockReason("Scheduled lock", minEnd(end, policy.expiresAt))
                end = end.plusSeconds(60)
            }
            return BlockReason("Scheduled lock", policy.expiresAt)
        }
        policy.dailyLimit?.toMillis()?.let { limit ->
            if (usedMillis >= limit) {
                val window = UsageWindow.containing(now, zone, LocalTime.ofSecondOfDay(policy.resetMinute * 60L))
                return BlockReason("Daily limit reached", minEnd(Instant.ofEpochMilli(window.end), policy.expiresAt), usedMillis, limit)
            }
        }
        return null
    }
    private fun minEnd(a: Instant, b: Instant?) = if (b != null && b < a) b else a
}
