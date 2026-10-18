package fr.maisonos.core

import org.junit.Test
import org.junit.Assert.*

class DashboardActionsTest {
    private val catalog = QuranCatalog(listOf(
        QuranRecitation(1,11,"A","Hafs","https://example.org/a",listOf(1,3,114)),
        QuranRecitation(2,22,"B","Hafs","https://example.org/b",listOf(2,3))
    ), listOf(QuranSurah(1,"الفاتحة"),QuranSurah(3,"آل عمران"),QuranSurah(114,"الناس")))
    private val selected = QuranSettings(custom = false,reciterId = 1,moshafId = 11,reciter = "A",server = "https://example.org/a",surah = 1)
    @Test fun surahNavigationSkipsUnavailableFilesAndWrapsBothWays() {
        assertEquals(3,catalog.step(selected,1).surah)
        assertEquals(114,catalog.step(selected,-1).surah)
        assertEquals(1,catalog.step(selected.copy(surah = 114),1).surah)
        assertEquals("https://example.org/a/003.mp3",catalog.step(selected,1).playbackUrl())
    }
    @Test fun reciterNavigationKeepsSurahWhenAvailableAndChoosesValidFallback() {
        assertEquals(3,catalog.step(selected.copy(surah = 3),1,true).surah)
        val second = catalog.step(selected,1,true)
        assertEquals(2,second.reciterId);assertEquals(2,second.surah)
        assertEquals(1,catalog.step(second,1,true).reciterId)
        assertEquals(2,catalog.step(selected,-1,true).reciterId)
    }
    @Test fun reciterButtonSkipsAlternateReadingsOfTheSamePerson() {
        val multiple = catalog.copy(recitations = listOf(catalog.recitations[0],catalog.recitations[0].copy(moshafId = 12),catalog.recitations[1]))
        assertEquals(2,multiple.step(selected,1,true).reciterId)
        assertEquals(2,multiple.step(selected.copy(moshafId = 12),1,true).reciterId)
    }
    @Test fun customUrlAndMissingCatalogSelectionRecoverWithoutInventingFiles() {
        assertFalse(catalog.step(QuranSettings(url = "https://example.org/live"),1).custom)
        assertEquals(selected,QuranCatalog().step(selected,1))
        assertThrows(IllegalArgumentException::class.java) { catalog.step(selected,0) }
    }
    @Test fun gazeMirrorsOnceClampsAndRejectsInvalidInput() {
        assertEquals(FaceGaze(1f,1f,true),FaceGaze.fromCamera(0f,0f))
        assertEquals(FaceGaze(-1f,-1f,true),FaceGaze.fromCamera(1f,1f))
        assertEquals(FaceGaze(0f,0f,true),FaceGaze.fromCamera(0.5f,0.5f))
        assertEquals(FaceGaze(),FaceGaze.fromCamera(Float.NaN,0.2f))
    }
    @Test fun automaticPumpkinWaitsForDelayAndNeverStartsWhenBlockedOrBackground() {
        val settings = PumpkinSettings(enabled = true,idleSeconds = 30)
        assertFalse(PumpkinSchedule.due(settings,29999,true,false))
        assertTrue(PumpkinSchedule.due(settings,30000,true,false))
        assertFalse(PumpkinSchedule.due(settings,90000,false,false))
        assertFalse(PumpkinSchedule.due(settings,90000,true,true))
        assertFalse(PumpkinSchedule.due(settings.copy(enabled = false),90000,true,false))
        assertThrows(IllegalArgumentException::class.java) { settings.copy(durationSeconds = 3600).validate() }
    }
    @Test fun largerAudioWidgetPreservesIdsPagesAndNonoverlappingLayouts() {
        val clock = HomeWidget(id = "clock",kind = WidgetKind.CLOCK)
        val audio = HomeWidget(id = "audio",kind = WidgetKind.QURAN,x = 3,h = 2)
        val news = HomeWidget(id = "news",kind = WidgetKind.NEWS,x = 3,y = 2)
        val home = HomeConfig(profiles = listOf(LayoutProfile(landscape = listOf(clock,audio,news))))
        val updated = home.withV13Widgets()
        assertEquals(home.profile().landscape.map { it.id },updated.profile().landscape.map { it.id })
        assertEquals(4,updated.profile().landscape.first { it.id == "audio" }.h)
        val widgets = updated.profile().landscape
        assertTrue(widgets.indices.all { i -> widgets.indices.all { j -> i == j || !LayoutEngine.overlaps(widgets[i],widgets[j]) } })
        updated.validate()
    }
}
