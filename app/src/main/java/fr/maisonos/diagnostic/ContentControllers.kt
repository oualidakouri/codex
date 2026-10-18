package fr.maisonos.diagnostic

import android.content.Context
import fr.maisonos.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

 data class QuranCatalogUi(val catalog: QuranCatalog = QuranCatalog(), val busy: Boolean = false, val error: String = "")
class QuranCatalogController(context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("quran-catalog",Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(QuranCatalogUi())
    val state = _state.asStateFlow()
    init { _state.value = QuranCatalogUi(catalog = runCatching { Json.decodeFromString<QuranCatalog>(prefs.getString("catalog","").orEmpty()) }.getOrElse { context.assets.open("quran-catalog.json").bufferedReader().use { Json.decodeFromString<QuranCatalog>(it.readText()) } }) }
    fun refresh() { if (_state.value.busy) return
        scope.launch {
            _state.update { it.copy(busy = true,error = "") }
            try {
                val catalog = withContext(Dispatchers.IO) { QuranApi.fetch(_state.value.catalog.surahs) }
                prefs.edit().putString("catalog",Json.encodeToString(catalog)).apply()
                _state.update { it.copy(catalog = catalog) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(error = "Catalogue MP3Quran indisponible. Vérifier Internet ; un catalogue déjà chargé reste disponible, ou utiliser votre URL personnelle.") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
}
 data class NewsUi(val france: NewsFeed? = null, val local: NewsFeed? = null, val busy: Boolean = false, val error: String = "")
class NewsController(context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("news-cache",Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(NewsUi())
    val state = _state.asStateFlow()
    private var lastKey = ""
    private var lastAttempt = 0L
    private var job: Job? = null
    init { _state.value = NewsUi(france = read("france"), local = read("local")) }
    private fun read(key: String) = runCatching { Json.decodeFromString<NewsFeed>(prefs.getString(key,"").orEmpty()) }.getOrNull()
    fun refresh(settings: NewsSettings, city: String, force: Boolean = false) {
        if (!settings.enabled || job?.isActive == true) return
        val localUrl = settings.localFeed(city)
        val key = "${settings.franceUrl}|${localUrl.orEmpty()}"
        if (!force && key == lastKey && System.currentTimeMillis() - lastAttempt < 900000) return
        lastKey = key; lastAttempt = System.currentTimeMillis()
        job = scope.launch {
            _state.update { it.copy(busy = true,error = "",france = it.france?.takeIf { f -> f.url == settings.franceUrl },local = it.local?.takeIf { f -> f.url == localUrl }) }
            try {
                val results = listOf("france" to settings.franceUrl,"local" to localUrl).filter { it.second != null }.map { (section,url) -> async(Dispatchers.IO) {
                    section to runCatching { NewsParser.fetch(url!!) }
                } }.awaitAll()
                var failed = false
                for ((section,result) in results) {
                    val feed = result.getOrNull()
                    if (feed == null) { failed = true; continue }
                    prefs.edit().putString(section,Json.encodeToString(feed)).apply()
                    _state.update { if (section == "france") it.copy(france = feed) else it.copy(local = feed) }
                }
                if (failed) _state.update { it.copy(error = "Certains flux sont indisponibles. Les derniers titres disponibles restent datés ; vérifier Internet ou changer de source RSS.") }
            } finally { _state.update { it.copy(busy = false) } }
        }
    }
}
