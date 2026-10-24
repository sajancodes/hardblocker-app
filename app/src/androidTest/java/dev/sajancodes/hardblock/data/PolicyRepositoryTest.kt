package dev.sajancodes.hardblock.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.sajancodes.hardblock.data.database.*
import dev.sajancodes.hardblock.data.repository.*
import dev.sajancodes.hardblock.domain.policy.*
import dev.sajancodes.hardblock.enforcement.ProtectedPackagesResolver
import dev.sajancodes.hardblock.usage.UsageStatsTracker
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class PolicyRepositoryTest {
    @Test fun requestDoesNotWeakenAndCancellationKeepsOriginal() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, HardBlockDatabase::class.java).build()
        try {
            val time = TimeIntegrityManager(context, db)
            val repo = PolicyRepository(db, ProtectedPackagesResolver(context, db), time, UsageStatsTracker(context, db, Clock.systemUTC()))
            val now = repo.now()
            val original = RestrictionPolicy("policy", setOf("example.test"), now.minusSeconds(10), now.minusSeconds(5),
                now.plus(Duration.ofDays(7)), PolicyState.ACTIVE, Duration.ofMinutes(30), zoneId = "UTC")
            val encoded = PolicyCodec.encode(original)
            db.policyDao().save(PolicyRecord(original.id, encoded, encoded, 0))
            repo.requestRelaxation(original.copy(dailyLimit = Duration.ofMinutes(45)), original)
            val request = repo.requests.first().single()
            assertEquals(original.expiresAt!!.toEpochMilli(), request.availableAt)
            assertEquals(original, repo.all().single())
            assertTrue(runCatching { repo.acceptRequest(request.id) }.isFailure)
            repo.cancelRequest(request.id)
            assertEquals("CANCELLED", repo.requests.first().single().status)
            assertEquals(original, repo.all().single())
            assertTrue(runCatching { repo.acceptRequest(request.id) }.isFailure)
        } finally { db.close() }
    }

    @Test fun explicitEligibleAcceptancePreservesOriginalAndRejectsReplay() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, HardBlockDatabase::class.java).build()
        try {
            val time = TimeIntegrityManager(context, db)
            val repo = PolicyRepository(db, ProtectedPackagesResolver(context, db), time, UsageStatsTracker(context, db, Clock.systemUTC()))
            val now = repo.now()
            val original = RestrictionPolicy("policy", setOf("example.test"), now.minus(Duration.ofDays(5)), now.minus(Duration.ofDays(5)),
                null, PolicyState.ACTIVE, Duration.ofMinutes(30), zoneId = "UTC")
            val encoded = PolicyCodec.encode(original)
            db.policyDao().save(PolicyRecord(original.id, encoded, encoded, 0))
            val proposed = original.copy(dailyLimit = Duration.ofMinutes(45))
            db.policyDao().saveRequest(RelaxationRequestEntity("request", original.id, 0, PolicyCodec.encode(proposed),
                now.minus(Duration.ofDays(4)).toEpochMilli(), now.minus(Duration.ofDays(1)).toEpochMilli(), "PENDING"))
            repo.acceptRequest("request")
            assertEquals(proposed, repo.all().single())
            assertEquals(encoded, db.policyDao().find(original.id)!!.original)
            assertTrue(runCatching { repo.acceptRequest("request") }.isFailure)
            assertEquals("RELAXATION_ACCEPTED", repo.history().first().event)
        } finally { db.close() }
    }

    @Test fun essentialsRejectedAndCodecFailsOnCorruptConfiguration() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, HardBlockDatabase::class.java).build()
        try {
            val safety = ProtectedPackagesResolver(context, db)
            assertTrue(runCatching { safety.validate(setOf("com.android.settings")) }.isFailure)
            assertTrue(runCatching { safety.validate(setOf(context.packageName)) }.isFailure)
            assertTrue(runCatching { PolicyCodec.decode("corrupted") }.isFailure)
        } finally { db.close() }
    }
}
