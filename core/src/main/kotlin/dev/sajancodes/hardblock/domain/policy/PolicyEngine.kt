package dev.sajancodes.hardblock.domain.policy

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The sole authority for policy edits. Call inside the repository's write transaction.
 * A finite commitment cannot be relaxed by waiting out a shorter cooldown.
 * Delayed relaxation for indefinite commitments requires a separate validated request flow.
 */
class PolicyEngine {
    fun canAcceptRelaxation(current: RestrictionPolicy, proposed: RestrictionPolicy,
        requestedAt: Instant, availableAt: Instant, now: Instant): PolicyDecision {
        if (!validate(current).allowed || !validate(proposed).allowed)
            return PolicyDecision(DecisionKind.REJECT, "Invalid relaxation configuration")
        if (current.id != proposed.id || current.createdAt != proposed.createdAt || current.activatedAt != proposed.activatedAt)
            return PolicyDecision(DecisionKind.REJECT, "History is immutable")
        if (requestedAt > now || now < availableAt || now < requestedAt.plus(current.relaxationCooldown))
            return PolicyDecision(DecisionKind.REJECT, "Relaxation cooldown has not elapsed")
        if (current.expiresAt != null && now < current.expiresAt)
            return PolicyDecision(DecisionKind.REJECT, "Finite commitment has not expired")
        if (current.expiresAt == null && (proposed.expiresAt != null || proposed.relaxationCooldown < current.relaxationCooldown ||
                proposed.resetMinute != current.resetMinute || proposed.zoneId != current.zoneId))
            return PolicyDecision(DecisionKind.REJECT, "Indefinite commitment metadata cannot be shortened or shifted")
        return PolicyDecision(DecisionKind.ALLOW, "Explicit cooled-down acceptance eligible")
    }

    fun validate(policy: RestrictionPolicy): PolicyDecision {
        val invalid = when {
            policy.id.isBlank() -> "Missing policy identity"
            policy.packages.isEmpty() || policy.packages.any(String::isBlank) -> "Select at least one application"
            policy.dailyLimit?.let { it.isNegative || it > Duration.ofDays(1) } == true -> "Invalid daily limit"
            policy.resetMinute !in 0..1439 -> "Invalid daily reset time"
            runCatching { ZoneId.of(policy.zoneId) }.isFailure -> "Invalid time zone"
            policy.relaxationCooldown < Duration.ofHours(24) -> "Cooldown must be at least 24 hours"
            policy.warningMinutes.any { it !in 1..60 } -> "Warning thresholds must be between 1 and 60 minutes"
            policy.activatedAt?.isBefore(policy.createdAt) == true -> "Activation precedes creation"
            policy.expiresAt != null && policy.activatedAt != null && policy.expiresAt <= policy.activatedAt -> "Invalid commitment interval"
            policy.state in setOf(PolicyState.ACTIVE, PolicyState.RELAXATION_PENDING) && policy.activatedAt == null -> "Active policy has no activation timestamp"
            policy.schedule.any { it.day !in 0..6 || it.startMinute !in 0..1439 || it.endMinute !in 0..1439 || it.startMinute == it.endMinute } -> "Invalid schedule window"
            policy.dailyLimit == null && policy.blockedUntil == null && !policy.alwaysBlocked && policy.schedule.isEmpty() -> "Policy has no restriction"
            else -> null
        }
        return if (invalid == null) PolicyDecision(DecisionKind.ALLOW, "Valid configuration")
        else PolicyDecision(DecisionKind.REJECT, invalid)
    }

    fun canApplyChange(current: RestrictionPolicy, proposed: RestrictionPolicy, now: Instant): PolicyDecision {
        if (!validate(current).allowed) return PolicyDecision(DecisionKind.REJECT, "Stored policy is invalid; recovery required")
        validate(proposed).let { if (!it.allowed) return it }
        if (current.id != proposed.id || current.createdAt != proposed.createdAt || current.activatedAt != proposed.activatedAt)
            return PolicyDecision(DecisionKind.REJECT, "Policy identity and activation history are immutable")
        if (current == proposed) return PolicyDecision(DecisionKind.NO_CHANGE, "No change")
        // Derive commitment from immutable activation/expiration, never a UI-supplied state label.
        val committed = current.activatedAt != null && (current.expiresAt == null || now < current.expiresAt)
        if (!committed) return PolicyDecision(DecisionKind.ALLOW, "No active commitment")
        val weakening = when {
            proposed.state !in setOf(PolicyState.ACTIVE, PolicyState.RELAXATION_PENDING) -> "An active commitment cannot be disabled"
            !proposed.packages.containsAll(current.packages) -> "Blocked applications cannot be removed"
            current.expiresAt == null && proposed.expiresAt != null -> "An indefinite commitment cannot be shortened"
            current.expiresAt != null && proposed.expiresAt != null && proposed.expiresAt < current.expiresAt -> "Commitment cannot end earlier"
            proposed.relaxationCooldown < current.relaxationCooldown -> "Relaxation delay cannot be shortened"
            !proposed.warningMinutes.containsAll(current.warningMinutes) -> "Committed warning thresholds cannot be removed"
            current.dailyLimit != null && (proposed.dailyLimit == null || proposed.dailyLimit > current.dailyLimit) -> "Daily allowance cannot increase"
            current.resetMinute != proposed.resetMinute || current.zoneId != proposed.zoneId -> "Reset boundaries cannot change during commitment"
            current.blockedUntil != null && (proposed.blockedUntil == null || proposed.blockedUntil < current.blockedUntil) -> "Block cannot end earlier"
            current.alwaysBlocked && !proposed.alwaysBlocked -> "Permanent block cannot be removed"
            !covers(proposed.schedule, current.schedule) -> "Committed schedule coverage cannot decrease"
            else -> null
        }
        return if (weakening == null) PolicyDecision(DecisionKind.ALLOW, "Restriction is equally strict or stricter")
        else PolicyDecision(DecisionKind.RELAXATION_REQUIRED, weakening)
    }

    private fun covers(proposed: Set<WeeklyWindow>, current: Set<WeeklyWindow>): Boolean {
        fun minutes(windows: Set<WeeklyWindow>): BooleanArray = BooleanArray(7 * 1440).also { bits ->
            windows.forEach { window ->
                val length = (window.endMinute - window.startMinute + 1440) % 1440
                repeat(length) { bits[(window.day * 1440 + window.startMinute + it) % bits.size] = true }
            }
        }
        val before = minutes(current)
        val after = minutes(proposed)
        return before.indices.all { !before[it] || after[it] }
    }
}
