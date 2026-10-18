package fr.maisonos.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*
import kotlin.math.abs

class PrayerEngineTest {
    @Test fun meccaMatchesPublishedAdhanReferenceWithinOneMinute() {
        // Batoul Apps Shared/Times/Makkah-UmmAlQura.json, 2016-01-05.
        val p = PrayerSettings(latitude = 21.427009, longitude = 39.828685, zone = "Asia/Riyadh", method = "UMM_AL_QURA", highLatitude = "MIDDLE_OF_THE_NIGHT")
        val day = PrayerEngine.calculate(LocalDate.of(2016, 1, 5), p)
        val expected = listOf("05:38", "12:26", "15:31", "17:52", "19:22")
        day.moments.zip(expected).forEach { (actual, time) ->
            val local = Instant.ofEpochMilli(actual.epochMillis).atZone(ZoneId.of(p.zone)).toLocalTime()
            assertTrue("${actual.prayer}: $local vs $time", abs(Duration.between(LocalTime.parse(time), local).toMinutes()) <= 1)
        }
    }
    private val paris = PrayerSettings(latitude = 48.8566, longitude = 2.3522, zone = "Europe/Paris")
    @Test fun nextPrayerRollsIntoTomorrowAfterIsha() {
        val day = PrayerEngine.calculate(LocalDate.of(2026, 3, 28), paris)
        val now = Instant.ofEpochMilli(day.moments.last().epochMillis).plusSeconds(1)
        val next = PrayerEngine.next(paris, now)!!
        assertEquals(PrayerName.FAJR, next.prayer)
        assertEquals(LocalDate.of(2026, 3, 29), Instant.ofEpochMilli(next.epochMillis).atZone(ZoneId.of(paris.zone)).toLocalDate())
    }
    @Test fun ishaAdjustedAfterMidnightStillBelongsToThePreviousPrayerDay() {
        val settings = paris.copy(adjustments = mapOf("ISHA" to 120), enabled = setOf("ISHA"))
        val isha = PrayerEngine.calculate(LocalDate.of(2026,6,21), settings).moments.last()
        assertEquals(LocalDate.of(2026,6,22), Instant.ofEpochMilli(isha.epochMillis).atZone(ZoneId.of(settings.zone)).toLocalDate())
        val now = Instant.ofEpochMilli(isha.epochMillis).minusSeconds(60)
        assertEquals(isha, PrayerEngine.next(settings, now))
        assertEquals(isha, PrayerEngine.nextAlarms(settings, now).first())
    }
    @Test fun dstComesFromZoneRulesAndNotAnAddedTwentyFourHours() {
        val before = PrayerEngine.calculate(LocalDate.of(2026, 3, 28), paris).moments[1]
        val after = PrayerEngine.calculate(LocalDate.of(2026, 3, 29), paris).moments[1]
        val a = Instant.ofEpochMilli(before.epochMillis).atZone(ZoneId.of(paris.zone))
        val b = Instant.ofEpochMilli(after.epochMillis).atZone(ZoneId.of(paris.zone))
        assertEquals(3600, a.offset.totalSeconds); assertEquals(7200, b.offset.totalSeconds)
        assertTrue(abs(Duration.between(a.toLocalTime(), b.toLocalTime()).toMinutes() - 60) <= 2)
    }
    @Test fun madhabAndPerPrayerAdjustmentAreApplied() {
        val date = LocalDate.of(2026, 10, 18)
        val base = PrayerEngine.calculate(date, paris)
        val adjusted = PrayerEngine.calculate(date, paris.copy(adjustments = mapOf("DHUHR" to 7)))
        assertEquals(7 * 60000L, adjusted.moments[1].epochMillis - base.moments[1].epochMillis)
        assertEquals(base.moments[0], adjusted.moments[0])
        assertTrue(PrayerEngine.calculate(date, paris.copy(hanafi = true)).moments[2].epochMillis > base.moments[2].epochMillis)
    }
    @Test fun alarmsAreFutureUniqueAndRespectDisabledPrayers() {
        val p = paris.copy(enabled = setOf("FAJR", "ISHA"))
        val now = Instant.parse("2026-10-18T22:30:00Z")
        val alarms = PrayerEngine.nextAlarms(p, now)
        assertEquals(setOf(PrayerName.FAJR, PrayerName.ISHA), alarms.map { it.prayer }.toSet())
        assertTrue(alarms.all { it.epochMillis > now.toEpochMilli() })
        assertEquals(emptyList<PrayerMoment>(), PrayerEngine.nextAlarms(p.copy(enabled = emptySet()), now))
        assertNull(PrayerEngine.next(PrayerSettings(), now))
    }
    @Test fun nightQuietHoursHandleMidnight() {
        val p = paris.copy(quietStart = 22, quietEnd = 7)
        assertTrue(PrayerEngine.quiet(p, Instant.parse("2026-10-18T22:30:00Z")))
        assertTrue(PrayerEngine.quiet(p, Instant.parse("2026-10-19T03:30:00Z")))
        assertFalse(PrayerEngine.quiet(p, Instant.parse("2026-10-19T10:30:00Z")))
    }
    @Test fun invalidCoordinatesTimezoneAndIncoherentAdjustmentsFail() {
        assertThrows(Exception::class.java) { PrayerEngine.calculate(LocalDate.now(), paris.copy(zone = "Invalid/Zone")) }
        assertThrows(IllegalArgumentException::class.java) { paris.copy(latitude = Double.NaN).validate() }
        assertThrows(IllegalArgumentException::class.java) { paris.copy(latitude = 192.0).validate() }
        assertThrows(IllegalArgumentException::class.java) { PrayerEngine.calculate(LocalDate.of(2026, 10, 18), paris.copy(adjustments = mapOf("FAJR" to 120))) }
    }
}

