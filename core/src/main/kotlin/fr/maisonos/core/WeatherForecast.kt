package fr.maisonos.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Serializable data class WeatherDay(val date: String, val low: Double, val high: Double, val code: Int,
    val rainChance: Int? = null, val wind: Double? = null, val feelsLow: Double? = null) {
    val description get() = when (code) {
        0 -> "Dégagé"; 1,2 -> "Éclaircies"; 3 -> "Couvert"; 45,48 -> "Brouillard"
        51,53,55,56,57 -> "Bruine"; 61,63,65,66,67 -> "Pluie"; 71,73,75,77 -> "Neige"
        80,81,82 -> "Averses"; 85,86 -> "Neige"; 95,96,99 -> "Orage"; else -> "Variable"
    }
    val clothing: String get() {
        val morning = feelsLow ?: low
        val base = when { morning <= 5 -> "Manteau conseillé"; morning < 12 -> "Veste pour le matin"; high >= 28 -> "Tenue légère"; high < 18 -> "Prévoir une veste"; else -> "Tenue de mi-saison" }
        val rain = if ((rainChance ?: 0) >= 50 || code in setOf(51,53,55,56,57,61,63,65,66,67,80,81,82,95,96,99)) " · protection pluie" else ""
        return base + rain + if ((wind ?: 0.0) >= 40) " · coupe-vent utile" else ""
    }
}
object WeatherForecast {
    fun parse(daily: JsonObject, observedAt: Long, zone: String): List<WeatherDay> {
        val timezone = ZoneId.of(zone)
        val today = Instant.ofEpochMilli(observedAt).atZone(timezone).toLocalDate()
        val dates = daily["time"] as? JsonArray ?: return emptyList()
        fun number(key: String, index: Int) = (daily[key] as? JsonArray)?.getOrNull(index)?.jsonPrimitive?.doubleOrNull?.takeIf(Double::isFinite)
        return dates.take(8).mapIndexedNotNull { index, time -> runCatching {
            val date = Instant.ofEpochSecond(time.jsonPrimitive.long).atZone(timezone).toLocalDate()
            if (date <= today || date > today.plusDays(3)) return@mapIndexedNotNull null
            val low = number("temperature_2m_min",index) ?: return@mapIndexedNotNull null
            val high = number("temperature_2m_max",index) ?: return@mapIndexedNotNull null
            val code = number("weather_code",index)?.toInt() ?: return@mapIndexedNotNull null
            val rain = number("precipitation_probability_max",index)?.toInt()?.takeIf { it in 0..100 }
            val wind = number("wind_speed_10m_max",index)?.takeIf { it in 0.0..500.0 }
            val feels = number("apparent_temperature_min",index)?.takeIf { it in -150.0..100.0 }
            require(low in -100.0..80.0 && high in low..80.0 && code in 0..99)
            WeatherDay(date.toString(),low,high,code,rain,wind,feels)
        }.getOrNull() }.distinctBy { it.date }.sortedBy { it.date }.take(3)
    }
}
