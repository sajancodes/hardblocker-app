package dev.sajancodes.hardblock.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.Build
import android.os.UserManager
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sajancodes.hardblock.data.database.HardBlockDatabase
import dev.sajancodes.hardblock.domain.usage.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageStatsTracker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: HardBlockDatabase,
    private val clock: Clock,
) : UsageTracker {
    private val mutex = Mutex()
    @Suppress("DEPRECATION")
    override fun hasAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 36) {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override suspend fun snapshot(zone: ZoneId, reset: LocalTime): UsageSnapshot = withContext(Dispatchers.IO) { mutex.withLock {
        check(hasAccess()) { "Usage Access is disabled. Restore it in Android Settings." }
        check(context.getSystemService(UserManager::class.java).isUserUnlocked) { "Unlock your device to read usage." }
        val now = clock.instant()
        val window = UsageWindow.containing(now, zone, reset)
        val until = now.toEpochMilli()
        val manager = context.getSystemService(UsageStatsManager::class.java)
        // Seed an activity opened before midnight. Android retains events only for a limited time.
        val source = manager.queryEvents(window.start - Duration.ofDays(2).toMillis(), until)
            ?: error("Android usage history is temporarily unavailable. Try refreshing.")
        val events = mutableListOf<ForegroundEvent>()
        val event = UsageEvents.Event()
        while (source.hasNextEvent()) {
            source.getNextEvent(event)
            val type = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventType.RESUME
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> UsageEventType.PAUSE
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN,
                UsageEvents.Event.DEVICE_SHUTDOWN, UsageEvents.Event.DEVICE_STARTUP -> UsageEventType.STOP_ALL
                else -> null
            }
            if (type != null) events += ForegroundEvent(event.timeStamp, type, event.packageName.orEmpty(), event.className.orEmpty())
        }
        val replay = UsageAccounting.replay(events, until)
        database.withTransaction {
            val dao = database.usageDao()
            replay.intervals.filter { it.end > window.start }.forEach { dao.mergeInterval(it.packageName, it.start, it.end) }
            val persisted = dao.intervals(window.start, until).map { UsageInterval(it.packageName, it.start, it.end) }
            val totals = UsageAccounting.totals(persisted, window, until)
            totals.forEach { (pkg, used) -> dao.mergeDaily(pkg, window.start, window.end, used, until) }
            val daily = dao.daily(window.start, window.end).associate { it.packageName to it.usedMillis }
            val retention = window.start - Duration.ofDays(8).toMillis()
            dao.pruneIntervals(retention)
            dao.pruneDaily(retention)
            UsageSnapshot(window, daily, replay.foregroundPackage, until)
        }
    } }
}