class LayoutEngineTest {
    @Test fun defaultLandscapeAndPortraitLayoutsAreValidAndDisjoint() {
        val cfg = HomeConfig(); cfg.validate()
        for ((columns, widgets) in listOf(6 to cfg.profile().landscape, 4 to cfg.profile().portrait)) {
            assertTrue(widgets.all { it.x >= 0 && it.x + it.w <= columns })
            assertTrue(widgets.indices.all { i -> widgets.indices.all { j -> i == j || !LayoutEngine.overlaps(widgets[i], widgets[j]) } })
        }
    }
    @Test fun movingIntoAnotherWidgetRelocatesOthersAndPreservesAllIds() {
        val widgets = defaultWidgets()
        val moved = LayoutEngine.move(widgets, widgets.first().id, 3, 0, columns = 6)
        assertEquals(widgets.map { it.id }, moved.map { it.id })
        assertEquals(3, moved.first().x)
        assertTrue(moved.indices.all { i -> moved.indices.all { j -> i == j || !LayoutEngine.overlaps(moved[i], moved[j]) } })
    }
    @Test fun resizingClampsToGridAndIndependentPagesCanShareCells() {
        val a = HomeWidget(kind = WidgetKind.CLOCK, page = 0)
        val b = HomeWidget(kind = WidgetKind.DUA, page = 1)
        val resized = LayoutEngine.move(listOf(a,b), a.id, 500, -20, 12, 20, 4)
        assertEquals(4, resized.first().w); assertEquals(6, resized.first().h); assertEquals(0, resized.first().x); assertEquals(0, resized.first().y)
        assertEquals(0, resized[1].y); assertFalse(LayoutEngine.overlaps(resized[0], resized[1]))
    }
}

class RuleEngineTest {
    @Test fun rulesHonorTypeEnabledStateAndCooldown() {
        val engine = RuleEngine(); val rule = LocalRule(cooldownSeconds = 30)
        assertEquals(1, engine.match(listOf(rule), "mailbox", 0).size)
        assertTrue(engine.match(listOf(rule), "mailbox", 29999).isEmpty())
        assertEquals(1, engine.match(listOf(rule), "mailbox", 30000).size)
        assertTrue(engine.match(listOf(rule.copy(enabled = false)), "mailbox", 60000).isEmpty())
        assertTrue(engine.match(listOf(rule), "presence", 60000).isEmpty())
    }
}
