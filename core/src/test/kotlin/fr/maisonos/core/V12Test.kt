package fr.maisonos.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.Locale

class V12Test {
    @Test fun threeFutureWeatherDaysRespectCityTimezoneAndDst() {
        val zone = ZoneId.of("Europe/Paris")
        val first = LocalDate.of(2026,10,24)
        val times = (0..3).map { first.plusDays(it.toLong()).atStartOfDay(zone).toEpochSecond() }
        assertEquals(25 * 3600L,times[2] - times[1])
        val observed = first.atTime(12,0).atZone(zone).toInstant().toEpochMilli()
        val json = """{"timezone":"Europe/Paris","current":{"time":${observed/1000},"temperature_2m":15,"apparent_temperature":14,"relative_humidity_2m":60,"wind_speed_10m":12,"weather_code":2},"daily":{"time":$times,"temperature_2m_min":[10,3,16,11],"temperature_2m_max":[18,12,30,21],"apparent_temperature_min":[9,1,17,10],"weather_code":[2,61,0,3],"precipitation_probability_max":[20,75,0,null],"wind_speed_10m_max":[12,48,8,11]}}"""
        val reading = WeatherReading.parse(json,WeatherSettings(city = "Fixture",latitude = 48.8,longitude = 2.3),observed)
        assertEquals(listOf("2026-10-25","2026-10-26","2026-10-27"),reading.forecast.map { it.date })
        assertEquals(75,reading.forecast[0].rainChance)
        assertTrue(reading.forecast[0].clothing.contains("Manteau"));assertTrue(reading.forecast[0].clothing.contains("pluie"));assertTrue(reading.forecast[0].clothing.contains("coupe-vent"))
        assertEquals("Tenue légère",reading.forecast[1].clothing)
        assertNull(reading.forecast[2].rainChance)
    }
    @Test fun unavailableForecastsStayEmptyAndInvalidDaysAreDropped() {
        val day = LocalDate.of(2026,10,18).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        val json = kotlinx.serialization.json.Json.parseToJsonElement("""{"time":[$day,${day+86400}],"temperature_2m_min":[10,999],"temperature_2m_max":[20,10],"weather_code":[0,0]}""").let { it as kotlinx.serialization.json.JsonObject }
        assertTrue(WeatherForecast.parse(json,day*1000,"UTC").isEmpty())
    }
    @Test fun rssPreservesTitlesSourcesDatesAndDropsUnsafeOrDuplicateLinks() {
        val xml = """<rss version="2.0"><channel><title>Source fixture</title><item><title><![CDATA[Une <b>actualité</b> locale]]></title><link>https://example.com/a</link><source>Éditeur local</source><pubDate>Sun, 18 Oct 2026 12:00:00 GMT</pubDate></item><item><title>Doublon</title><link>https://example.com/a</link></item><item><title>Refusé</title><link>javascript:alert(1)</link></item></channel></rss>"""
        val result = NewsParser.parse(xml,"https://example.com/rss",1234)
        assertEquals(1,result.items.size);assertEquals("Une actualité locale",result.items.single().title)
        assertEquals("Éditeur local",result.items.single().source)
        assertEquals(Instant.parse("2026-10-18T12:00:00Z").toEpochMilli(),result.items.single().publishedAt)
        assertEquals(1234,result.fetchedAt)
    }
    @Test fun atomLinksWorkAndRssCannotExpandExternalEntities() {
        val atom = """<feed xmlns="http://www.w3.org/2005/Atom"><title>Feed</title><entry><title>Fixture</title><link rel="alternate" href="https://example.com/story"/><updated>2026-10-18T10:00:00Z</updated></entry></feed>"""
        assertEquals("https://example.com/story",NewsParser.parse(atom,"https://example.com/feed").items.single().url)
        assertThrows(IllegalArgumentException::class.java) { NewsParser.parse("<!DOCTYPE rss [<!ENTITY test SYSTEM 'file:///etc/passwd'>]><rss>&test;</rss>","https://example.com/feed") }
    }
    @Test fun localNewsDoesNotGuessAnUnknownCity() {
        assertNull(NewsSettings().localFeed(""));assertNull(NewsSettings().localFeed("Ma position actuelle"))
        val feed = NewsSettings().localFeed("Lyon, France")!!
        assertTrue(feed.contains("Lyon"));assertFalse(feed.contains("France"))
        assertEquals("https://example.com/local.rss",NewsSettings(localUrl = "https://example.com/local.rss").localFeed(""))
    }
    @Test fun quranCatalogKeepsActualAvailableSurahsAndRejectsUnsafeServers() {
        val reciters = """{"reciters":[{"id":5,"name":"Récitant fixture","moshaf":[{"id":8,"name":"Lecture fixture","server":"https://example.com/reader/","surah_list":"1,2,114,999"},{"id":9,"name":"Invalide","server":"http://example.com/","surah_list":"1"}]}]}"""
        val surahs = """{"suwar":[{"id":1,"name":"الفاتحة"},{"id":114,"name":"الناس"}]}"""
        val catalog = QuranApi.parse(reciters,surahs,1000)
        assertEquals(1,catalog.recitations.size);assertEquals(listOf(1,2,114),catalog.recitations.single().surahs)
        assertEquals("الفاتحة",catalog.surahs.first().name)
    }
    @Test fun quranUsesAsciiSurahNumbersAndExportsExcludePrivateStreamUrls() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar"))
            val q = QuranSettings(custom = false,reciterId = 1,moshafId = 1,server = "https://example.com/reader/",surah = 2)
            assertEquals("https://example.com/reader/002.mp3",q.playbackUrl())
            val settings = HomeConfig(quran = QuranSettings(url = "http://192.168.1.2/audio.mp3?token=private"))
            assertEquals("",settings.exportSafe().quran.url)
            assertThrows(IllegalArgumentException::class.java) { QuranSettings(url = "file:///etc/passwd").playbackUrl() }
            assertThrows(IllegalArgumentException::class.java) { q.copy(surah = 115).playbackUrl() }
        } finally { Locale.setDefault(old) }
    }
    @Test fun newWidgetsAreAddedWithoutRetiredSwitchWidgetsOrResettingOtherPages() {
        val profile = LayoutProfile(pages = 2,landscape = listOf(HomeWidget(kind = WidgetKind.CLOCK,page = 1),HomeWidget(kind = WidgetKind.SWITCHES)))
        val original = HomeConfig(profiles = listOf(profile),lastPage = "Interrupteurs")
        val next = original.withV12Widgets();next.validate()
        assertEquals("Accueil",next.lastPage)
        assertTrue(next.profile().landscape.none { it.kind == WidgetKind.SWITCHES })
        assertEquals(1,next.profile().landscape.first { it.kind == WidgetKind.CLOCK }.page)
        assertTrue(next.profile().landscape.any { it.kind == WidgetKind.QURAN && it.page == 0 })
        assertTrue(next.profile().landscape.any { it.kind == WidgetKind.NEWS && it.page == 0 })
        assertEquals(next,next.withV12Widgets())
    }
}
