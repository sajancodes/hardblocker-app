package dev.sajancodes.hardblock.data.database

import androidx.room.*

@Entity(tableName = "apps")
data class AppInfoEntity(@PrimaryKey val packageName: String, val label: String, val lastSeenAt: Long)

// Start remains stable while a foreground session grows. Upsert keeps its longest observed end.
@Entity(tableName = "usage_intervals", primaryKeys = ["packageName", "start"])
data class UsageIntervalEntity(val packageName: String, val start: Long, val end: Long)

@Entity(tableName = "daily_usage", primaryKeys = ["packageName", "windowStart", "windowEnd"])
data class DailyUsageEntity(val packageName: String, val windowStart: Long, val windowEnd: Long, val usedMillis: Long, val updatedAt: Long)

@Dao
interface UsageDao {
    @Upsert suspend fun saveApps(apps: List<AppInfoEntity>)
    @Query("SELECT * FROM usage_intervals WHERE `end` > :start AND start < :end")
    suspend fun intervals(start: Long, end: Long): List<UsageIntervalEntity>
    @Query("INSERT INTO usage_intervals(packageName, start, `end`) VALUES(:packageName, :start, :end) ON CONFLICT(packageName, start) DO UPDATE SET `end` = MAX(`end`, excluded.`end`)")
    suspend fun mergeInterval(packageName: String, start: Long, end: Long)
    @Query("SELECT * FROM daily_usage WHERE windowStart = :start AND windowEnd = :end")
    suspend fun daily(start: Long, end: Long): List<DailyUsageEntity>
    @Query("INSERT INTO daily_usage(packageName, windowStart, windowEnd, usedMillis, updatedAt) VALUES(:packageName, :start, :end, :used, :now) ON CONFLICT(packageName, windowStart, windowEnd) DO UPDATE SET usedMillis = MAX(usedMillis, excluded.usedMillis), updatedAt = excluded.updatedAt")
    suspend fun mergeDaily(packageName: String, start: Long, end: Long, used: Long, now: Long)
    @Query("DELETE FROM usage_intervals WHERE `end` < :before") suspend fun pruneIntervals(before: Long)
    @Query("DELETE FROM daily_usage WHERE windowEnd < :before") suspend fun pruneDaily(before: Long)
    @Query("DELETE FROM apps WHERE lastSeenAt < :now") suspend fun pruneApps(now: Long)
}

@Database(entities = [AppInfoEntity::class, UsageIntervalEntity::class, DailyUsageEntity::class,
    PolicyRecord::class, PolicyHistoryEntity::class, RelaxationRequestEntity::class, LocalStateEntity::class, AppExceptionEntity::class],
    version = 2, exportSchema = true, autoMigrations = [AutoMigration(from = 1, to = 2)])
abstract class HardBlockDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun policyDao(): PolicyDao
}
