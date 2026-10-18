package fr.maisonos.core

import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.util.UUID

@Serializable
enum class WidgetKind(val label: String) { CLOCK("Horloge et dates"), PRAYERS("Horaires de salat"), NEXT_PRAYER("Prochaine salat"), DUA("Doua du jour"), CAMERA("Caméra en direct"), TABLET("Tablette et Wi-Fi"), MAILBOX("Courrier · simulation"), SHORTCUTS("Accès rapides") }

@Serializable
data class HomeWidget(val id: String = UUID.randomUUID().toString(), val kind: WidgetKind, val x: Int = 0, val y: Int = 0,
    val w: Int = 3, val h: Int = 2, val page: Int = 0, val cameraId: String = "", val opacity: Float = 0.94f)

@Serializable
data class LayoutProfile(val id: String = UUID.randomUUID().toString(), val name: String = "Ma maison", val pages: Int = 1,
    val landscape: List<HomeWidget> = defaultWidgets(), val portrait: List<HomeWidget> = landscape.map { it.copy(w = minOf(it.w, 4)) }.let { LayoutEngine.pack(it, 4) })

fun defaultWidgets() = listOf(
    HomeWidget(kind = WidgetKind.CLOCK, x = 0, y = 0, w = 3, h = 2),
    HomeWidget(kind = WidgetKind.NEXT_PRAYER, x = 3, y = 0, w = 3, h = 2),
    HomeWidget(kind = WidgetKind.PRAYERS, x = 0, y = 2, w = 3, h = 2),
    HomeWidget(kind = WidgetKind.DUA, x = 3, y = 2, w = 3, h = 3),
    HomeWidget(kind = WidgetKind.TABLET, x = 0, y = 4, w = 3, h = 1),
    HomeWidget(kind = WidgetKind.SHORTCUTS, x = 0, y = 5, w = 6, h = 1),
)

@Serializable
data class PrayerSettings(
    val city: String = "", val latitude: Double? = null, val longitude: Double? = null,
    val zone: String = ZoneId.systemDefault().id, val method: String = "MUSLIM_WORLD_LEAGUE", val hanafi: Boolean = false,
    val highLatitude: String = "TWILIGHT_ANGLE", val fajrAngle: Double = 18.0, val ishaAngle: Double = 17.0,
    val adjustments: Map<String, Int> = emptyMap(), val enabled: Set<String> = emptySet(),
    val audioId: String = "", val fajrAudioId: String = "", val volume: Float = 0.8f,
    val quietStart: Int = -1, val quietEnd: Int = -1, val hijriAdjustment: Int = 0,
) {
    val configured get() = latitude != null && longitude != null
    fun validate() {
        require((latitude == null) == (longitude == null))
        latitude?.let { require(it.isFinite() && it in -89.0..89.0) }
        longitude?.let { require(it.isFinite() && it in -180.0..180.0) }
        ZoneId.of(zone)
        require(method in PrayerEngine.methods && highLatitude in setOf("TWILIGHT_ANGLE", "SEVENTH_OF_THE_NIGHT", "MIDDLE_OF_THE_NIGHT"))
        require(fajrAngle in 1.0..30.0 && ishaAngle in 1.0..30.0)
        require(adjustments.all { it.key in PrayerName.entries.map(PrayerName::name) && it.value in -120..120 })
        require(enabled.all { it in PrayerName.entries.map(PrayerName::name) })
        require(volume in 0f..1f && hijriAdjustment in -2..2 && quietStart in -1..23 && quietEnd in -1..23)
    }
}

@Serializable enum class MediaKind { AUDIO, VIDEO, IMAGE }
@Serializable data class LocalMedia(val id: String = UUID.randomUUID().toString(), val name: String, val kind: MediaKind, val bytes: Long)
@Serializable data class LocalRule(val id: String = UUID.randomUUID().toString(), val name: String = "Tu as du courrier !", val trigger: String = "mailbox", val enabled: Boolean = true,
    val mediaId: String = "", val durationSeconds: Int = 8, val cooldownSeconds: Int = 30, val delaySeconds: Int = 0, val volume: Float = 0.8f)

