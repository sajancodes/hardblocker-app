package dev.sajancodes.hardblock.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sajancodes.hardblock.data.datastore.OnboardingPreferences
import dev.sajancodes.hardblock.data.repository.InstalledApp
import dev.sajancodes.hardblock.data.repository.InstalledAppsRepository
import dev.sajancodes.hardblock.usage.UsageSnapshot
import dev.sajancodes.hardblock.usage.UsageTracker
import dev.sajancodes.hardblock.data.repository.PolicyRepository
import dev.sajancodes.hardblock.data.database.RelaxationRequestEntity
import dev.sajancodes.hardblock.domain.policy.RestrictionPolicy
import dev.sajancodes.hardblock.enforcement.*
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardState(
    val loading: Boolean = true,
    val accessGranted: Boolean = false,
    val acknowledged: Boolean = false,
    val apps: List<InstalledApp> = emptyList(),
    val snapshot: UsageSnapshot? = null,
    val error: String? = null,
    val policies: List<RestrictionPolicy> = emptyList(),
    val requests: List<RelaxationRequestEntity> = emptyList(),
    val protectedPackages: Set<String> = emptySet(),
    val protection: ProtectionStatus = ProtectionStatus(),
    val now: Instant = Instant.now(),
    val message: String? = null,
    val saving: Boolean = false,
    val policyUsage: Map<String, Map<String, Long>> = emptyMap(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val installedApps: InstalledAppsRepository,
    private val usage: UsageTracker,
    private val preferences: OnboardingPreferences,
    private val policies: PolicyRepository,
    private val coordinator: EnforcementCoordinator,
    private val safety: ProtectedPackagesResolver,
    private val updater: dev.sajancodes.hardblock.update.GitHubUpdater,
) : ViewModel() {
    val updates = updater.state
    private var updateJob: kotlinx.coroutines.Job? = null
    fun checkUpdates() { if (updateJob?.isActive != true) updateJob = viewModelScope.launch { updater.check() } }
    fun downloadUpdate() { if (updateJob?.isActive != true) updateJob = viewModelScope.launch { updater.download() } }
    fun installUpdate(open: (android.content.Intent) -> Unit) {
        if (updateJob?.isActive != true) updateJob = viewModelScope.launch { updater.installationIntent()?.let(open) }
    }
    private val mutable = MutableStateFlow(DashboardState())
    val state = mutable.asStateFlow()
    init {
        viewModelScope.launch { policies.policies.catch { error -> mutable.update { it.copy(error = "Policy persistence requires recovery: ${error.message}") } }.collect { values -> mutable.update { it.copy(policies = values) } } }
        viewModelScope.launch { policies.requests.collect { values -> mutable.update { it.copy(requests = values) } } }
        viewModelScope.launch { coordinator.status.collect { status -> mutable.update { it.copy(protection = status) } } }
        viewModelScope.launch {
            preferences.hasAcknowledged.catch { emit(false) }.collect { acknowledged -> mutable.update { it.copy(acknowledged = acknowledged) } }
        }
    }
    fun acknowledge() { viewModelScope.launch { preferences.acknowledge() } }
    fun refresh() {
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            try {
                val access = usage.hasAccess()
                mutable.update { it.copy(accessGranted = access, error = null, snapshot = if (access) it.snapshot else null) }
                val apps = installedApps.list()
                mutable.update { it.copy(apps = apps) }
                val snapshot = if (access) usage.snapshot() else null
                val protected = safety.resolve()
                val now = policies.now()
                coordinator.reconcile()
                val policyUsage = if (access) policies.all().filter { it.dailyLimit != null }.associate { p ->
                    p.id to usage.snapshot(java.time.ZoneId.of(p.zoneId), java.time.LocalTime.ofSecondOfDay(p.resetMinute * 60L)).usedMillis
                } else emptyMap()
                mutable.update { it.copy(loading = false, snapshot = snapshot, protectedPackages = protected, now = now, policyUsage = policyUsage) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutable.update { it.copy(loading = false, accessGranted = usage.hasAccess(), error = error.message ?: "Could not load local usage.") }
            } finally { refreshing = false }
        }
    }
    private var refreshing = false

    fun save(proposed: RestrictionPolicy, current: RestrictionPolicy? = null, relaxation: Boolean = false) = action {
        if (current == null) policies.activate(proposed)
        else if (relaxation) policies.requestRelaxation(proposed, current)
        else policies.edit(proposed, current)
    }
    fun cancelRequest(id: String) = action { policies.cancelRequest(id) }
    fun acceptRequest(id: String) = action { policies.acceptRequest(id) }
    fun protect(pkg: String) = action { policies.protect(pkg) }
    fun clearMessage() { mutable.update { it.copy(message = null) } }
    private fun action(operation: suspend () -> Unit) {
        if (mutable.value.saving) return
        mutable.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            try {
                operation()
                coordinator.reconcile()
                ProtectionBridge.service?.evaluate()
                mutable.update { it.copy(message = "Saved. Protection recalculated.", now = policies.now()) }
                refresh()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.update { it.copy(message = error.message ?: "Change failed") } }
            finally { mutable.update { it.copy(saving = false) } }
        }
    }
}
