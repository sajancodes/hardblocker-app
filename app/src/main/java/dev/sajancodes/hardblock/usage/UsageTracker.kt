package dev.sajancodes.hardblock.usage

import dev.sajancodes.hardblock.domain.usage.UsageWindow
import java.time.ZoneId
import java.time.LocalTime

data class UsageSnapshot(
    val window: UsageWindow,
    val usedMillis: Map<String, Long>,
    val foregroundPackage: String?,
    val measuredAt: Long,
)

interface UsageTracker {
    fun hasAccess(): Boolean
    suspend fun snapshot(zone: ZoneId = ZoneId.systemDefault(), reset: LocalTime = LocalTime.MIDNIGHT): UsageSnapshot
}
