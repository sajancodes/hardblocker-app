package dev.sajancodes.hardblock.data

import android.content.Intent
import android.os.Build
import android.app.UiAutomation
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import dev.sajancodes.hardblock.data.repository.*
import dev.sajancodes.hardblock.domain.policy.*
import dev.sajancodes.hardblock.enforcement.*
import dev.sajancodes.hardblock.scheduler.WorkerDependencies
import dev.sajancodes.hardblock.service.ProtectionService
import dev.sajancodes.hardblock.usage.UsageStatsTracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.Clock
import java.util.UUID

class EnforcementFlowTest {
    @Test fun committedAppIsCoveredWhileSettingsRemainsAvailable() = runBlocking {
        // This test changes accessibility settings only on a disposable emulator.
        assumeTrue(Build.MODEL.contains("sdk"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val context = instrumentation.targetContext
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes().toString(Charsets.UTF_8).trim() }
        val oldServices = shell("settings get secure enabled_accessibility_services")
        val oldEnabled = shell("settings get secure accessibility_enabled")
        val dependencies = EntryPointAccessors.fromApplication(context, WorkerDependencies::class.java)
        val db = dependencies.database()
        val time = dependencies.time()
        val repo = PolicyRepository(db, ProtectedPackagesResolver(context, db), time, UsageStatsTracker(context, db, Clock.systemUTC()))
        val id = "test-${UUID.randomUUID()}"
        fun hasOverlay(): Boolean {
            var attached = false
            instrumentation.runOnMainSync {
                val service = ProtectionBridge.service
                val view = service?.let { ProtectionService::class.java.getDeclaredField("overlay").apply { isAccessible = true }.get(it) as View? }
                attached = view?.isAttachedToWindow == true
            }
            return attached
        }
        try {
            shell("appops set ${context.packageName} GET_USAGE_STATS allow")
            val component = "${context.packageName}/.service.ProtectionService"
            val services = if (oldServices == "null" || oldServices.isBlank()) component else "$oldServices:$component"
            shell("settings put secure enabled_accessibility_services $services")
            shell("settings put secure accessibility_enabled 1")
            repeat(50) { if (ProtectionBridge.service == null) delay(200) }
            assertNotNull("Accessibility protection should bind", ProtectionBridge.service)
            val now = repo.now()
            val policy = RestrictionPolicy(id, setOf("com.android.chrome"), now.minusSeconds(1), expiresAt = now.plusSeconds(120), alwaysBlocked = true, zoneId = "UTC")
            repo.activate(policy)
            dependencies.coordinator().reconcile()
            val active = repo.all().single { it.id == id }
            assertTrue(runCatching { repo.edit(active.copy(alwaysBlocked = false, dailyLimit = java.time.Duration.ofHours(1)), active) }.isFailure)
            assertEquals(active, repo.all().single { it.id == id })
            val launch = requireNotNull(context.packageManager.getLaunchIntentForPackage("com.android.chrome"))
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            repeat(50) { if (!hasOverlay()) delay(200) }
            assertTrue("Committed Chrome must be covered by the real accessibility overlay", hasOverlay())
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            repeat(30) { if (hasOverlay()) delay(200) }
            assertFalse("Essential Settings must remain accessible", hasOverlay())
            assertEquals(EnforcementMode.STANDARD, dependencies.coordinator().status.value.mode)
        } finally {
            // Remove only this synthetic fixture. Production has no policy-delete bypass API.
            db.openHelper.writableDatabase.execSQL("DELETE FROM policies WHERE id = ?", arrayOf(id))
            dependencies.coordinator().reconcile()
            if (oldServices == "null") shell("settings delete secure enabled_accessibility_services") else shell("settings put secure enabled_accessibility_services $oldServices")
            if (oldEnabled == "null") shell("settings delete secure accessibility_enabled") else shell("settings put secure accessibility_enabled $oldEnabled")
            shell("appops set ${context.packageName} GET_USAGE_STATS default")
        }
    }
}
