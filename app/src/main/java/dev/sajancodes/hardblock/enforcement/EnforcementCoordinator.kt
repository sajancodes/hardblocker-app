package dev.sajancodes.hardblock.enforcement

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sajancodes.hardblock.data.database.*
import dev.sajancodes.hardblock.data.repository.*
import dev.sajancodes.hardblock.domain.policy.*
import dev.sajancodes.hardblock.usage.UsageTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.time.*
import javax.inject.Inject
import javax.inject.Singleton
import dev.sajancodes.hardblock.scheduler.ProtectionWorker

data class ProtectionStatus(val mode: EnforcementMode = EnforcementMode.STANDARD, val state: EnforcementState = EnforcementState.PENDING,
    val detail: String = "Checking protection", val foreground: String? = null, val nextCheckMillis: Long = 30_000)

@Singleton
class EnforcementCoordinator @Inject constructor(
    private val repository: PolicyRepository, private val usage: UsageTracker, private val safety: ProtectedPackagesResolver,
    private val standard: StandardEnforcer, private val owner: DeviceOwnerEnforcer, private val db: HardBlockDatabase,
    private val time: TimeIntegrityManager, @param:ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(ProtectionStatus())
    val status = mutable.asStateFlow()
    suspend fun reconcile(foreground: String? = null): ProtectionStatus = mutex.withLock {
        val mode = if (owner.isOwner()) EnforcementMode.DEVICE_OWNER else EnforcementMode.STANDARD
        try {
            val now = repository.now()
            val policies = repository.all()
            val protected = safety.resolve()
            val active = policies.filter { PolicyEvaluation.active(it, now) }
            val access = usage.hasAccess()
            val connected = ProtectionBridge.service != null
            var next = if (active.isEmpty()) 86_400_000L else 30_000L
            val desired = mutableMapOf<String, BlockReason>()
            val snapshots = mutableMapOf<Pair<String, Int>, Map<String, Long>>()
            active.forEach { p ->
                val limit = p.dailyLimit
                val used = if (p.dailyLimit != null && access) snapshots.getOrPut(p.zoneId to p.resetMinute) {
                    usage.snapshot(ZoneId.of(p.zoneId), LocalTime.ofSecondOfDay(p.resetMinute * 60L)).usedMillis
                } else emptyMap()
                p.packages.filterNot { it in protected }.forEach { pkg ->
                    // When Usage Access disappears, retain strict protection instead of silently granting a new allowance.
                    val reason = if (p.dailyLimit != null && time.suspicious) BlockReason("Clock change detected; restore automatic date and time", null)
                        else if (p.dailyLimit != null && !access) BlockReason("Usage Access interrupted; restore protection in HardBlock", null)
                        else PolicyEvaluation.reason(p, pkg, used[pkg] ?: 0L, now)
                    if (reason != null) desired.putIfAbsent(pkg, reason)
                    else if (pkg == foreground && limit != null) next = minOf(next, (limit.toMillis() - (used[pkg] ?: 0L)).coerceAtLeast(500))
                    if (pkg == foreground && limit != null && access && !time.suspicious) {
                        val remaining = limit.toMillis() - (used[pkg] ?: 0L)
                        val window = dev.sajancodes.hardblock.domain.usage.UsageWindow.containing(now, ZoneId.of(p.zoneId), LocalTime.ofSecondOfDay(p.resetMinute * 60L))
                        val threshold = if (remaining <= 0) 0 else p.warningMinutes.sorted().firstOrNull { remaining <= it * 60_000L }
                        if (threshold != null) {
                            val key = "warning:${p.id}:$pkg:${window.start}:$threshold"
                            if (db.policyDao().local(key) == null) {
                                val label = runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault("Application")
                                if (ProtectionWorker.notify(context, key.hashCode(), label, if (threshold == 0) "Daily limit reached" else "$threshold minute(s) remaining"))
                                    db.policyDao().local(LocalStateEntity(key, "sent"))
                            }
                        }
                    }
                }
                p.expiresAt?.let { next = minOf(next, Duration.between(now, it).toMillis().coerceAtLeast(500)) }
            }
            if (active.isNotEmpty()) next = minOf(next, (60_000 - (now.toEpochMilli() % 60_000)).coerceAtLeast(500))
            val enforcer: AppEnforcer = if (mode == EnforcementMode.DEVICE_OWNER) owner else standard
            val ledger = db.policyDao().local("suspended")?.value?.let { value ->
                val array = JSONArray(value); (0 until array.length()).map { array.getString(it) }.toMutableSet()
            } ?: mutableSetOf()
            if (mode == EnforcementMode.DEVICE_OWNER) {
                owner.protectCommitments(active.isNotEmpty())
                // Persist intent before suspension: process death must not orphan a suspended app.
                val allManaged = ledger + desired.keys
                db.policyDao().local(LocalStateEntity("suspended", JSONArray(allManaged.sorted()).toString()))
                (ledger - desired.keys).forEach { owner.unblock(it) }
            }
            val previous = ProtectionBridge.blocks.toMap()
            ProtectionBridge.blocks.keys.filterNot { it in desired }.forEach { standard.unblock(it) }
            desired.forEach { (pkg, reason) ->
                if (mode == EnforcementMode.DEVICE_OWNER || connected) enforcer.block(pkg)
                ProtectionBridge.blocks[pkg] = reason
            }
            if (mode == EnforcementMode.DEVICE_OWNER) db.policyDao().local(LocalStateEntity("suspended", JSONArray(desired.keys.sorted()).toString()))
            val interrupted = active.isNotEmpty() && (!access || !connected)
            val detail = when {
                interrupted -> "Protection interrupted: restore Usage Access and the Accessibility service"
                time.suspicious -> "Clock change detected; deadlines use elapsed time. Check the device clock."
                active.isEmpty() -> "Ready · no active commitments"
                mode == EnforcementMode.DEVICE_OWNER -> "Device Owner · package suspension active"
                else -> "Standard Mode · can be bypassed in Android Settings"
            }
            val result = ProtectionStatus(mode, if (interrupted) EnforcementState.INTERRUPTED else EnforcementState.ENFORCED, detail, foreground, next)
            if (mutable.value.detail != result.detail) repository.enforcementEvent(if (interrupted) "ENFORCEMENT_INTERRUPTED" else "ENFORCEMENT_RESTORED", detail)
            if (previous.keys != desired.keys) repository.enforcementEvent("ENFORCEMENT_RECONCILED", "blockedCount=${desired.size}")
            mutable.value = result
            result
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) {
            mutable.value = ProtectionStatus(mode, EnforcementState.INTERRUPTED, "Protection interrupted: ${error.message}", foreground)
            mutable.value
        }
    }
}
