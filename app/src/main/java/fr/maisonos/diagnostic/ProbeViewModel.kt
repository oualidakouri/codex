package fr.maisonos.diagnostic

import android.app.Application
import android.net.wifi.WifiManager
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.maisonos.core.*
import fr.maisonos.core.vendor.ptz.PtzCommand
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalTime
import java.time.Instant
import android.net.Uri
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.net.SocketFactory

data class ProbeState(
    val config: ProbeConfig = ProbeConfig(),
    val busy: Boolean = false,
    val simulation: Boolean = false,
    val sleeping: Boolean = false,
    val pumpkinActive: Boolean = false,
    val pumpkinSimulated: Boolean = false,
    val pumpkinCameraReady: Boolean = false,
    val pumpkinCameraError: Boolean = false,
    val pumpkinFaceFrames: Long = 0,
    val gaze: FaceGaze = FaceGaze(),
    val cameraEnabled: Boolean = false,
    val video: Boolean = false,
    val foreground: Boolean = true,
    val discovered: List<FoundCamera> = emptyList(),
    val report: String = "Lecture du matériel…",
    val resources: Resources? = null,
    val samples: List<Resources> = emptyList(),
    val wifi: String = "Non observé",
    val motionScore: Double = 0.0,
    val motionFrames: Long = 0,
    val motionEvents: Long = 0,
    val animation: Boolean = false,
    val eventRule: LocalRule? = null,
    val eventSimulated: Boolean = true,
    val onvifPtz: Boolean = false,
    val dvripConnected: Boolean = false,
    val diagnostics: Map<String, Diagnostic> = listOf("Tablette", "Wi-Fi / découverte", "DVRIP", "ONVIF", "RTSP", "PTZ", "Présence", "Veille", "Performance", "Audio / adhan", "Calcul salat", "Capteurs", "Animations", "Sans Internet").associateWith { Diagnostic(it) },
    val logs: List<String> = emptyList(),
    val cameraPorts: List<CameraPortResult> = emptyList(),
)

class ProbeViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ConfigStore(app)
    private val _state = MutableStateFlow(ProbeState())
    val state = _state.asStateFlow()
    val weather = WeatherController(app, viewModelScope)
    val quranCatalog = QuranCatalogController(app, viewModelScope)
    val news = NewsController(app, viewModelScope)
    val updates = AppUpdates(app, viewModelScope)
    private val history = BoundedLog()
    private val simulation = SimulationConnector()
    private var dvrip: DvripClient? = null
    private var onvif: OnvifClient? = null
    private var ptzJob: Job? = null
    private var keepAlive: Job? = null
    private var operation: Job? = null
    private val loginLimiter = LoginLimiter()
    private val configMutex = Mutex()
    private val ruleEngine = RuleEngine()
    private var eventJob: Job? = null
    private var pumpkinJob: Job? = null
    private var lastFaceAt = 0L
    private var lastInteraction = SystemClock.elapsedRealtime()
    private val started = SystemClock.elapsedRealtime()
    init {
        viewModelScope.launch {
            simulation.events.collect { event ->
                dismissPumpkin()
                record("Capteurs", Evidence.SIMULATED, "Événement local ${event.type} : boîte aux lettres. Aucun capteur réel connecté.")
                val rule = ruleEngine.match(_state.value.config.home.rules, "mailbox", SystemClock.elapsedRealtime()).firstOrNull()
                if (rule != null) playRule(rule, true)
                else _state.update { it.copy(animation = true, sleeping = false, eventRule = null) }
                record("Animations", Evidence.SIMULATED, "Animation Compose de démonstration. Import vidéo prévu dans une phase ultérieure.")
                if (rule == null) launch { delay(4000); dismissAnimation() }
            }
        }
        viewModelScope.launch {
            try {
                val config = withContext(Dispatchers.IO) { store.load() }
                _state.update { it.copy(config = config) }
            } catch (_: Exception) { log("Configuration illisible : aucune donnée écrasée. Réenregistrer seulement après vérification.") }
            val report = withContext(Dispatchers.Default) { DeviceDiagnostics.report(app) }
            _state.update { it.copy(report = report) }
            record("Tablette", Evidence.OBSERVED, "Caractéristiques fournies par Android ; aucune valeur commerciale supposée.")
            reschedulePrayers()
            checkUpdates()
            record("Audio / adhan", Evidence.NOT_TESTED, "Adhan local via AlarmManager et fichier personnel. Vérifier autorisations et tester l’écoute dans Salat.")
            while (isActive) {
                val r = withContext(Dispatchers.IO) { DeviceDiagnostics.sample(app, started) }
                val wifi = DeviceDiagnostics.wifi(app)
                _state.update { s -> s.copy(resources = r, samples = (s.samples + r).takeLast(120), wifi = wifi?.let { "${it.address}/${it.prefix} · Wi-Fi présent (Internet indépendant)" } ?: "Wi-Fi indisponible") }
                if (r.protect && _state.value.pumpkinActive) dismissPumpkin()
                if (r.protect && (_state.value.video || _state.value.cameraEnabled)) {
                    stopVideo(); enablePresence(false); stopPtz()
                    record("Performance", Evidence.OBSERVED, "Protection active : vidéo et analyse caméra arrêtées (température système ou batterie faible).")
                }
                val current = _state.value
                val idle = SystemClock.elapsedRealtime() - lastInteraction
                val pumpkin = current.config.home.pumpkin
                val pumpkinWaiting = pumpkin.enabled && current.foreground && !current.simulation && !current.animation && !AdhanPlayback.active.value && current.resources?.protect != true
                if (PumpkinSchedule.due(pumpkin,idle,current.foreground,!pumpkinWaiting || current.pumpkinActive || current.busy)) startPumpkin()
                if (!current.sleeping && current.foreground && !current.pumpkinActive && !pumpkinWaiting && idle > current.config.idleSeconds * 1000L) sleep()
                if (_state.value.foreground && !_state.value.sleeping) {
                    val home = _state.value.config.home
                    weather.refresh(home.weather); news.refresh(home.news, home.weather.city.ifBlank { home.prayer.city })
                }
                delay(5000)
            }
        }
    }
    fun log(message: String) {
        history.add("${LocalTime.now().withNano(0)} · $message")
        _state.update { it.copy(logs = history.snapshot()) }
    }
    fun record(key: String, evidence: Evidence, detail: String) {
        _state.update { it.copy(diagnostics = it.diagnostics + (key to Diagnostic(key, evidence, detail))) }
        log("$key · $detail")
    }
    private fun task(key: String, block: suspend () -> Unit) {
        if (_state.value.busy || !_state.value.foreground) return
        if (_state.value.simulation) { record(key, Evidence.UNAVAILABLE, "Désactiver le mode simulation pour contacter le matériel."); return }
        _state.update { it.copy(busy = true) }
        operation = viewModelScope.launch {
            record(key, Evidence.RUNNING, "Test en cours…")
            try { block() }
            catch (e: CancellationException) { record(key, Evidence.NOT_TESTED, "Test interrompu ; résultat non validé."); throw e }
            catch (e: Exception) {
                // Never log peer bodies, URIs or exception messages: they may contain credentials.
                record(key, Evidence.FAILED, CameraTroubleshooting.failure(e))
            } finally { _state.update { it.copy(busy = false) } }
        }
    }
    private fun sockets(): SocketFactory = DeviceDiagnostics.wifi(getApplication())?.network?.socketFactory ?: error("Wi-Fi absent")
    fun save(config: ProbeConfig) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        operation = viewModelScope.launch {
            try {
                disconnect()
                persist { config }
                log("Configuration chiffrée enregistrée sur la tablette.")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { log("Enregistrement refusé : vérifier adresse locale, ports et réglages.") }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    private suspend fun persist(transform: (ProbeConfig) -> ProbeConfig) = configMutex.withLock {
        val old = _state.value.config
        var next = transform(old).migrated()
        if (next.camera.host.isNotBlank()) {
            val cameras = next.home.cameras.toMutableList()
            val index = cameras.indexOfFirst { it.id == next.camera.id }
            if (index < 0) cameras += next.camera else cameras[index] = next.camera
            next = next.copy(home = next.home.copy(cameras = cameras))
        }
        if (next.home.cameras.any { candidate -> old.home.cameras.none { it.id == candidate.id } }) next = next.copy(home = next.home.withCameraTiles())
        withContext(Dispatchers.IO) { next.validate(); store.save(next) }
        _state.update { it.copy(config = next) }
        if (old.home.prayer != next.home.prayer) reschedulePrayers()
        if (old.home.weather != next.home.weather) weather.refresh(next.home.weather, true)
    }
    fun updateHome(transform: (HomeConfig) -> HomeConfig) { viewModelScope.launch {
        try { persist { old -> old.copy(home = transform(old.home)) } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { log("Réglage refusé : données invalides ou stockage indisponible.") }
    } }
    fun stepQuran(direction: Int, reciter: Boolean = false) { viewModelScope.launch {
        try {
            persist { old -> old.copy(home = old.home.copy(quran = quranCatalog.state.value.catalog.step(old.home.quran,direction,reciter))) }
            val audio = QuranPlayback.state.value
            if (audio.active && !AdhanPlayback.active.value) QuranPlayback.play(getApplication(),_state.value.config.home.quran)
            interact()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { log("Changement de récitation impossible : catalogue ou stockage indisponible.") }
    } }
    fun adjustQuranVolume(delta: Float) { viewModelScope.launch {
        try {
            persist { old -> old.copy(home = old.home.copy(quran = old.home.quran.copy(volume = (old.home.quran.volume + delta).coerceIn(0f,1f)))) }
            QuranPlayback.volume(getApplication(),_state.value.config.home.quran.volume)
            interact()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { log("Volume non enregistré.") }
    } }
    fun selectCamera(camera: CameraConfig) { _state.update { it.copy(cameraPorts = emptyList()) }; save(_state.value.config.copy(camera = camera)) }
    fun newCamera() { _state.update { it.copy(cameraPorts = emptyList()) }; save(_state.value.config.copy(camera = CameraConfig())) }
    fun checkCameraPorts() = task("Réseau caméra") {
        val camera = _state.value.config.camera
        val results = withContext(Dispatchers.IO) { CameraConnectivity(sockets()).inspect(camera) }
        _state.update { it.copy(cameraPorts = results) }
        record("Réseau caméra", Evidence.OBSERVED, "Ports testés sans authentification. Un port accessible ne valide ni son protocole ni le mot de passe. Le scan /24 ne teste pas ONVIF.")
    }
    fun checkUpdates(force: Boolean = false) { if (_state.value.report != "Lecture du matériel…") updates.check(_state.value.config.home.updates, force) }
    fun removeCamera(id: String) { viewModelScope.launch {
        try {
            disconnect(); persist { old ->
                val remaining = old.home.cameras.filterNot { it.id == id }
                old.copy(camera = if (old.camera.id == id) remaining.firstOrNull() ?: CameraConfig() else old.camera, home = old.home.copy(cameras = remaining))
            }
        } catch (_: Exception) { log("Suppression de caméra impossible.") }
    } }
    fun importMedia(uri: Uri, kind: MediaKind) { viewModelScope.launch {
        try {
            val media = withContext(Dispatchers.IO) { LocalFiles.import(getApplication(), uri, kind) }
            persist { it.copy(home = it.home.copy(media = it.home.media + media)) }
            log("Média importé et conservé localement.")
        } catch (_: Exception) { log("Import refusé : fichier compatible de 100 Mo maximum requis.") }
    } }
    suspend fun backup(uri: Uri) = withContext(Dispatchers.IO) { LocalFiles.backup(getApplication(), uri, _state.value.config) }
    fun restore(uri: Uri) { viewModelScope.launch {
        try {
            val restored = withContext(Dispatchers.IO) { LocalFiles.restore(getApplication(), uri) }
            disconnect(); persist { restored }; log("Sauvegarde restaurée avec médias. Ressaisir mots de passe et URL RTSP.")
        } catch (_: Exception) { log("Restauration refusée : archive invalide, trop volumineuse ou incompatible.") }
    } }
    fun reschedulePrayers() { viewModelScope.launch {
        try {
            val config = _state.value.config
            val planned = withContext(Dispatchers.IO) { AdhanScheduler.schedule(getApplication(), config) }
            record("Calcul salat", if (config.home.prayer.configured) Evidence.OBSERVED else Evidence.NOT_TESTED,
                if (config.home.prayer.configured) "Calcul local Adhan Kotlin actif ; ${planned.size} alarme(s) à venir. ${if (AdhanScheduler.exactAllowed(getApplication())) "Autorisation exacte disponible." else "Autorisation exacte manquante : horaires d’alarme approximatifs."}" else "Choisir une ville ou saisir les coordonnées dans Salat.")
        } catch (_: Exception) { record("Calcul salat", Evidence.FAILED, "Calcul impossible : vérifier position, méthode, fuseau et ajustements. Aucun horaire inventé.") }
    } }
    suspend fun exportConfig(): String = withContext(Dispatchers.IO) { store.export(_state.value.config) }
    fun importConfig(text: String) {
        try { save(store.parseImport(text)); log("Import : mots de passe et URL RTSP exclus ; renseigner à nouveau les secrets.") }
        catch (_: Exception) { log("Import refusé : format, version ou paramètres invalides.") }
    }
    fun discover() = task("Wi-Fi / découverte") {
        val wifi = DeviceDiagnostics.wifi(getApplication()) ?: error("Wi-Fi absent")
        val manager = getApplication<Application>().applicationContext.getSystemService(WifiManager::class.java)
        val lock = manager.createMulticastLock("maison-discovery").apply { setReferenceCounted(false); acquire() }
        val context = currentCoroutineContext()
        val found = try { withContext(Dispatchers.IO) { LanDiscovery(wifi.network::bindSocket).search(wifi.broadcast) { !context.isActive } } }
        finally { if (lock.isHeld) lock.release() }
        _state.update { it.copy(discovered = found) }
        record("Wi-Fi / découverte", Evidence.OBSERVED, "Fenêtre de 5 s terminée : ${found.size} réponse(s) de protocole. Absence de réponse ≠ absence de caméra. Ajout manuel disponible.")
    }
    fun deepDiscover() = task("Wi-Fi / découverte") {
        val wifi = DeviceDiagnostics.wifi(getApplication()) ?: error("Wi-Fi absent")
        val found = withTimeout(35000) { LanUnicastProbe(wifi.network.socketFactory).scanSegment(wifi.address) }
        _state.update { it.copy(discovered = (it.discovered + found).distinctBy { c -> "${c.host}/${c.protocol}" }) }
        record("Wi-Fi / découverte", Evidence.OBSERVED, "Recherche directe du segment ${wifi.address.substringBeforeLast('.')}.0/24 terminée : ${found.distinctBy { it.host }.size} hôte(s) avec réponse DVRIP/RTSP. Aucun mot de passe essayé.")
    }
    fun connectDvrip() = task("DVRIP") {
        val target = _state.value.config.camera
        target.validate()
        val now = SystemClock.elapsedRealtime()
        if (!loginLimiter.allow("${target.host}:${target.dvripPort}", now)) {
            record("DVRIP", Evidence.FAILED, "Limite d’authentification locale : 5 s entre essais, 3 essais par minute. Attendre avant de réessayer.")
            return@task
        }
        disconnect()
        val client = DvripClient(sockets()).also { dvrip = it }
        val message = withContext(Dispatchers.IO) { client.connect(target) }
        _state.update { it.copy(dvripConnected = true) }
        record("DVRIP", Evidence.OBSERVED, message)
        keepAlive = viewModelScope.launch {
            while (isActive) {
                delay(10000)
                try { withContext(Dispatchers.IO) { client.keepAlive() } }
                catch (_: Exception) {
                    _state.update { it.copy(dvripConnected = false) }
                    record("DVRIP", Evidence.FAILED, "Session perdue. Reconnexion manuelle pour éviter les verrouillages d’authentification.")
                    client.close(); break
                }
            }
        }
    }
    fun inspectOnvif() = task("ONVIF") {
        stopPtzAndWait()
        onvif?.close()
        val client = OnvifClient(_state.value.config.camera, sockets()).also { onvif = it }
        _state.update { it.copy(onvifPtz = false) }
        val s = withContext(Dispatchers.IO) { client.inspect() }
        _state.update { it.copy(onvifPtz = s.continuousPanTilt) }
        s.streamUrl?.let { uri -> persist { it.copy(camera = it.camera.copy(rtspUrl = uri)) } }
        record("ONVIF", Evidence.OBSERVED, "Profil média obtenu. URI RTSP : ${if (s.streamUrl != null) "obtenue (à tester)" else "indisponible"}. PTZ continu déclaré : ${s.continuousPanTilt}.")
    }
    fun startVideo() {
        if (_state.value.simulation || _state.value.sleeping || !_state.value.foreground || _state.value.resources?.protect == true) return
        try {
            _state.value.config.camera.validate(); LocalEndpoint.authenticatedRtsp(_state.value.config.camera); sockets()
            _state.update { it.copy(video = true) }; record("RTSP", Evidence.RUNNING, "Connexion RTSP ; attendre la première image décodée.")
        } catch (_: Exception) { record("RTSP", Evidence.FAILED, "URL RTSP locale requise, identifiants dans les champs dédiés et Wi-Fi disponible.") }
    }
    fun stopVideo() { _state.update { it.copy(video = false) } }
    fun setSimulation(value: Boolean) {
        dismissPumpkin(); stopVideo(); enablePresence(false)
        viewModelScope.launch { disconnect() }
        _state.update { it.copy(simulation = value) }
        log(if (value) "SIMULATION : les tests réseau sont désactivés." else "Mode matériel : chaque test attend une observation réelle.")
    }
    fun simulateEvent() {
        if (_state.value.simulation) viewModelScope.launch { simulation.mailboxOpened() }
    }
    fun dismissAnimation() { eventJob?.cancel(); _state.update { it.copy(animation = false, eventRule = null) } }
    fun testRule(rule: LocalRule) { playRule(rule, true) }
    private fun playRule(rule: LocalRule, simulated: Boolean) {
        eventJob?.cancel()
        eventJob = viewModelScope.launch {
            delay(rule.delaySeconds * 1000L)
            if (!_state.value.foreground) return@launch
            dismissPumpkin()
            wake("événement local")
            _state.update { it.copy(animation = true, eventRule = rule, eventSimulated = simulated) }
            record("Animations", if (simulated) Evidence.SIMULATED else Evidence.OBSERVED, if (simulated) "Test manuel d’une règle locale ; aucun capteur réel supposé." else "Règle déclenchée par un mouvement de la caméra frontale.")
            delay(rule.durationSeconds * 1000L)
            _state.update { it.copy(animation = false, eventRule = null) }
        }
    }
    fun startPumpkin(simulated: Boolean = false) {
        val s = _state.value
        if (!s.foreground || s.resources?.protect == true || s.animation || AdhanPlayback.active.value) return
        if (!simulated && (s.simulation || androidx.core.content.ContextCompat.checkSelfPermission(getApplication(),android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED)) {
            record("Citrouille",Evidence.NOT_TESTED,"Autoriser la caméra dans Paramètres → Citrouille, ou utiliser la démonstration sans caméra.")
            // Prevent an enabled timer from looping on a denied permission.
            updateHome { it.copy(pumpkin = it.pumpkin.copy(enabled = false)) }
            return
        }
        stopVideo(); stopPtz(); pumpkinJob?.cancel(); lastFaceAt = 0
        _state.update { it.copy(pumpkinActive = true,pumpkinSimulated = simulated,pumpkinCameraReady = false,pumpkinCameraError = false,pumpkinFaceFrames = 0,gaze = FaceGaze(),sleeping = false) }
        record("Citrouille",if (simulated) Evidence.SIMULATED else Evidence.RUNNING,
            if (simulated) "Démonstration du regard ; aucun humain détecté." else "Suivi frontal local demandé ; résultat matériel non vérifié.")
        pumpkinJob = viewModelScope.launch {
            val until = SystemClock.elapsedRealtime() + s.config.home.pumpkin.durationSeconds*1000L
            val cameraDeadline = SystemClock.elapsedRealtime() + 15000
            while (isActive && SystemClock.elapsedRealtime() < until) {
                delay(250)
                if (!simulated && !_state.value.pumpkinCameraReady && !_state.value.pumpkinCameraError && SystemClock.elapsedRealtime() > cameraDeadline) pumpkinCameraFailed()
                if (!simulated && _state.value.gaze.detected && SystemClock.elapsedRealtime()-lastFaceAt > 1500) _state.update { it.copy(gaze = FaceGaze()) }
            }
            dismissPumpkin()
        }
    }
    fun dismissPumpkin() {
        pumpkinJob?.cancel(); pumpkinJob = null
        _state.update { it.copy(pumpkinActive = false,gaze = FaceGaze(),pumpkinCameraReady = false) }; interact()
    }
    fun pumpkinCameraReady() {
        _state.update { it.copy(pumpkinCameraReady = true) }
        record("Citrouille",Evidence.RUNNING,"Caméra frontale liée. Détection de visage localement ; aucun suivi humain validé avant observation.")
    }
    fun pumpkinCameraFailed() {
        _state.update { it.copy(pumpkinCameraReady = false,pumpkinCameraError = true,gaze = FaceGaze()) }
        record("Citrouille",Evidence.FAILED,"Caméra indisponible ou permission refusée. Regard au centre ; toucher Fermer pour sortir.")
    }
    fun face(gaze: FaceGaze) {
        if (!_state.value.pumpkinActive || _state.value.pumpkinSimulated) return
        _state.update { it.copy(pumpkinFaceFrames = it.pumpkinFaceFrames + 1) }
        val now = SystemClock.elapsedRealtime()
        if (gaze.detected) {
            if (lastFaceAt == 0L) record("Citrouille",Evidence.OBSERVED,"Visage frontal détecté par l’API Android. Direction et fluidité du regard à vérifier sur la tablette.")
            lastFaceAt = now
            _state.update { it.copy(gaze = gaze) }
        } else if (now - lastFaceAt >= 1000) _state.update { it.copy(gaze = FaceGaze()) }
    }
    fun sleep() {
        dismissPumpkin()
        stopVideo(); stopPtz()
        _state.update { it.copy(sleeping = true) }
        record("Veille", Evidence.OBSERVED, "Écran sombre logiciel demandé ; l’application reste au premier plan. Extinction physique non demandée.")
    }
    fun interact() { lastInteraction = SystemClock.elapsedRealtime() }
    fun wake(reason: String = "toucher") {
        interact()
        if (_state.value.sleeping) { _state.update { it.copy(sleeping = false) }; record("Veille", Evidence.OBSERVED, "Réveil de l’interface : $reason.") }
    }
    fun enablePresence(enabled: Boolean) {
        if (enabled && (_state.value.resources?.protect == true || !_state.value.foreground)) return
        _state.update { it.copy(cameraEnabled = enabled) }
        if (!enabled) record("Présence", Evidence.NOT_TESTED, "Analyse arrêtée ; aucune image conservée.")
    }
    fun motion(score: Double) {
        val detected = score >= _state.value.config.motionThreshold
        val firstFrame = _state.value.motionFrames == 0L
        val firstDetection = detected && _state.value.motionEvents == 0L
        _state.update { it.copy(motionScore = score, motionFrames = it.motionFrames + 1, motionEvents = it.motionEvents + if (detected) 1 else 0) }
        if (firstFrame) record("Présence", Evidence.OBSERVED, "Première image analysée localement. Détection de mouvement à tester devant la tablette.")
        if (firstDetection) record("Présence", Evidence.OBSERVED, "Variation de luminance détectée. Présence humaine et faux positifs à vérifier sur place.")
        if (detected && !_state.value.config.home.pumpkin.enabled) { interact(); if (_state.value.sleeping) wake("mouvement détecté localement") }
        if (detected && !_state.value.animation) ruleEngine.match(_state.value.config.home.rules, "presence", SystemClock.elapsedRealtime()).firstOrNull()?.let { playRule(it, false) }
    }
    fun foreground(active: Boolean) {
        _state.update { it.copy(foreground = active) }
        if (!active) {
            dismissPumpkin()
            dismissAnimation()
            operation?.cancel(); stopVideo(); enablePresence(false)
            viewModelScope.launch { disconnect() }
        } else interact()
    }
    fun ptz(direction: PtzCommand, x: Double, y: Double) {
        val s = _state.value
        if (ptzJob?.isActive == true || s.simulation || !s.foreground || s.sleeping || s.busy) return
        val useOnvif = s.onvifPtz
        if (!useOnvif && !(s.dvripConnected && s.config.camera.dvripPtzConfirmed)) return
        ptzJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { if (useOnvif) checkNotNull(onvif).move(x * 0.25, y * 0.25) else checkNotNull(dvrip).move(direction, 1) }
                delay(180)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { record("PTZ", Evidence.FAILED, "Déplacement refusé ou réseau interrompu. STOP tenté ; arrêt mécanique à vérifier.") }
            finally { bestEffortStop() }
        }
    }
    private suspend fun bestEffortStop() = withContext(NonCancellable + Dispatchers.IO) {
        val s = _state.value
        val useOnvif = onvif != null && s.onvifPtz
        val useDvrip = dvrip != null && s.dvripConnected && s.config.camera.dvripPtzConfirmed
        if (!useOnvif && !useDvrip) return@withContext
        try {
            if (useOnvif) onvif?.stop() else dvrip?.stop()
            record("PTZ", Evidence.OBSERVED, "Commande STOP acquittée ; mouvement et arrêt mécaniques restent à vérifier visuellement.")
        } catch (_: Exception) { record("PTZ", Evidence.FAILED, "STOP sans accusé de réception. Vérifier immédiatement la caméra ; arrêt réseau non garanti.") }
    }
    fun stopPtz() {
        val job = ptzJob
        if (job != null && !job.isCompleted) job.cancel()
        else viewModelScope.launch { bestEffortStop() }
    }
    private suspend fun stopPtzAndWait() { ptzJob?.cancelAndJoin(); ptzJob = null }
    private suspend fun disconnect() {
        stopPtzAndWait(); keepAlive?.cancel(); keepAlive = null
        withContext(Dispatchers.IO) { dvrip?.close(); onvif?.close() }
        dvrip = null; onvif = null
        _state.update { it.copy(dvripConnected = false, onvifPtz = false) }
    }
    override fun onCleared() { dvrip?.close(); onvif?.close(); super.onCleared() }
}
