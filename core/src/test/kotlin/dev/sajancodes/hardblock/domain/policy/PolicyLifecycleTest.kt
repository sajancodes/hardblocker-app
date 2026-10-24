package dev.sajancodes.hardblock.domain.policy

import dev.sajancodes.hardblock.domain.time.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class PolicyLifecycleTest {
    private val now = Instant.parse("2026-10-24T12:00:00Z")
    private val engine = PolicyEngine()
    private val p = RestrictionPolicy("p", setOf("app"), now.minusSeconds(1), now,
        now.plus(Duration.ofDays(7)), PolicyState.ACTIVE, Duration.ofMinutes(30), zoneId = "UTC")
    @Test fun finiteCommitmentCannotBeRelaxedAfterOnlyCooldown() {
        assertFalse(engine.canAcceptRelaxation(p, p.copy(dailyLimit = Duration.ofHours(1)), now, now.plus(Duration.ofDays(3)), now.plus(Duration.ofDays(4))).allowed)
    }
    @Test fun indefiniteRelaxationNeedsFullCooldown() {
        val indefinite = p.copy(expiresAt = null)
        assertFalse(engine.canAcceptRelaxation(indefinite, indefinite.copy(state = PolicyState.DISABLED), now, now.plus(Duration.ofDays(3)), now.plus(Duration.ofDays(2))).allowed)
        assertTrue(engine.canAcceptRelaxation(indefinite, indefinite.copy(state = PolicyState.DISABLED), now, now.plus(Duration.ofDays(3)), now.plus(Duration.ofDays(3))).allowed)
    }
    @Test fun forgedEarlierAvailableAtCannotShortenDelay() {
        val indefinite = p.copy(expiresAt = null)
        assertFalse(engine.canAcceptRelaxation(indefinite, indefinite.copy(dailyLimit = Duration.ofHours(1)), now, now, now.plusSeconds(1)).allowed)
    }
    @Test fun indefiniteRequestCannotShortenCooldownOrCommitment() {
        val indefinite = p.copy(expiresAt = null)
        assertFalse(engine.canAcceptRelaxation(indefinite, indefinite.copy(relaxationCooldown = Duration.ofDays(1)), now, now.plus(Duration.ofDays(3)), now.plus(Duration.ofDays(4))).allowed)
        assertFalse(engine.canAcceptRelaxation(indefinite, indefinite.copy(expiresAt = now.plus(Duration.ofDays(5))), now, now.plus(Duration.ofDays(3)), now.plus(Duration.ofDays(4))).allowed)
    }
    @Test fun finiteExpirationAndCooldownAllowExplicitAcceptance() {
        assertTrue(engine.canAcceptRelaxation(p, p.copy(state = PolicyState.DISABLED), now, now.plus(Duration.ofDays(7)), now.plus(Duration.ofDays(7))).allowed)
    }
    @Test fun foreignPolicyIdentityRejectedAtAcceptance() {
        assertFalse(engine.canAcceptRelaxation(p, p.copy(id = "other"), now, now.plus(Duration.ofDays(7)), now.plus(Duration.ofDays(8))).allowed)
    }
    @Test fun dailyLimitBlocksAtExactBoundary() {
        assertNull(PolicyEvaluation.reason(p, "app", Duration.ofMinutes(30).toMillis() - 1, now))
        val reason = PolicyEvaluation.reason(p, "app", Duration.ofMinutes(30).toMillis(), now)!!
        assertEquals("Daily limit reached", reason.message)
        assertEquals(Instant.parse("2026-10-25T00:00:00Z"), reason.availableAt)
    }
    @Test fun unselectedAppsAndExpiredCommitmentsDoNotBlock() {
        assertNull(PolicyEvaluation.reason(p, "other", Long.MAX_VALUE, now))
        assertNull(PolicyEvaluation.reason(p, "app", Long.MAX_VALUE, p.expiresAt!!))
    }
    @Test fun overnightScheduleChecksFollowingDayAndSundayWrap() {
        val scheduled = p.copy(dailyLimit = null, schedule = setOf(WeeklyWindow(5, 23 * 60, 7 * 60)))
        assertNotNull(PolicyEvaluation.reason(scheduled, "app", 0, Instant.parse("2026-10-24T23:30:00Z")))
        assertEquals(Instant.parse("2026-10-25T07:00:00Z"), PolicyEvaluation.reason(scheduled, "app", 0, Instant.parse("2026-10-25T01:00:00Z"))!!.availableAt)
        assertNull(PolicyEvaluation.reason(scheduled, "app", 0, Instant.parse("2026-10-25T07:00:00Z")))
    }
    @Test fun adjacentScheduleIntervalsRemainBlockedUntilUnionEnds() {
        val scheduled = p.copy(dailyLimit = null, schedule = setOf(WeeklyWindow(5, 12 * 60, 13 * 60), WeeklyWindow(5, 13 * 60, 14 * 60)))
        assertEquals(Instant.parse("2026-10-24T14:00:00Z"), PolicyEvaluation.reason(scheduled, "app", 0, now)!!.availableAt)
    }
    @Test fun focusSurvivesProcessRestartByAbsoluteExpiry() {
        val focus = p.copy(dailyLimit = null, blockedUntil = now.plusSeconds(10800))
        assertNotNull(PolicyEvaluation.reason(focus.copy(), "app", 0, now.plusSeconds(3600)))
        assertNull(PolicyEvaluation.reason(focus, "app", 0, now.plusSeconds(10800)))
    }
    @Test fun sameBootForwardOrBackwardClockCannotShortenDeadline() {
        val checkpoint = TimeCheckpoint(1_000_000, 1_000_000, 500, 1)
        val back = TimeIntegrity.read(checkpoint, 0, 1500, 1)
        val forward = TimeIntegrity.read(checkpoint, 100_000_000, 1500, 1)
        assertEquals(1_001_000L, back.checkpoint.trustedMillis)
        assertEquals(back.checkpoint.trustedMillis, forward.checkpoint.trustedMillis)
        assertTrue(back.suspicious); assertTrue(forward.suspicious)
    }
    @Test fun rebootRetainsBackwardFloorWithoutPermanentLockout() {
        val checkpoint = TimeCheckpoint(1_000_000, 1_000_000, 500, 1)
        val back = TimeIntegrity.read(checkpoint, 0, 50, 2)
        assertEquals(1_000_000L, back.checkpoint.trustedMillis)
        val progress = TimeIntegrity.read(back.checkpoint, 1000, 1050, 2)
        assertEquals(1_001_000L, progress.checkpoint.trustedMillis)
    }
}
