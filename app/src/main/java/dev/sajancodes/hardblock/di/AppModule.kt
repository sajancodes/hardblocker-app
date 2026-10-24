package dev.sajancodes.hardblock.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.sajancodes.hardblock.data.database.HardBlockDatabase
import dev.sajancodes.hardblock.usage.UsageStatsTracker
import dev.sajancodes.hardblock.usage.UsageTracker
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): HardBlockDatabase =
        Room.databaseBuilder(context, HardBlockDatabase::class.java, "hardblock.db").build()
    @Provides fun clock(): Clock = Clock.systemUTC()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UsageModule {
    @Binds abstract fun tracker(implementation: UsageStatsTracker): UsageTracker
}
