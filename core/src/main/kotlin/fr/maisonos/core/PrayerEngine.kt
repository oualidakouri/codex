package fr.maisonos.core

import com.batoulapps.adhan2.*
import com.batoulapps.adhan2.data.DateComponents
import java.time.*

enum class PrayerName(val label: String) { FAJR("Fajr"), DHUHR("Dhuhr"), ASR("Asr"), MAGHRIB("Maghrib"), ISHA("Isha") }
data class PrayerMoment(val prayer: PrayerName, val epochMillis: Long)
data class PrayerDay(val date: LocalDate, val sunrise: Long, val moments: List<PrayerMoment>)

object PrayerEngine {
    val methods = listOf("MUSLIM_WORLD_LEAGUE", "EGYPTIAN", "KARACHI", "UMM_AL_QURA", "DUBAI", "QATAR", "KUWAIT", "MOON_SIGHTING_COMMITTEE", "SINGAPORE", "NORTH_AMERICA", "TURKEY", "OTHER")
    val methodLabels = listOf("Ligue islamique mondiale · 18°/17°", "Égypte · 19,5°/17,5°", "Karachi · 18°/18°", "Umm al-Qura · Isha +90 min", "Dubaï", "Qatar", "Koweït", "Moonsighting Committee", "Singapour", "ISNA · 15°/15°", "Turquie", "Angles personnalisés")
    fun calculate(date: LocalDate, settings: PrayerSettings): PrayerDay {
        settings.validate(); require(settings.configured) { "Position non configurée" }
        val method = CalculationMethod.valueOf(settings.method)
        val adjustment = settings.adjustments
        val params = method.parameters.copy(
            madhab = if (settings.hanafi) Madhab.HANAFI else Madhab.SHAFI,
            highLatitudeRule = HighLatitudeRule.valueOf(settings.highLatitude),
            prayerAdjustments = PrayerAdjustments(fajr = adjustment["FAJR"] ?: 0, dhuhr = adjustment["DHUHR"] ?: 0, asr = adjustment["ASR"] ?: 0, maghrib = adjustment["MAGHRIB"] ?: 0, isha = adjustment["ISHA"] ?: 0),
        ).let { if (method == CalculationMethod.OTHER) it.copy(fajrAngle = settings.fajrAngle, ishaAngle = settings.ishaAngle) else it }
        val times = PrayerTimes(Coordinates(settings.latitude!!, settings.longitude!!), DateComponents(date.year, date.monthValue, date.dayOfMonth), params)
        val moments = listOf(times.fajr, times.dhuhr, times.asr, times.maghrib, times.isha).mapIndexed { index, instant -> PrayerMoment(PrayerName.entries[index], instant.toEpochMilliseconds()) }
        val ordered = listOf(moments[0].epochMillis, times.sunrise.toEpochMilliseconds()) + moments.drop(1).map { it.epochMillis }
        require(ordered.zipWithNext().all { (a, b) -> b > a } && ordered.last() - ordered.first() < 26 * 3600000L) { "Horaires incohérents : vérifier méthode, latitude et ajustements" }
        return PrayerDay(date, times.sunrise.toEpochMilliseconds(), moments)
    }
    fun next(settings: PrayerSettings, now: Instant): PrayerMoment? {
        if (!settings.configured) return null
        val date = now.atZone(ZoneId.of(settings.zone)).toLocalDate()
        return (-1..2).flatMap { calculate(date.plusDays(it.toLong()), settings).moments }.sortedBy { it.epochMillis }.firstOrNull { it.epochMillis > now.toEpochMilli() }
    }
    fun nextAlarms(settings: PrayerSettings, now: Instant): List<PrayerMoment> {
        if (!settings.configured) return emptyList()
        val date = now.atZone(ZoneId.of(settings.zone)).toLocalDate()
        val moments = (-1..2).flatMap { calculate(date.plusDays(it.toLong()), settings).moments }.sortedBy { it.epochMillis }
        return PrayerName.entries.mapNotNull { p -> moments.firstOrNull { it.prayer == p && it.epochMillis > now.toEpochMilli() && p.name in settings.enabled } }
    }
    fun quiet(settings: PrayerSettings, instant: Instant): Boolean {
        if (settings.quietStart < 0 || settings.quietEnd < 0 || settings.quietStart == settings.quietEnd) return false
        val hour = instant.atZone(ZoneId.of(settings.zone)).hour
        return if (settings.quietStart < settings.quietEnd) hour in settings.quietStart until settings.quietEnd
        else hour >= settings.quietStart || hour < settings.quietEnd
    }
}
