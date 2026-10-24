package dev.sajancodes.hardblock.data.repository

import android.content.Context
import android.content.Intent
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sajancodes.hardblock.data.database.AppInfoEntity
import dev.sajancodes.hardblock.data.database.HardBlockDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledApp(val packageName: String, val label: String)

@Singleton
class InstalledAppsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: HardBlockDatabase,
) {
    @Suppress("DEPRECATION")
    suspend fun list(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        val now = System.currentTimeMillis()
        database.withTransaction {
            database.usageDao().saveApps(apps.map { AppInfoEntity(it.packageName, it.label, now) })
            database.usageDao().pruneApps(now)
        }
        apps
    }
}
