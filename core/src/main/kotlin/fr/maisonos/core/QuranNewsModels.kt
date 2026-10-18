package fr.maisonos.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.net.URI
import java.net.URLEncoder
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Serializable data class QuranSettings(val custom: Boolean = true, val url: String = "", val reciterId: Int = 0,
    val moshafId: Int = 0, val reciter: String = "", val server: String = "", val surah: Int = 1, val surahName: String = "",
    val volume: Float = 0.8f) {
    fun validate() {
        require(surah in 1..114 && volume in 0f..1f && reciter.length <= 180 && surahName.length <= 120)
        require(reciterId >= 0 && moshafId >= 0)
        if (url.isNotBlank()) requireAudioUrl(url)
        if (server.isNotBlank()) requireHttps(server)
    }
    fun playbackUrl(): String {
        validate()
        return if (custom) url.also { require(it.isNotBlank()); requireAudioUrl(it) }
        else { require(reciterId > 0 && server.isNotBlank()); requireHttps(server); server.trimEnd('/') + "/%03d.mp3".format(java.util.Locale.ROOT, surah) }
    }
    val title get() = if (custom) "Coran · flux personnel" else "$reciter · ${surahName.ifBlank { "Sourate $surah" }}"
    fun exportSafe() = copy(url = "")
}
fun requireAudioUrl(url: String) {
    require(url.length in 8..2048)
    val uri = URI(url)
    require(uri.scheme in setOf("http","https") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.fragment == null)
}
@Serializable data class QuranRecitation(val reciterId: Int, val moshafId: Int, val name: String, val reading: String, val server: String, val surahs: List<Int>)
@Serializable data class QuranSurah(val id: Int, val name: String)
@Serializable data class QuranCatalog(val recitations: List<QuranRecitation> = emptyList(), val surahs: List<QuranSurah> = emptyList(), val fetchedAt: Long = 0)
object QuranApi {
    const val RECITERS_URL = "https://mp3quran.net/api/v3/reciters?language=ar"
    const val SURAHS_URL = "https://mp3quran.net/api/v3/suwar?language=ar"
    fun parse(reciters: String, surahs: String, now: Long = System.currentTimeMillis()): QuranCatalog {
        require(reciters.toByteArray().size <= 2097152 && surahs.toByteArray().size <= 262144)
        val recitations = mutableListOf<QuranRecitation>()
        val root = Json.parseToJsonElement(reciters).jsonObject
        for (entry in root.getValue("reciters").jsonArray.take(1000)) {
            val r = entry.jsonObject
            val id = r["id"]?.jsonPrimitive?.intOrNull ?: continue
            val name = r["name"]?.jsonPrimitive?.content?.take(180).orEmpty()
            if (id <= 0 || name.isBlank()) continue
            for (item in (r["moshaf"] as? JsonArray).orEmpty().take(30)) runCatching {
                val m = item.jsonObject
                val mid = m.getValue("id").jsonPrimitive.int
                val server = m.getValue("server").jsonPrimitive.content.trim(); requireHttps(server)
                val reading = m.getValue("name").jsonPrimitive.content.take(180)
                val available = m.getValue("surah_list").jsonPrimitive.content.split(',').mapNotNull { it.trim().toIntOrNull()?.takeIf { s -> s in 1..114 } }.distinct().sorted()
                require(mid > 0 && available.isNotEmpty())
                recitations += QuranRecitation(id,mid,name,reading,server,available)
            }
        }
        val names = Json.parseToJsonElement(surahs).jsonObject.getValue("suwar").jsonArray.mapNotNull { item -> runCatching {
            val s = item.jsonObject; val id = s.getValue("id").jsonPrimitive.int; val name = s.getValue("name").jsonPrimitive.content
            require(id in 1..114 && name.isNotBlank() && name.length <= 120); QuranSurah(id,name)
        }.getOrNull() }.distinctBy { it.id }.sortedBy { it.id }
        require(recitations.isNotEmpty() && names.isNotEmpty())
        return QuranCatalog(recitations.distinctBy { "${it.reciterId}/${it.moshafId}" },names,now)
    }
    fun fetch(names: List<QuranSurah> = emptyList()): QuranCatalog {
        val surahs = if (names.isEmpty()) PublicHttp.text(SURAHS_URL,262144) else buildJsonObject { putJsonArray("suwar") { names.forEach { name -> add(buildJsonObject { put("id",name.id); put("name",name.name) }) } } }.toString()
        return parse(PublicHttp.text(RECITERS_URL,2097152),surahs)
    }
}

@Serializable data class NewsSettings(val enabled: Boolean = true, val city: String = "", val franceUrl: String = "https://www.francetvinfo.fr/titres.rss", val localUrl: String = "") {
    fun validate() { require(city.length <= 150); requireHttps(franceUrl); if (localUrl.isNotBlank()) requireHttps(localUrl) }
    fun localFeed(weatherCity: String): String? {
        if (localUrl.isNotBlank()) return localUrl
        val place = city.ifBlank { weatherCity.substringBefore(',').trim().takeUnless { it == "Ma position actuelle" }.orEmpty() }
        if (place.isBlank()) return null
        return "https://news.google.com/rss/search?q=" + URLEncoder.encode("\"$place\" when:3d", "UTF-8") + "&hl=fr&gl=FR&ceid=FR:fr"
    }
}
@Serializable data class NewsArticle(val title: String, val url: String, val source: String, val publishedAt: Long? = null)
@Serializable data class NewsFeed(val url: String, val items: List<NewsArticle>, val fetchedAt: Long)
object NewsParser {
    fun parse(xml: String, feedUrl: String, now: Long = System.currentTimeMillis()): NewsFeed {
        requireHttps(feedUrl)
        val root = SafeXml.parse(xml)
        val rss = SafeXml.all(root,"item"); val entries = if (rss.isNotEmpty()) rss else SafeXml.all(root,"entry")
        val fallback = SafeXml.text(root,"title").take(120).ifBlank { URI(feedUrl).host }
        fun clean(value: String) = value.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
        val items = entries.take(100).mapNotNull { item -> runCatching {
            val title = clean(SafeXml.text(item,"title")).take(350); require(title.isNotBlank())
            val link = SafeXml.all(item,"link").firstOrNull { it.getAttribute("rel") in setOf("", "alternate") } ?: error("Lien absent")
            val url = (link.getAttribute("href").takeIf(String::isNotBlank) ?: link.textContent).trim()
            requireHttps(url)
            val source = clean(SafeXml.text(item,"source")).take(120).ifBlank { fallback }
            val date = listOf("pubDate","published","updated").firstNotNullOfOrNull { key -> SafeXml.text(item,key).takeIf(String::isNotBlank) }
            val published = date?.let { raw -> runCatching { ZonedDateTime.parse(raw,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull() }
            NewsArticle(title,url,source,published)
        }.getOrNull() }.distinctBy { it.url }.take(12)
        require(items.isNotEmpty()) { "Aucun titre lisible" }
        return NewsFeed(feedUrl,items,now)
    }
    fun fetch(url: String) = parse(PublicHttp.text(url,524288),url)
}
