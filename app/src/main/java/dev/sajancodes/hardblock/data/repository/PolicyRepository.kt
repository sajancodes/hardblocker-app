package dev.sajancodes.hardblock.data.repository

import android.util.Log
import androidx.room.withTransaction
import dev.sajancodes.hardblock.data.database.*
import dev.sajancodes.hardblock.domain.policy.*
import dev.sajancodes.hardblock.enforcement.ProtectedPackagesResolver
import dev.sajancodes.hardblock.enforcement.ProtectionBridge
import dev.sajancodes.hardblock.usage.UsageTracker
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PolicyRepository @Inject constructor(private val db: HardBlockDatabase, private val safety: ProtectedPackagesResolver,
    private val time: TimeIntegrityManager, private val usage: UsageTracker) {
    private val engine = PolicyEngine()
    private val dao get() = db.policyDao()
    val policies = dao.observe().map { records -> records.map { PolicyCodec.decode(it.current) } }
    val requests = dao.requests()
    suspend fun all(): List<RestrictionPolicy> = dao.all().map { PolicyCodec.decode(it.current) }
    suspend fun history() = dao.history()
    suspend fun now() = time.now()

    suspend fun activate(draft: RestrictionPolicy) {
        require(usage.hasAccess() && ProtectionBridge.service != null) { "Restore Usage Access and Accessibility protection before activation" }
        val now = time.now()
        require(draft.activatedAt == null && draft.state == PolicyState.DRAFT) { "Expected an unactivated draft" }
        val policy = draft.copy(activatedAt = now, state = PolicyState.ACTIVE)
        val decision = engine.validate(policy)
        require(decision.allowed) { decision.reason }
        require(policy.expiresAt?.let { it > now } != false) { "Commitment must end in the future" }
        safety.validate(policy.packages, requireLaunchable = true)
        db.withTransaction {
            require(dao.find(policy.id) == null) { "Policy already exists" }
            val encoded = PolicyCodec.encode(policy)
            dao.save(PolicyRecord(policy.id, encoded, encoded, 0))
            record(policy.id, null, encoded, "POLICY_ACTIVATED", decision.reason, now)
        }
    }

    suspend fun edit(proposed: RestrictionPolicy, expected: RestrictionPolicy) {
        val now = time.now()
        if (PolicyEvaluation.active(proposed, now)) require(usage.hasAccess() && ProtectionBridge.service != null) { "Restore protection before changing active commitments" }
        safety.validate(proposed.packages)
        safety.validate(proposed.packages - expected.packages, requireLaunchable = true)
        val result = db.withTransaction {
            val row = requireNotNull(dao.find(proposed.id)) { "Policy unavailable" }
            val current = PolicyCodec.decode(row.current)
            require(current == expected) { "Policy changed; reopen it before editing" }
            val decision = engine.canApplyChange(current, proposed, now)
            val encoded = PolicyCodec.encode(proposed)
            record(row.id, row.current, encoded, if (decision.allowed) "POLICY_STRICTENED" else "POLICY_WEAKEN_ATTEMPTED", decision.reason, now)
            if (decision.allowed) {
                dao.save(row.copy(current = encoded, revision = row.revision + 1))
                dao.invalidateRequests(row.id)
            }
            decision
        }
        require(result.allowed) { "${result.reason}. Request delayed relaxation instead." }
    }

    suspend fun requestRelaxation(proposed: RestrictionPolicy, expected: RestrictionPolicy) {
        val now = time.now()
        require(engine.validate(proposed).allowed) { "Requested configuration is invalid" }
        safety.validate(proposed.packages)
        db.withTransaction {
            val row = requireNotNull(dao.find(proposed.id))
            val current = PolicyCodec.decode(row.current)
            require(current == expected) { "Policy changed; reopen it before requesting" }
            require(current.id == proposed.id && current.createdAt == proposed.createdAt && current.activatedAt == proposed.activatedAt)
            require(proposed.relaxationCooldown >= current.relaxationCooldown) { "Cooldown cannot be shortened" }
            val available = maxOf(now.plus(current.relaxationCooldown), current.expiresAt ?: now)
            dao.invalidateRequests(row.id)
            dao.saveRequest(RelaxationRequestEntity(UUID.randomUUID().toString(), row.id, row.revision,
                PolicyCodec.encode(proposed), now.toEpochMilli(), available.toEpochMilli(), "PENDING"))
            record(row.id, row.current, PolicyCodec.encode(proposed), "RELAXATION_REQUESTED", "Explicit acceptance required after $available", now)
        }
    }

    suspend fun cancelRequest(id: String) {
        val now = time.now()
        db.withTransaction {
            val request = requireNotNull(dao.request(id))
            require(request.status == "PENDING") { "Request is no longer pending" }
            dao.saveRequest(request.copy(status = "CANCELLED"))
            record(request.policyId, null, null, "RELAXATION_CANCELLED", "Restriction retained", now)
        }
    }

    suspend fun acceptRequest(id: String) {
        val now = time.now()
        db.withTransaction {
            val r = requireNotNull(dao.request(id))
            require(r.status == "PENDING") { "Request is no longer pending" }
            val row = requireNotNull(dao.find(r.policyId))
            require(row.revision == r.revision) { "Policy changed; request is stale" }
            val current = PolicyCodec.decode(row.current)
            val proposed = PolicyCodec.decode(r.proposed)
            safety.validate(proposed.packages)
            val result = engine.canAcceptRelaxation(current, proposed, Instant.ofEpochMilli(r.requestedAt), Instant.ofEpochMilli(r.availableAt), now)
            require(result.allowed) { result.reason }
            dao.save(row.copy(current = r.proposed, revision = row.revision + 1))
            dao.saveRequest(r.copy(status = "APPLIED"))
            record(row.id, row.current, r.proposed, "RELAXATION_ACCEPTED", result.reason, now)
        }
    }

    suspend fun protect(pkg: String) {
        val now = time.now()
        db.withTransaction {
            require(dao.all().map { PolicyCodec.decode(it.current) }.none { PolicyEvaluation.active(it, now) && pkg in it.packages }) {
                "Cannot exempt an application during its active commitment"
            }
            dao.protect(AppExceptionEntity(pkg))
        }
    }

    suspend fun enforcementEvent(event: String, reason: String) {
        val now = time.now()
        db.withTransaction { record("enforcement", null, null, event, reason, now) }
    }
    private suspend fun record(id: String, old: String?, proposed: String?, event: String, reason: String, now: Instant) {
        dao.history(PolicyHistoryEntity(policyId = id, oldConfiguration = old, proposed = proposed, event = event, reason = reason, at = now.toEpochMilli()))
        Log.i("HardBlock", "event=$event") // No package names or configuration in logcat.
    }
}
