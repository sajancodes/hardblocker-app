package dev.sajancodes.hardblock.domain.usage

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class UsageAccountingTest {
    private fun event(at: Long, type: UsageEventType, pkg: String = "a", activity: String = "main") = ForegroundEvent(at, type, pkg, activity)
    private fun total(events: List<ForegroundEvent>, until: Long = 1000): Long =
        UsageAccounting.totals(UsageAccounting.replay(events, until).intervals, UsageWindow(0, 2000), until)["a"] ?: 0

    @Test fun `rapid switching excludes home`() {
        assertEquals(400L, total(listOf(event(100, UsageEventType.RESUME), event(300, UsageEventType.RESUME, "home"), event(500, UsageEventType.RESUME), event(700, UsageEventType.PAUSE))))
    }
    @Test fun `duplicate resumes do not restart or double count`() = assertEquals(900L, total(listOf(event(100, UsageEventType.RESUME), event(100, UsageEventType.RESUME), event(200, UsageEventType.RESUME))))
    @Test fun `late pause from old activity does not close newer activity`() = assertEquals(900L, total(listOf(event(100, UsageEventType.RESUME), event(200, UsageEventType.RESUME, activity = "detail"), event(250, UsageEventType.PAUSE))))
    @Test fun `screen lock stops accounting`() = assertEquals(300L, total(listOf(event(100, UsageEventType.RESUME), event(400, UsageEventType.STOP_ALL))))
    @Test fun `reboot closes session and new usage resumes separately`() = assertEquals(500L, total(listOf(event(100, UsageEventType.RESUME), event(400, UsageEventType.STOP_ALL), event(800, UsageEventType.RESUME))))
    @Test fun `pause of unrelated package ignored`() = assertEquals(900L, total(listOf(event(100, UsageEventType.RESUME), event(200, UsageEventType.PAUSE, "other"))))
    @Test fun `pause without resume counts nothing`() = assertEquals(0L, total(listOf(event(100, UsageEventType.PAUSE))))
    @Test fun `future events ignored`() = assertEquals(0L, total(listOf(event(1001, UsageEventType.RESUME))))
    @Test fun `open activity clipped at now`() = assertEquals(900L, total(listOf(event(100, UsageEventType.RESUME))))
    @Test fun `no events does not invent usage`() = assertEquals(0L, total(emptyList()))
    @Test fun `overlapping snapshots union after process restart`() {
        val entries = listOf(UsageInterval("a", 100, 400), UsageInterval("a", 100, 600), UsageInterval("a", 300, 800))
        assertEquals(700L, UsageAccounting.totals(entries, UsageWindow(0, 1000), 1000)["a"])
    }
    @Test fun `midnight carry in clipped to new day`() {
        assertEquals(200L, UsageAccounting.totals(listOf(UsageInterval("a", 100, 700)), UsageWindow(500, 1500), 1000)["a"])
    }
    @Test fun `backward time never yields negative usage`() {
        assertEquals(0L, UsageAccounting.totals(listOf(UsageInterval("a", 100, 700)), UsageWindow(500, 1500), 200)["a"])
    }
    @Test fun `foreground cleared on screen off`() = assertNull(UsageAccounting.replay(listOf(event(100, UsageEventType.RESUME), event(200, UsageEventType.STOP_ALL)), 300).foregroundPackage)
    @Test fun `current foreground exposed`() = assertEquals("a", UsageAccounting.replay(listOf(event(100, UsageEventType.RESUME)), 300).foregroundPackage)
    @Test fun `DST spring day is 23 hours`() {
        val window = UsageWindow.containing(Instant.parse("2026-03-08T12:00:00Z"), ZoneId.of("America/New_York"))
        assertEquals(Duration.ofHours(23).toMillis(), window.end - window.start)
    }
    @Test fun `DST fall day is 25 hours`() {
        val window = UsageWindow.containing(Instant.parse("2026-11-01T12:00:00Z"), ZoneId.of("America/New_York"))
        assertEquals(Duration.ofHours(25).toMillis(), window.end - window.start)
    }
    @Test fun `Kathmandu midnight uses 45 minute offset`() {
        val window = UsageWindow.containing(Instant.parse("2026-10-23T10:00:00Z"), ZoneId.of("Asia/Kathmandu"))
        assertEquals(Instant.parse("2026-10-22T18:15:00Z").toEpochMilli(), window.start)
    }
    @Test fun `timezone change re-buckets absolute usage without duplicate intervals`() {
        val now = Instant.parse("2026-10-23T01:00:00Z")
        val utc = UsageWindow.containing(now, ZoneId.of("UTC"))
        val ny = UsageWindow.containing(now, ZoneId.of("America/New_York"))
        assertNotEquals(utc.start, ny.start)
        val intervals = listOf(UsageInterval("a", now.minusSeconds(60).toEpochMilli(), now.toEpochMilli()))
        assertEquals(60000L, UsageAccounting.totals(intervals, utc, now.toEpochMilli())["a"])
        assertEquals(60000L, UsageAccounting.totals(intervals, ny, now.toEpochMilli())["a"])
    }
    @Test fun `exact midnight starts new window`() {
        val instant = Instant.parse("2026-10-23T00:00:00Z")
        assertEquals(instant.toEpochMilli(), UsageWindow.containing(instant, ZoneId.of("UTC")).start)
    }
    @Test fun `custom reset before boundary belongs to yesterday`() {
        val instant = Instant.parse("2026-10-23T03:00:00Z")
        assertEquals(Instant.parse("2026-10-22T04:00:00Z").toEpochMilli(), UsageWindow.containing(instant, ZoneId.of("UTC"), LocalTime.of(4, 0)).start)
    }
}
