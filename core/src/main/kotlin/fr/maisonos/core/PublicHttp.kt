package fr.maisonos.core

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Optional public HTTPS services; never uses the Wi-Fi camera socket or credentials. */
object PublicHttp {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).build()
    private fun response(url: String): okhttp3.Response {
        var current = url
        repeat(6) {
            requireHttps(current)
            val response = client.newCall(Request.Builder().url(current).header("User-Agent", "MaisonOS-Android").header("Cache-Control", "no-cache").build()).execute()
            if (response.code in setOf(301,302,303,307,308)) {
                val next = response.header("Location")?.let { response.request.url.resolve(it)?.toString() }
                response.close(); current = next ?: throw IOException("Redirection invalide")
            } else {
                if (!response.isSuccessful) { val code = response.code; response.close(); throw PublicHttpFailure(code) }
                return response
            }
        }
        throw IOException("Trop de redirections")
    }
    fun text(url: String, limit: Int = 131072): String = response(url).use { r ->
        val body = r.body ?: throw IOException("Réponse vide")
        require(body.contentLength() <= limit)
        val source = body.source(); source.request(limit.toLong() + 1)
        require(source.buffer.size <= limit)
        source.readUtf8()
    }
    fun download(url: String, file: File, expectedSize: Long, expectedSha256: String, progress: (Long) -> Unit = {}) {
        require(expectedSize in 1024..104857600)
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            response(url).use { r ->
                val body = r.body ?: throw IOException("Réponse vide")
                require(body.contentLength() == -1L || body.contentLength() == expectedSize)
                var count = 0L
                body.byteStream().use { input -> file.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw IOException("Téléchargement interrompu")
                        val n = input.read(buffer); if (n < 0) break
                        count += n; require(count <= expectedSize)
                        output.write(buffer, 0, n); digest.update(buffer, 0, n); progress(count)
                    }
                } }
                require(count == expectedSize)
                require(digest.digest().joinToString("") { "%02x".format(it) }.equals(expectedSha256, true)) { "Empreinte APK différente" }
            }
        } catch (e: Exception) { file.delete(); throw e }
    }
}
class PublicHttpFailure(val status: Int) : IOException("Erreur HTTP $status")

object WeatherApi {
    fun search(name: String): List<WeatherCity> {
        require(name.trim().length in 2..100)
        val encoded = java.net.URLEncoder.encode(name.trim(), "UTF-8")
        val root = kotlinx.serialization.json.Json.parseToJsonElement(PublicHttp.text("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=5&language=fr&format=json"))
        return parseCities(root)
    }
    private fun parseCities(root: kotlinx.serialization.json.JsonElement): List<WeatherCity> {
        val json = root as kotlinx.serialization.json.JsonObject
        val rows = json["results"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return rows.take(5).mapNotNull { value -> runCatching {
            val obj = value as kotlinx.serialization.json.JsonObject
            fun text(key: String) = (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            WeatherCity(listOf(text("name"), text("admin1"), text("country")).filter(String::isNotBlank).joinToString(", ").take(150), text("latitude").toDouble(), text("longitude").toDouble())
                .also { WeatherSettings(city = it.name, latitude = it.latitude, longitude = it.longitude).validate() }
        }.getOrNull() }
    }
    fun current(settings: WeatherSettings): WeatherReading {
        settings.validate(); require(settings.configured)
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${settings.latitude}&longitude=${settings.longitude}&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto&timeformat=unixtime&temperature_unit=celsius&wind_speed_unit=kmh"
        return WeatherReading.parse(PublicHttp.text(url), settings)
    }
}
