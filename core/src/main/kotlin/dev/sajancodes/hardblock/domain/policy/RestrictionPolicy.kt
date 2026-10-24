package dev.sajancodes.hardblock.domain.policy

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

enum class PolicyState { DRAFT, ACTIVE, EXPIRED, RELAXATION_PENDING, DISABLED }
enum class EnforcementState { ENFORCED, PENDING, INTERRUPTED, UNSUPPORTED }

/** A conjunction of restrictions: every configured rule must hold. Null means unlimited. */
data class RestrictionPolicy(
    val id: String,
    val packages: Set<String>,
    val createdAt: Instant,
    val activatedAt: Instant? = null,
    val expiresAt: Instant? = null,
    val state: PolicyState = PolicyState.DRAFT,
    val dailyLimit: Duration? = null,
    val resetMinute: Int = 0,
    val zoneId: String = ZoneId.systemDefault().id,
    val blockedUntil: Instant? = null,
    val alwaysBlocked: Boolean = false,
    val schedule: Set<WeeklyWindow> = emptySet(),
    val relaxationCooldown: Duration = Duration.ofHours(72),
    val warningMinutes: Set<Int> = setOf(5, 1),
)

/** Monday = 0. An end earlier than start runs into the next day; equal is invalid. */
data class WeeklyWindow(val day: Int, val startMinute: Int, val endMinute: Int)

enum class DecisionKind { ALLOW, NO_CHANGE, RELAXATION_REQUIRED, REJECT }
data class PolicyDecision(val kind: DecisionKind, val reason: String) {
    val allowed: Boolean get() = kind == DecisionKind.ALLOW || kind == DecisionKind.NO_CHANGE
}
