package fr.maisonos.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.net.URI
import java.time.Instant

const val DEFAULT_UPDATE_URL = "https://raw.githubusercontent.com/oualidakouri/codex/main/downloads/update.json"

fun requireHttps(url: String) {
    require(url.length in 8..2048)
    val u = URI(url)
    require(u.scheme == "https" && !u.host.isNullOrBlank() && u.rawUserInfo == null && u.fragment == null) { "Une URL HTTPS sans identifiants est requise" }
}

@Serializable data class UpdateSettings(val enabled: Boolean = true, val manifestUrl: String = DEFAULT_UPDATE_URL) {
    fun validate() { requireHttps(manifestUrl) }
}
@Serializable data class AppRelease(val versionCode: Long, val versionName: String, val apkUrl: String, val sha256: String,
    val packageName: String, val size: Long, val notes: String = "", val minSdk: Int = 26) {
    fun validate() {
        require(versionCode > 0 && versionName.length in 1..40 && notes.length <= 4000 && minSdk in 26..100)
        require(packageName == "fr.maisonos.diagnostic" && size in 1024..104857600)
        require(sha256.matches(Regex("[a-fA-F0-9]{64}"))); requireHttps(apkUrl)
    }
    companion object { fun parse(text: String): AppRelease {
        require(text.toByteArray().size <= 65536)
        return Json { ignoreUnknownKeys = true }.decodeFromString<AppRelease>(text).also { it.validate() }
    } }
}

@Serializable data class WeatherSettings(val enabled: Boolean = true, val city: String = "", val latitude: Double? = null, val longitude: Double? = null) {
    val configured get() = latitude != null && longitude != null
    fun validate() {
        require(city.length <= 150 && (latitude == null) == (longitude == null))
        latitude?.let { require(it.isFinite() && it in -90.0..90.0) }
        longitude?.let { require(it.isFinite() && it in -180.0..180.0) }
    }
}
@Serializable data class WeatherReading(val city: String, val latitude: Double, val longitude: Double, val observedAt: Long,
    val fetchedAt: Long, val temperature: Double, val feelsLike: Double, val humidity: Int, val wind: Double,
    val code: Int, val high: Double, val low: Double, val forecast: List<WeatherDay> = emptyList(), val zone: String = "UTC") {
    val description get() = when (code) {
        0 -> "Ciel dégagé"; 1, 2 -> "Éclaircies"; 3 -> "Couvert"; 45, 48 -> "Brouillard"
        51,53,55,56,57 -> "Bruine"; 61,63,65,66,67 -> "Pluie"; 71,73,75,77 -> "Neige"
        80,81,82 -> "Averses"; 85,86 -> "Averses de neige"; 95,96,99 -> "Orage"; else -> "Conditions variables"
    }
    companion object { fun parse(text: String, settings: WeatherSettings, now: Long = System.currentTimeMillis()): WeatherReading {
        settings.validate(); require(settings.configured)
        val root = Json.parseToJsonElement(text).jsonObject
        val current = root.getValue("current").jsonObject; val daily = root.getValue("daily").jsonObject
        fun number(key: String) = current.getValue(key).jsonPrimitive.double.also { require(it.isFinite()) }
        fun daily(key: String) = daily.getValue(key).jsonArray.first().jsonPrimitive.double.also { require(it.isFinite()) }
        val time = current.getValue("time").jsonPrimitive.long * 1000
        require(time in now - 172800000..now + 7200000)
        val reading = WeatherReading(settings.city, settings.latitude!!, settings.longitude!!, time, now, number("temperature_2m"), number("apparent_temperature"),
            current.getValue("relative_humidity_2m").jsonPrimitive.int, number("wind_speed_10m"), current.getValue("weather_code").jsonPrimitive.int,
            daily("temperature_2m_max"), daily("temperature_2m_min"), WeatherForecast.parse(daily, time, root["timezone"]?.jsonPrimitive?.content ?: "UTC"), root["timezone"]?.jsonPrimitive?.content ?: "UTC")
        require(reading.temperature in -100.0..80.0 && reading.feelsLike in -150.0..100.0 && reading.humidity in 0..100 && reading.wind in 0.0..500.0)
        return reading
    } }
}
data class WeatherCity(val name: String, val latitude: Double, val longitude: Double)
