package dev.sajancodes.hardblock.data

import android.content.Intent
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.sajancodes.hardblock.data.database.HardBlockDatabase
import dev.sajancodes.hardblock.data.repository.InstalledAppsRepository
import dev.sajancodes.hardblock.usage.UsageStatsTracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock

class UsageAdapterTest {
    @Test fun actualUsagePermissionCanBeGrantedMeasuredAndRevoked() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun shell(command: String) {
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
        val db = Room.inMemoryDatabaseBuilder(context, HardBlockDatabase::class.java).build()
        val tracker = UsageStatsTracker(context, db, Clock.systemUTC())
        try {
            shell("appops set ${context.packageName} GET_USAGE_STATS allow")
            assertTrue(tracker.hasAccess())
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            var measured = false
            repeat(10) {
                if (!measured) {
                    delay(500)
                    measured = (tracker.snapshot().usedMillis["com.android.settings"] ?: 0L) > 0L
                }
            }
            assertTrue("Android Settings foreground usage should be observed", measured)
            shell("appops set ${context.packageName} GET_USAGE_STATS ignore")
            assertFalse(tracker.hasAccess())
            assertTrue(runCatching { tracker.snapshot() }.exceptionOrNull() is IllegalStateException)
        } finally {
            shell("appops set ${context.packageName} GET_USAGE_STATS default")
            db.close()
        }
    }

    @Test fun installedAppDiscoveryReturnsDistinctLaunchableApps() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, HardBlockDatabase::class.java).build()
        try {
            val apps = InstalledAppsRepository(context, db).list()
            assertTrue(apps.isNotEmpty())
            assertEquals(apps.size, apps.map { it.packageName }.toSet().size)
            assertFalse(apps.any { it.packageName == context.packageName })
            assertTrue(apps.all { it.label.isNotBlank() && it.packageName.isNotBlank() })
        } finally { db.close() }
    }
}
