package dev.sajancodes.hardblock.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import dev.sajancodes.hardblock.data.database.HardBlockDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UsageDatabaseTest {
    @Test fun usageSurvivesReopenAndCannotDecrease() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "usage-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, HardBlockDatabase::class.java, name).build()
        var db = open()
        try {
            db.usageDao().mergeDaily("example", 0, 1000, 500, 600)
            db.usageDao().mergeInterval("example", 100, 600)
            db.close()
            db = open()
            db.usageDao().mergeDaily("example", 0, 1000, 200, 700)
            db.usageDao().mergeInterval("example", 100, 400)
            assertEquals(500L, db.usageDao().daily(0, 1000).single().usedMillis)
            assertEquals(600L, db.usageDao().intervals(0, 1000).single().end)
            db.usageDao().mergeInterval("example", 100, 800)
            assertEquals(800L, db.usageDao().intervals(0, 1000).single().end)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun interruptedTransactionDoesNotPartiallyPersist() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, HardBlockDatabase::class.java).build()
        try {
            runCatching {
                db.withTransaction {
                    db.usageDao().mergeDaily("example", 0, 1000, 500, 600)
                    error("Interrupted refresh")
                }
            }
            assertTrue(db.usageDao().daily(0, 1000).isEmpty())
        } finally { db.close() }
    }
}
