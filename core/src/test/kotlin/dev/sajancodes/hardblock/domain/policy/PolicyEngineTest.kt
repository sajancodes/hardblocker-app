package dev.sajancodes.hardblock.domain.policy

import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.Instant

class PolicyEngineTest {
    private val now = Instant.parse("2026-10-23T10:00:00Z")
    private val engine = PolicyEngine()
    private val base = RestrictionPolicy(
        id = "test", packages = setOf("instagram", "tiktok"), createdAt = now.minusSeconds(60),
        activatedAt = now, expiresAt = now.plus(Duration.ofDays(7)), state = PolicyState.ACTIVE,
        dailyLimit = Duration.ofMinutes(30), zoneId = "Asia/Kathmandu",
    )
    private fun check(kind: DecisionKind, proposed: RestrictionPolicy, current: RestrictionPolicy = base, at: Instant = now) {
        assertEquals(kind, engine.canApplyChange(current, proposed, at).kind)
    }

    @Test fun `30 minutes to 20 allowed`() = check(DecisionKind.ALLOW, base.copy(dailyLimit = Duration.ofMinutes(20)))
    @Test fun `30 minutes to 30 unchanged`() = check(DecisionKind.NO_CHANGE, base.copy())
    @Test fun `30 minutes to 45 requires relaxation`() = check(DecisionKind.RELAXATION_REQUIRED, base.copy(dailyLimit = Duration.ofMinutes(45)))
    @Test fun `limit removal requires relaxation`() = check(DecisionKind.RELAXATION_REQUIRED, base.copy(dailyLimit = null, alwaysBlocked = true))
    @Test fun `zero allowance is valid stricter limit`() = check(DecisionKind.ALLOW, base.copy(dailyLimit = Duration.ZERO))
    @Test fun `negative allowance rejected`() = check(DecisionKind.REJECT, base.copy(dailyLimit = Duration.ofSeconds(-1)))
    @Test fun `allowance above a day rejected`() = check(DecisionKind.REJECT, base.copy(dailyLimit = Duration.ofHours(25)))
    @Test fun `adding application allowed`() = check(DecisionKind.ALLOW, base.copy(packages = base.packages + "youtube"))
    @Test fun `removing application denied`() = check(DecisionKind.RELAXATION_REQUIRED, base.copy(packages = setOf("instagram")))
    @Test fun `empty application list rejected`() = check(DecisionKind.REJECT, base.copy(packages = emptySet()))
    @Test fun `blank package rejected`() = check(DecisionKind.REJECT, base.copy(packages = setOf("")))
    @Test fun `commitment extension allowed`() = check(DecisionKind.ALLOW, base.copy(expiresAt = now.plus(Duration.ofDays(30))))
    @Test fun `commitment shortening denied`() = check(DecisionKind.RELAXATION_REQUIRED, base.copy(expiresAt = now.plus(Duration.ofDays(1))))
    @Test fun `finite to indefinite allowed`() = check(DecisionKind.ALLOW, base.copy(expiresAt = null))
    @Test fun `indefinite to finite denied`() = check(DecisionKind.RELAXATION_REQUIRED, base, base.copy(expiresAt = null))
    @Test fun `longer cooldown allowed`() = check(DecisionKind.ALLOW, base.copy(relaxationCooldown = Duration.ofDays(7)))
    @Test fun `shorter cooldown denied`() = check(DecisionKind.RELAXATION_REQUIRED, base.copy(relaxationCooldown = Duration.ofHours(24)))
    @Test fun `cooldown below safety minimum rejected`() = check(DecisionKind.REJECT, base.copy(relaxationCooldown = Duration.ZERO))
    @Test fun `disable denied`() = check(DecisionKind.RELAXATION_REQUIRED, base.copy(state = PolicyState.DISABLED))
    @Test fun `fake expiration denied`() = check(DecisionKind.RELAXATION_REQUIRED, base.copy(state = PolicyState.EXPIRED))
    @Test fun `fake draft denied`() = check(DecisionKind.RELAXATION_REQUIRED, base.copy(state = PolicyState.DRAFT))
    @Test fun `pending relaxation never authorizes weaker rule`() = check(DecisionKind.RELAXATION_REQUIRED, base.copy(state = PolicyState.RELAXATION_PENDING, dailyLimit = Duration.ofMinutes(45)))
    @Test fun `forged current state does not bypass activation timestamps`() {
        val forged = base.copy(state = PolicyState.EXPIRED)
        check(DecisionKind.RELAXATION_REQUIRED, forged.copy(state = PolicyState.ACTIVE, dailyLimit = Duration.ofMinutes(60)), forged)
    }
    @Test fun `real expiration permits weaker rule`() = check(DecisionKind.ALLOW, base.copy(dailyLimit = Duration.ofMinutes(60)), at = base.expiresAt!!)
    @Test fun `one millisecond before expiration still committed`() = check(DecisionKind.RELAXATION_REQUIRED, base.copy(dailyLimit = Duration.ofMinutes(60)), at = base.expiresAt!!.minusMillis(1))
    @Test fun `cooldown passing does not bypass finite commitment`() = check(DecisionKind.RELAXATION_REQUIRED, base.copy(dailyLimit = Duration.ofMinutes(60)), at = now.plus(Duration.ofDays(4)))
    @Test fun `draft can be freely edited`() {
        val draft = base.copy(activatedAt = null, state = PolicyState.DRAFT)
        check(DecisionKind.ALLOW, draft.copy(dailyLimit = Duration.ofMinutes(60)), draft)
    }
    @Test fun `identity cannot be replaced`() = check(DecisionKind.REJECT, base.copy(id = "forged"))
    @Test fun `creation cannot be replaced`() = check(DecisionKind.REJECT, base.copy(createdAt = now))
    @Test fun `activation cannot be removed`() = check(DecisionKind.REJECT, base.copy(activatedAt = null, state = PolicyState.DRAFT))
    @Test fun `invalid stored policy fails closed`() = check(DecisionKind.REJECT, base, base.copy(dailyLimit = Duration.ofMinutes(-1)))
    @Test fun `active policy without activation invalid`() = check(DecisionKind.REJECT, base.copy(activatedAt = null))
    @Test fun `expiration before activation invalid`() = check(DecisionKind.REJECT, base.copy(expiresAt = now.minusSeconds(1)))
    @Test fun `reset change cannot grant a new allowance`() = check(DecisionKind.RELAXATION_REQUIRED, base.copy(resetMinute = 60))
    @Test fun `zone change cannot shift daily allowance`() = check(DecisionKind.RELAXATION_REQUIRED, base.copy(zoneId = "UTC"))
    @Test fun `invalid zone rejected`() = check(DecisionKind.REJECT, base.copy(zoneId = "unknown"))
    @Test fun `invalid reset rejected`() = check(DecisionKind.REJECT, base.copy(resetMinute = 1440))
    @Test fun `block until extension allowed`() {
        val current = base.copy(blockedUntil = now.plusSeconds(3600))
        check(DecisionKind.ALLOW, current.copy(blockedUntil = now.plusSeconds(7200)), current)
    }
    @Test fun `block until reduction denied`() {
        val current = base.copy(blockedUntil = now.plusSeconds(7200))
        check(DecisionKind.RELAXATION_REQUIRED, current.copy(blockedUntil = now.plusSeconds(3600)), current)
    }
    @Test fun `block until removal denied`() {
        val current = base.copy(blockedUntil = now.plusSeconds(7200))
        check(DecisionKind.RELAXATION_REQUIRED, current.copy(blockedUntil = null), current)
    }
    @Test fun `always block removal denied`() {
        val current = base.copy(alwaysBlocked = true)
        check(DecisionKind.RELAXATION_REQUIRED, current.copy(alwaysBlocked = false), current)
    }
    @Test fun `one stronger dimension cannot compensate weaker dimension`() = check(DecisionKind.RELAXATION_REQUIRED, base.copy(packages = base.packages + "youtube", dailyLimit = Duration.ofMinutes(60)))
    @Test fun `overnight schedule extension allowed`() {
        val current = base.copy(schedule = setOf(WeeklyWindow(0, 22 * 60, 8 * 60)))
        check(DecisionKind.ALLOW, current.copy(schedule = setOf(WeeklyWindow(0, 21 * 60, 9 * 60))), current)
    }
    @Test fun `schedule removal denied`() {
        val current = base.copy(schedule = setOf(WeeklyWindow(0, 1080, 1260)))
        check(DecisionKind.RELAXATION_REQUIRED, current.copy(schedule = emptySet()), current)
    }
    @Test fun `equivalent split schedule coverage allowed`() {
        val current = base.copy(schedule = setOf(WeeklyWindow(0, 1080, 1260)))
        check(DecisionKind.ALLOW, current.copy(schedule = setOf(WeeklyWindow(0, 1080, 1200), WeeklyWindow(0, 1200, 1260))), current)
    }
    @Test fun `Sunday overnight wraps to Monday`() {
        val current = base.copy(schedule = setOf(WeeklyWindow(6, 1380, 60)))
        check(DecisionKind.RELAXATION_REQUIRED, current.copy(schedule = setOf(WeeklyWindow(6, 1380, 30))), current)
    }
    @Test fun `schedule gaps rejected as weakening`() {
        val current = base.copy(schedule = setOf(WeeklyWindow(0, 1080, 1260)))
        check(DecisionKind.RELAXATION_REQUIRED, current.copy(schedule = setOf(WeeklyWindow(0, 1080, 1199), WeeklyWindow(0, 1200, 1260))), current)
    }
    @Test fun `invalid weekday rejected`() = check(DecisionKind.REJECT, base.copy(schedule = setOf(WeeklyWindow(7, 1, 2))))
    @Test fun `empty window rejected`() = check(DecisionKind.REJECT, base.copy(schedule = setOf(WeeklyWindow(0, 60, 60))))
    @Test fun `all minute reductions are monotonic`() {
        (0..30).forEach { minutes -> assertTrue(engine.canApplyChange(base, base.copy(dailyLimit = Duration.ofMinutes(minutes.toLong())), now).allowed) }
        (31..1440).forEach { minutes -> assertFalse(engine.canApplyChange(base, base.copy(dailyLimit = Duration.ofMinutes(minutes.toLong())), now).allowed) }
    }
}
