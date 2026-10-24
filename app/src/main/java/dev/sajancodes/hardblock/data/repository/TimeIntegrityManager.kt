package dev.sajancodes.hardblock.data.repository

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sajancodes.hardblock.data.database.*
import dev.sajancodes.hardblock.domain.time.*
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeIntegrityManager @Inject constructor(@param:ApplicationContext private val context: Context, private val db: HardBlockDatabase) {
    @Volatile var suspicious: Boolean = false; private set
    suspend fun now(): Instant = db.withTransaction {
        val previous = db.policyDao().local("clock")?.value?.split(',')?.let {
            require(it.size == 4) { "Clock checkpoint is corrupt; recovery required" }
            TimeCheckpoint(it[0].toLong(), it[1].toLong(), it[2].toLong(), it[3].toInt())
        }
        val reading = TimeIntegrity.read(previous, System.currentTimeMillis(), SystemClock.elapsedRealtime(),
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0))
        suspicious = reading.suspicious
        val c = reading.checkpoint
        db.policyDao().local(LocalStateEntity("clock", "${c.trustedMillis},${c.wallMillis},${c.elapsedMillis},${c.boot}"))
        Instant.ofEpochMilli(c.trustedMillis)
    }
}
