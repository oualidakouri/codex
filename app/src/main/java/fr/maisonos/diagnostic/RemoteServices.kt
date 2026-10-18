package fr.maisonos.diagnostic

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import fr.maisonos.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

 data class WeatherUi(val reading: WeatherReading? = null, val loading: Boolean = false, val searching: Boolean = false,
    val cities: List<WeatherCity> = emptyList(), val error: String = "")
class WeatherController(private val context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("weather-cache", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(WeatherUi())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private var lastAttempt = 0L
    private var lastSettings: WeatherSettings? = null
    init { _state.update { it.copy(reading = runCatching { Json.decodeFromString<WeatherReading>(prefs.getString("reading", "").orEmpty()) }.getOrNull()) } }
    fun refresh(settings: WeatherSettings, force: Boolean = false) {
        if (!settings.enabled || !settings.configured || job?.isActive == true) return
        val changed = lastSettings != settings
        if (!force && !changed && System.currentTimeMillis() - lastAttempt < 1800000) return
        lastAttempt = System.currentTimeMillis(); lastSettings = settings
        job = scope.launch {
            _state.update { it.copy(loading = true, error = "", reading = it.reading?.takeIf { r -> r.latitude == settings.latitude && r.longitude == settings.longitude }) }
            try {
                val reading = withContext(Dispatchers.IO) { WeatherApi.current(settings) }
                prefs.edit().putString("reading", Json.encodeToString(reading)).apply()
                _state.update { it.copy(reading = reading) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(error = "Météo indisponible. Vérifier Internet ; la dernière mesure de cette ville reste affichée avec sa date.") } }
            finally { _state.update { it.copy(loading = false) } }
        }
    }
    fun search(name: String) { if (_state.value.searching) return
        scope.launch {
            _state.update { it.copy(searching = true, error = "", cities = emptyList()) }
            try {
                val cities = withContext(Dispatchers.IO) { WeatherApi.search(name) }
                _state.update { it.copy(cities = cities, error = if (cities.isEmpty()) "Aucune ville trouvée. Préciser le nom." else "") }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(error = "Recherche impossible. Vérifier Internet ou utiliser la ville configurée pour la salat.") } }
            finally { _state.update { it.copy(searching = false) } }
        }
    }
}

data class UpdateUi(val busy: Boolean = false, val message: String = "", val release: AppRelease? = null,
    val progress: Int = 0, val ready: File? = null, val prompt: Boolean = false)
class AppUpdates(private val context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(UpdateUi())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private var dismissed: Long? = null
    private var lastAttempt = 0L
    private var lastSource = ""
    private fun available(release: AppRelease) = release.versionCode > BuildConfig.VERSION_CODE && release.minSdk <= Build.VERSION.SDK_INT
    fun check(settings: UpdateSettings, force: Boolean = false) {
        if (job?.isActive == true || (!force && !settings.enabled)) return
        // Check on each fresh launch. Only debounce rapid onResume calls within this process.
        if (!force && lastSource == settings.manifestUrl && System.currentTimeMillis() - lastAttempt < 60000) {
            val cached = runCatching { AppRelease.parse(prefs.getString("manifest", "").orEmpty()) }.getOrNull()
            if (cached != null && available(cached)) _state.update { it.copy(release = cached, prompt = dismissed != cached.versionCode) }
            return
        }
        lastAttempt = System.currentTimeMillis(); lastSource = settings.manifestUrl
        job = scope.launch {
            _state.update { it.copy(busy = true, message = "Vérification de la version…") }
            try {
                settings.validate()
                val text = withContext(Dispatchers.IO) { PublicHttp.text(settings.manifestUrl, 65536) }
                val release = AppRelease.parse(text)
                prefs.edit().putString("manifest", text).putString("source", settings.manifestUrl).putLong("checked", System.currentTimeMillis()).apply()
                _state.update { it.copy(release = release.takeIf(::available), ready = null,
                    prompt = available(release) && dismissed != release.versionCode,
                    message = when { release.versionCode <= BuildConfig.VERSION_CODE -> "Maison OS est à jour (${BuildConfig.VERSION_NAME})."; release.minSdk > Build.VERSION.SDK_INT -> "Cette version nécessite Android API ${release.minSdk}."; else -> "Maison OS ${release.versionName} disponible." }) }
            } catch (e: CancellationException) { throw e }
            catch (e: PublicHttpFailure) { _state.update { it.copy(message = "Vérification impossible (HTTP ${e.status}). Si le dépôt est privé, renseigner une adresse HTTPS de publication accessible à la tablette.") } }
            catch (_: Exception) { _state.update { it.copy(message = "Vérification impossible. Vérifier Internet et l’URL du manifeste ; les fonctions locales restent disponibles.") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun dismiss() { dismissed = _state.value.release?.versionCode; _state.update { it.copy(prompt = false) } }
    fun show() { if (_state.value.release != null) _state.update { it.copy(prompt = true) } }
    fun download() {
        val release = _state.value.release ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            val folder = File(context.cacheDir, "updates").apply { mkdirs() }
            val temporary = File(folder, "maison-update.part")
            _state.update { it.copy(busy = true, progress = 0, message = "Téléchargement de la mise à jour…", ready = null) }
            try {
                val ready = withContext(Dispatchers.IO) {
                    PublicHttp.download(release.apkUrl, temporary, release.size, release.sha256) { bytes ->
                        val percent = (bytes * 100 / release.size).toInt()
                        if (percent != _state.value.progress) _state.update { it.copy(progress = percent) }
                    }
                    verifyArchive(temporary, release)
                    val result = File(folder, "maison-${release.versionCode}.apk")
                    if (result.exists()) result.delete()
                    check(temporary.renameTo(result)); result
                }
                _state.update { it.copy(ready = ready, message = "APK vérifiée : empreinte, application et signature. Prête à installer.") }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { temporary.delete(); _state.update { it.copy(message = "Mise à jour refusée ou interrompue : téléchargement, empreinte, version ou signature non valides. L’application installée est conservée.") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    @Suppress("DEPRECATION")
    internal fun verifyArchive(file: File, release: AppRelease) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = context.packageManager.getPackageArchiveInfo(file.path, flags) ?: error("APK invalide")
        val current = context.packageManager.getPackageInfo(context.packageName, flags)
        fun signatures(info: android.content.pm.PackageInfo): Set<String> {
            val values = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return values.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
        }
        val version = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        require(archive.packageName == context.packageName && archive.packageName == release.packageName)
        require(version == release.versionCode && version > BuildConfig.VERSION_CODE)
        require(signatures(archive).isNotEmpty() && signatures(archive) == signatures(current))
    }
    fun install() {
        val file = _state.value.ready ?: return
        try {
            if (!context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                _state.update { it.copy(message = "Autoriser cette source dans Android, puis revenir et toucher Installer.") }
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { _state.update { it.copy(message = "Installateur Android indisponible. Réessayer ou télécharger l’APK depuis GitHub.") } }
    }
}
