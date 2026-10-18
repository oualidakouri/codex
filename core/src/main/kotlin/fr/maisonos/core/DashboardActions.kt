package fr.maisonos.core

import kotlinx.serialization.Serializable

/** Navigation wraps only through files actually announced by the selected recitation. */
fun QuranCatalog.step(settings: QuranSettings, direction: Int, reciter: Boolean = false): QuranSettings {
    require(direction == -1 || direction == 1)
    val available = recitations.filter { it.surahs.isNotEmpty() }
    if (available.isEmpty()) return settings
    val index = available.indexOfFirst { it.reciterId == settings.reciterId && it.moshafId == settings.moshafId }
    val people = available.distinctBy { it.reciterId }
    val personIndex = people.indexOfFirst { it.reciterId == settings.reciterId }
    val selected = if (reciter) people[Math.floorMod(if (personIndex < 0) 0 else personIndex + direction, people.size)]
        else available.getOrElse(index) { available.first() }
    val numbers = selected.surahs.distinct().sorted()
    val number = if (reciter || settings.custom || index < 0) settings.surah.takeIf { it in numbers } ?: numbers.first()
        else numbers[Math.floorMod(numbers.indexOf(settings.surah).let { if (it < 0) 0 else it + direction }, numbers.size)]
    return settings.copy(custom = false, reciterId = selected.reciterId, moshafId = selected.moshafId,
        reciter = selected.name, server = selected.server, surah = number, surahName = surahs.firstOrNull { it.id == number }?.name.orEmpty())
}

@Serializable data class PumpkinSettings(val enabled: Boolean = false, val idleSeconds: Int = 30, val durationSeconds: Int = 60) {
    fun validate() { require(idleSeconds in 5..3600 && durationSeconds in 10..300) }
}
/** Coordinates in upright front-camera image. Mirror X once to follow the visitor on the screen. */
data class FaceGaze(val x: Float = 0f, val y: Float = 0f, val detected: Boolean = false) {
    companion object {
        fun fromCamera(x: Float, y: Float) = if (!x.isFinite() || !y.isFinite()) FaceGaze()
            else FaceGaze((1f - 2f*x).coerceIn(-1f,1f), (1f - 2f*y).coerceIn(-1f,1f), true)
    }
}
object PumpkinSchedule {
    fun due(settings: PumpkinSettings, idleMs: Long, foreground: Boolean, blocked: Boolean): Boolean =
        settings.enabled && foreground && !blocked && idleMs >= settings.idleSeconds * 1000L
}