@Serializable data class HomeConfig(
    val cameras: List<CameraConfig> = emptyList(), val profiles: List<LayoutProfile> = listOf(LayoutProfile()), val activeProfile: String = "",
    val prayer: PrayerSettings = PrayerSettings(), val media: List<LocalMedia> = emptyList(), val rules: List<LocalRule> = listOf(LocalRule()),
    val favorites: Set<String> = emptySet(), val duaHistory: List<String> = emptyList(), val duaOffset: Int = 0,
    val dark: Boolean = true, val accent: String = "mint", val wallpaperId: String = "", val maxStreams: Int = 1,
    val liveDashboard: Boolean = true, val presenceOnLaunch: Boolean = false, val lastPage: String = "Accueil",
) {
    fun validate() {
        prayer.validate(); cameras.forEach { it.validate() }
        require(cameras.distinctBy { it.id }.size == cameras.size)
        require(profiles.isNotEmpty() && profiles.all { it.name.length in 1..80 && it.pages in 1..20 })
        profiles.forEach { p ->
            for ((columns, widgets) in listOf(6 to p.landscape, 4 to p.portrait)) {
                require(widgets.map { it.id }.distinct().size == widgets.size)
                widgets.forEach { require(it.x >= 0 && it.y in 0..1000 && it.w in 1..columns && it.x + it.w <= columns && it.h in 1..6 && it.page in 0 until p.pages && it.opacity in 0.3f..1f) }
            }
        }
        require(maxStreams in 1..2 && accent in setOf("mint", "blue", "amber"))
        require(media.all { runCatching { UUID.fromString(it.id) }.isSuccess && it.name.length <= 180 && it.bytes in 0..104857600 })
        require(rules.all { it.durationSeconds in 1..300 && it.cooldownSeconds in 0..86400 && it.delaySeconds in 0..300 && it.volume in 0f..1f && it.name.length in 1..100 && it.trigger in setOf("mailbox", "presence", "manual") })
    }
    fun profile() = profiles.firstOrNull { it.id == activeProfile } ?: profiles.first()
    fun exportSafe() = copy(cameras = cameras.map(CameraConfig::exportSafe))
}

object LayoutEngine {
    fun overlaps(a: HomeWidget, b: HomeWidget) = a.page == b.page && a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y
    fun pack(widgets: List<HomeWidget>, columns: Int): List<HomeWidget> {
        require(columns in 1..12)
        val placed = mutableListOf<HomeWidget>()
        widgets.forEach { original ->
            var w = original.copy(w = original.w.coerceIn(1, columns), h = original.h.coerceIn(1, 6), x = original.x.coerceIn(0, columns - original.w.coerceIn(1, columns)), y = original.y.coerceAtLeast(0))
            if (placed.any { overlaps(it, w) }) {
                var found = false
                for (y in 0..1000) {
                    for (x in 0..columns - w.w) {
                        val candidate = w.copy(x = x, y = y)
                        if (placed.none { overlaps(it, candidate) }) { w = candidate; found = true; break }
                    }
                    if (found) break
                }
                check(found) { "Grille pleine" }
            }
            placed += w
        }
        return placed
    }
    fun move(widgets: List<HomeWidget>, id: String, x: Int, y: Int, width: Int? = null, height: Int? = null, columns: Int): List<HomeWidget> {
        val old = widgets.first { it.id == id }
        val w = (width ?: old.w).coerceIn(1, columns)
        val target = old.copy(x = x.coerceIn(0, columns - w), y = y.coerceIn(0, 990), w = w, h = (height ?: old.h).coerceIn(1, 6))
        val packed = pack(listOf(target) + widgets.filterNot { it.id == id }, columns).associateBy { it.id }
        return widgets.map { packed.getValue(it.id) }
    }
}
