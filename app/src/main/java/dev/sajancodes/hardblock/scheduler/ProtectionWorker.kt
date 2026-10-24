package dev.sajancodes.hardblock.scheduler

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.sajancodes.hardblock.MainActivity
import dev.sajancodes.hardblock.R
import dev.sajancodes.hardblock.data.database.*
import dev.sajancodes.hardblock.enforcement.EnforcementCoordinator
import dev.sajancodes.hardblock.domain.policy.EnforcementState
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import dev.sajancodes.hardblock.data.repository.TimeIntegrityManager

@EntryPoint @InstallIn(SingletonComponent::class)
interface WorkerDependencies {
    fun coordinator(): EnforcementCoordinator
    fun database(): HardBlockDatabase
    fun time(): TimeIntegrityManager
}

class ProtectionWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, WorkerDependencies::class.java)
        val status = dependencies.coordinator().reconcile()
        val dao = dependencies.database().policyDao()
        if (status.state == EnforcementState.INTERRUPTED) {
            if (dao.local("interruption-notified")?.value != status.detail) {
                if (notify(applicationContext, 1, "Protection interrupted", status.detail)) dao.local(LocalStateEntity("interruption-notified", status.detail))
            }
        } else dao.local(LocalStateEntity("interruption-notified", ""))
        val now = dependencies.time().now().toEpochMilli()
        dao.requests().first().filter { it.status == "PENDING" && it.availableAt <= now }.forEach { request ->
            if (dao.local("notified-${request.id}") == null && notify(applicationContext, request.id.hashCode(), "Relaxation request available", "Open HardBlock to review and explicitly confirm. Your restriction remains unchanged."))
                dao.local(LocalStateEntity("notified-${request.id}", "true"))
        }
        return Result.success()
    }
    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("protection-watchdog", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ProtectionWorker>(15, TimeUnit.MINUTES).build())
        }
        fun refresh(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("protection-refresh", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ProtectionWorker>().build())
        }
        fun notify(context: Context, id: Int, title: String, message: String): Boolean {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33) return false
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("protection", "Protection and commitments", NotificationManager.IMPORTANCE_DEFAULT))
            val pending = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            manager.notify(id, NotificationCompat.Builder(context, "protection").setSmallIcon(R.drawable.ic_hardblock)
                .setContentTitle(title).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(pending).setAutoCancel(true).build())
            return true
        }
    }
}

class RecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_PACKAGE_REPLACED)) ProtectionWorker.refresh(context)
    }
}
