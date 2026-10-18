package fr.maisonos.diagnostic

import android.Manifest
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import fr.maisonos.core.*
import fr.maisonos.core.vendor.ptz.PtzCommand
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private lateinit var model: ProbeViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize before onResume, including when the same ViewModel survives rotation.
        model = ViewModelProvider(this)[ProbeViewModel::class.java]
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            val vm = model
            val state by vm.state.collectAsStateWithLifecycle()
            val palette = ThemeCatalog.forHome(state.config.home)
            val colors = if (palette.dark) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors.copy(primary = Color(palette.accent), onPrimary = Color(palette.onAccent),
                background = Color(palette.background), onBackground = Color(palette.text), surface = Color(palette.surface), onSurface = Color(palette.text),
                secondary = Color(palette.muted), onSurfaceVariant = Color(palette.muted), primaryContainer = Color(palette.surface), onPrimaryContainer = Color(palette.text))) {
                LaunchedEffect(state.config.keepAwake, state.foreground) {
                    if (state.config.keepAwake && state.foreground) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                LaunchedEffect(state.sleeping) {
                    window.attributes = window.attributes.apply { screenBrightness = if (state.sleeping) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) { MaisonApp(state, vm) }
            }
        }
    }
    override fun onResume() { super.onResume(); if (::model.isInitialized) { model.foreground(true); model.reschedulePrayers(); model.checkUpdates() } }
    override fun onPause() { if (::model.isInitialized) model.foreground(false); super.onPause() }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (::model.isInitialized && (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE)) model.interact()
        return super.dispatchTouchEvent(event)
    }
}

@Composable internal fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            content()
        }
    }
}

@Composable internal fun Status(d: Diagnostic) {
    val (label, color) = when (d.evidence) {
        Evidence.NOT_TESTED -> "NON VÉRIFIÉ" to Color(0xFF9FAFC4)
        Evidence.RUNNING -> "EN COURS" to Color(0xFF7CB7F5)
        Evidence.OBSERVED -> "OBSERVÉ PAR L’API" to Color(0xFF6AE0C2)
        Evidence.FAILED -> "ÉCHEC" to Color(0xFFFFA0A0)
        Evidence.SIMULATED -> "SIMULÉ" to Color(0xFFE9C67C)
        Evidence.UNAVAILABLE -> "NON IMPLÉMENTÉ / INDISPONIBLE" to Color(0xFF9FAFC4)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(d.title, fontWeight = FontWeight.Medium)
        Text(label, fontSize = 10.sp, color = color)
    }
    Text(d.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
}

@Composable fun DiagnosticsPage(s: ProbeState, vm: ProbeViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var audio by remember { mutableStateOf<MediaPlayer?>(null) }
    var audioJob by remember { mutableStateOf<Job?>(null) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            audioJob?.cancel(); audio?.release(); audio = null
            audioJob = scope.launch {
                try {
                    val file = withContext(Dispatchers.IO) {
                        File(context.cacheDir, "test-audio").also { f ->
                            context.contentResolver.openInputStream(uri)!!.use { input ->
                                f.outputStream().use { out ->
                                    val buf = ByteArray(8192); var total = 0
                                    while (true) { ensureActive(); val n = input.read(buf); if (n < 0) break; total += n; require(total <= 20 * 1024 * 1024); out.write(buf, 0, n) }
                                }
                            }
                        }
                    }
                    audio = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        setOnPreparedListener { it.start(); vm.record("Audio / adhan", Evidence.RUNNING, "Lecture du fichier personnel local. Audibilité à vérifier ; la programmation est disponible dans Salat.") }
                        setOnCompletionListener { it.release(); audio = null; vm.record("Audio / adhan", Evidence.OBSERVED, "Lecture locale terminée selon Android. Son audible à confirmer sur la tablette.") }
                        setOnErrorListener { p, _, _ -> p.release(); audio = null; vm.record("Audio / adhan", Evidence.FAILED, "Format audio non pris en charge."); true }
                        prepareAsync()
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { vm.record("Audio / adhan", Evidence.FAILED, "Lecture impossible : fichier local compatible de 20 Mo maximum requis.") }
            }
        }
    }
    DisposableEffect(s.foreground) { onDispose { audioJob?.cancel(); audio?.release(); audio = null } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel("État de la tablette") {
            s.resources?.let { r ->
                Text("${r.totalRamMb} Mo RAM totale · ${r.availableRamMb} Mo disponibles", fontSize = 21.sp, fontWeight = FontWeight.Medium)
                Text("Application : PSS ${r.pssMb} Mo · tas Java ${r.heapMb} Mo")
                Text("Batterie ${r.battery}% · ${if (r.charging) "branchée" else "sur batterie"} · batterie ${r.temperatureC?.let { "$it °C" } ?: "température indisponible"}")
                Text("État thermique Android : ${r.thermal} · session ${r.elapsed / 60000} min · ${s.samples.size}/120 mesures conservées")
                if (r.protect) Text("Protection active : tests vidéo et caméra suspendus.", color = Color(0xFFFFA0A0))
            }
            Text(s.wifi, color = MaterialTheme.colorScheme.primary)
            Text(s.report, fontSize = 12.sp)
        }
        Panel("Tests indépendants") {
            Text("Aucun résultat matériel n’est présumé. « Observé » décrit un retour API ; les essais visuels et physiques restent nécessaires.", fontSize = 12.sp)
            OutlinedButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }) { Text("Tester l’audio local / récitation personnelle") }
            if (audio != null) TextButton(onClick = { audioJob?.cancel(); audio?.release(); audio = null }) { Text("Arrêter l’audio") }
            OutlinedButton(onClick = {
                val first = s.samples.firstOrNull(); val last = s.samples.lastOrNull()
                if (first != null && last != null) vm.record("Performance", Evidence.OBSERVED, "${s.samples.size} mesures sur ${(last.elapsed - first.elapsed) / 1000}s ; PSS ${first.pssMb} → ${last.pssMb} Mo. Stabilité longue durée non validée.")
            }) { Text("Relever les mesures de performance") }
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }) { Text("Ouvrir les réglages Wi-Fi") }
            Text("Sans Internet : couper l’accès WAN du routeur en conservant le Wi-Fi, puis refaire découverte, vidéo, PTZ et présence. Vérifier chaque fonction avec les équipements réels.", fontSize = 12.sp)
            OutlinedButton(onClick = { vm.record("Sans Internet", Evidence.NOT_TESTED, "Procédure affichée. Aucun test WAN ni accès extérieur lancé ; coupure WAN et tests locaux à effectuer par l’utilisateur.") }) { Text("Consigner le test hors Internet à effectuer") }
        }
        Panel("Matrice de diagnostic") {
            s.diagnostics.values.forEach { Status(it); HorizontalDivider(color = Color(0xFF283646)) }
        }
    }
}

@Composable fun CameraPage(s: ProbeState, vm: ProbeViewModel, full: Boolean, setFull: (Boolean) -> Unit) {
    var config by remember(s.config) { mutableStateOf(s.config) }
    val c = config.camera
    var showPassword by remember(c.id) { mutableStateOf(false) }
    fun camera(edit: (CameraConfig) -> CameraConfig) { config = config.copy(camera = edit(config.camera)) }
    if (s.video && s.foreground && !s.sleeping) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.stopVideo(); setFull(false) }) { Text("Arrêter la vidéo") }
                OutlinedButton(onClick = { setFull(!full) }) { Text(if (full) "Réduire" else "Agrandir") }
                Text("1 flux · TCP · son coupé", fontSize = 12.sp)
            }
            RtspVideo(s.config.camera, vm, Modifier.weight(1f).fillMaxWidth())
            PtzControls(s, vm)
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel("Découverte sur mon Wi-Fi") {
            Text("DVRIP UDP 34569 et ONVIF WS-Discovery. Recherche de 5 secondes, sans essai d’authentification ni balayage du réseau.", fontSize = 12.sp)
            Button(enabled = !s.busy && !s.simulation, onClick = vm::discover) { Text(if (s.busy) "Test en cours…" else "Rechercher mes caméras") }
            OutlinedButton(enabled = !s.busy && !s.simulation, onClick = vm::deepDiscover) { Text("Recherche approfondie du segment Wi-Fi /24") }
            Text("Le scan n’est pas limité par le nombre de vidéos. La recherche approfondie vérifie DVRIP et RTSP sur 254 adresses au maximum, sans mot de passe. Une caméra peut annoncer plusieurs protocoles.", fontSize = 11.sp)
            s.discovered.groupBy { it.host }.forEach { (host, protocols) ->
                OutlinedButton(onClick = {
                    camera { old -> old.copy(host = host,
                        dvripPort = protocols.firstOrNull { it.protocol == "DVRIP" }?.port ?: old.dvripPort,
                        onvifPort = protocols.firstOrNull { it.protocol == "ONVIF" }?.port ?: old.onvifPort,
                        onvifPath = protocols.firstOrNull { it.protocol == "ONVIF" }?.let { runCatching { java.net.URI(it.endpoint).path }.getOrDefault(old.onvifPath) } ?: old.onvifPath) }
                }) { Text("$host · ${protocols.joinToString { "${it.protocol}:${it.port}" }} → utiliser") }
            }
            Text(s.diagnostics["Wi-Fi / découverte"]!!.detail, fontSize = 12.sp)
        }
        Panel("Caméra locale · ajout manuel") {
            Text("Enregistrer avant les tests. L’URL RTSP exacte dépend du firmware : utiliser de préférence le sous-flux basse résolution ou l’URI retournée par ONVIF.", fontSize = 12.sp)
            Field("Nom", c.name, { v -> camera { it.copy(name = v) } })
            Field("Pièce / emplacement", c.room, { v -> camera { it.copy(room = v) } })
            Field("Adresse IPv4 locale", c.host, { v -> camera { it.copy(host = v.trim()) } })
            Field("Identifiant", c.username, { v -> camera { it.copy(username = v) } })
            OutlinedTextField(value = c.password, onValueChange = { v -> camera { it.copy(password = v) } }, label = { Text("Mot de passe") },
                visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Masquer" else "Afficher") } },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            PortField("Port DVRIP", c.dvripPort) { v -> camera { it.copy(dvripPort = v) } }
            PortField("Port ONVIF HTTP", c.onvifPort) { v -> camera { it.copy(onvifPort = v) } }
            Field("Chemin ONVIF", c.onvifPath, { v -> camera { it.copy(onvifPath = v) } })
            Field("URL RTSP sans identifiants", c.rtspUrl, { v -> camera { it.copy(rtspUrl = v.trim()) } })
            Field("URL RTSP principale (plein écran, facultative)", c.mainRtspUrl, { v -> camera { it.copy(mainRtspUrl = v.trim()) } })
            Choice(listOf("RTSP standard / URI ONVIF", "RTSP iCSee / Xiongmai (identifiants dans le chemin)"), if (c.rtspVendorPath) 1 else 0) { index -> camera { it.copy(rtspVendorPath = index == 1) } }
            OutlinedButton(enabled = c.host.isNotBlank(), onClick = { camera { it.copy(rtspVendorPath = true, rtspUrl = "rtsp://${it.host}:554/stream=1", mainRtspUrl = "rtsp://${it.host}:554/stream=0") } }) { Text("Préparer les chemins iCSee / Xiongmai") }
            Text("Ce format est à tester selon le firmware. Il utilise uniquement vos identifiants saisis ; aucun compte par défaut n’est essayé. Enregistrer avant le test RTSP.", fontSize = 11.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = c.dvripPtzConfirmed, onCheckedChange = { v -> camera { it.copy(dvripPtzConfirmed = v) } })
                Text("J’autorise le test PTZ DVRIP sur cette caméra motorisée. Je vérifierai son arrêt visuellement.", fontSize = 12.sp)
            }
            Text("DVRIP est un protocole peu sécurisé. Utiliser le compte fourni par le propriétaire et un réseau IoT isolé. Aucun mot de passe par défaut n’est essayé.", fontSize = 12.sp)
            Button(enabled = !s.busy, onClick = { vm.save(config) }) { Text("Enregistrer sur la tablette") }
            s.logs.lastOrNull()?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary) }
        }
        Panel("Validation par protocole") {
            val enabled = !s.busy && !s.simulation
            Text("Le scan approfondi /24 recherche DVRIP et RTSP. Une ligne sans ONVIF ne prouve pas son absence. Le Wi-Fi 5 GHz fonctionne avec le 2,4 GHz si les réseaux ne sont pas isolés.", fontSize = 12.sp)
            OutlinedButton(enabled = enabled, onClick = vm::checkCameraPorts) { Text("Vérifier les ports de cette caméra") }
            s.cameraPorts.forEach { result -> Text("${result.label} : ${result.port} · ${if (result.reachable) "accessible" else "non joignable"}\n${result.detail}", fontSize = 12.sp) }
            OutlinedButton(enabled = enabled, onClick = vm::connectDvrip) { Text("Tester l’authentification DVRIP") }
            Status(s.diagnostics["DVRIP"]!!)
            OutlinedButton(enabled = enabled, onClick = vm::inspectOnvif) { Text("Tester ONVIF / obtenir le profil RTSP") }
            Status(s.diagnostics["ONVIF"]!!)
            Button(enabled = enabled, onClick = vm::startVideo) { Text("Tester / afficher le flux RTSP") }
            Status(s.diagnostics["RTSP"]!!)
            PtzControls(s, vm)
        }
    }
}

@Composable internal fun Field(label: String, value: String, changed: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = changed, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}
@Composable internal fun PortField(label: String, value: Int, changed: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(value = text, onValueChange = { text = it; changed(it.toIntOrNull() ?: 0) }, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable internal fun PtzControls(s: ProbeState, vm: ProbeViewModel) {
    val supported = s.onvifPtz || (s.dvripConnected && s.config.camera.dvripPtzConfirmed)
    if (!supported) { Text("PTZ masqué : capacité ONVIF non détectée ou test DVRIP non autorisé.", fontSize = 12.sp); return }
    Text("PTZ ${if (s.onvifPtz) "ONVIF" else "DVRIP"} · impulsion courte · arrêt mécanique à vérifier", fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple("←", PtzCommand.DIRECTION_LEFT, -1.0 to 0.0), Triple("↑", PtzCommand.DIRECTION_UP, 0.0 to 1.0), Triple("↓", PtzCommand.DIRECTION_DOWN, 0.0 to -1.0), Triple("→", PtzCommand.DIRECTION_RIGHT, 1.0 to 0.0)).forEach { (label, direction, xy) ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(52.dp).pointerInput(direction) {
                    detectTapGestures(onPress = {
                        vm.interact(); vm.ptz(direction, xy.first, xy.second)
                        try { tryAwaitRelease() } finally { vm.stopPtz() }
                    })
                }) { Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 24.sp) } }
        }
        Button(onClick = vm::stopPtz) { Text("STOP") }
    }
    Status(s.diagnostics["PTZ"]!!)
}

@Composable fun PresencePage(s: ProbeState, vm: ProbeViewModel) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.enablePresence(true) else vm.record("Présence", Evidence.FAILED, "Permission caméra refusée. La veille reste disponible au toucher.")
    }
    var threshold by remember(s.config.motionThreshold) { mutableFloatStateOf(s.config.motionThreshold) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel("Présence locale · caméra frontale") {
            Text("Comparaison de luminance, pas de reconnaissance de personne. Les changements de lumière peuvent déclencher une détection. Aucune image enregistrée ni transmise.")
            Text("Analyse limitée au premier plan, y compris derrière l’écran sombre logiciel. Une sortie de l’application arrête l’analyse.", fontSize = 12.sp)
            Button(enabled = !s.simulation && s.resources?.protect != true, onClick = { if (s.cameraEnabled) vm.enablePresence(false) else permission.launch(Manifest.permission.CAMERA) }) { Text(if (s.cameraEnabled) "Arrêter la caméra frontale" else "Tester la caméra frontale") }
            Text("Images analysées : ${s.motionFrames} · seuil franchi : ${s.motionEvents} fois · score ${"%.3f".format(s.motionScore)}")
            Status(s.diagnostics["Présence"]!!)
            Text("Sensibilité : seuil ${"%.2f".format(threshold)} (plus bas = plus sensible)")
            Slider(value = threshold, onValueChange = { threshold = it }, enabled = !s.busy,
                onValueChangeFinished = { vm.save(s.config.copy(motionThreshold = threshold)) }, valueRange = 0.01f..0.5f)
        }
        Panel("Écran mural et veille") {
            Button(onClick = vm::sleep) { Text("Tester l’écran sombre / réveil") }
            Text("Réveil par toucher ou mouvement si l’analyse est active. Aucun usage de Device Admin et aucun contournement de Doze. La prochaine salat est affichée si les coordonnées sont configurées.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = s.config.keepAwake, onCheckedChange = { vm.save(s.config.copy(keepAwake = it)) })
                Text("Maintenir l’écran actif au premier plan")
            }
            Text("Délai de veille : ${s.config.idleSeconds} s")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(30, 60, 300).forEach { seconds -> OutlinedButton(onClick = { vm.save(s.config.copy(idleSeconds = seconds)) }) { Text("${seconds}s") } } }
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }) { Text("Configurer le lanceur Android (facultatif)") }
            Text("Si choisi comme écran d’accueil par défaut, Android peut relancer Maison OS au démarrage. Le constructeur peut modifier ce comportement ; à tester après redémarrage. Aucun lancement forcé depuis l’arrière-plan.", fontSize = 12.sp)
        }
    }
}

@Composable fun SimulationPage(s: ProbeState, vm: ProbeViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel("Banc de simulation") {
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = s.simulation, onCheckedChange = vm::setSimulation); Text("Mode simulation") }
            Text("Permet de tester un événement local et son affichage sans acheter de capteur. Ce test ne valide aucun équipement ni connexion réseau.")
            Button(enabled = s.simulation, onClick = vm::simulateEvent) { Text("Simuler : tu as du courrier !") }
            Text("Les widgets, horaires et règles locales se règlent dans Accueil, Salat et Événements. Les capteurs externes restent à intégrer selon leurs protocoles réels.", fontSize = 12.sp)
        }
    }
}

@Composable fun JournalPage(s: ProbeState, vm: ProbeViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try { val text = vm.exportConfig(); withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) } }; vm.log("Configuration exportée sans mot de passe ni URL RTSP.") }
            catch (_: Exception) { vm.log("Export impossible.") }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(4096)
                        while (true) { val n = input.read(buf); if (n < 0) break; require(out.size() + n <= 65536); out.write(buf, 0, n) }
                        out.toString("UTF-8")
                    }
                }
                vm.importConfig(text)
            } catch (_: Exception) { vm.log("Import impossible ou fichier trop volumineux.") }
        }
    }
    val report = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = buildString {
                    appendLine("Maison OS 0.1 · rapport de diagnostic, aucune validation matérielle présumée")
                    appendLine(s.report)
                    s.diagnostics.values.forEach { appendLine("${it.title}: ${it.evidence} · ${it.detail}") }
                    appendLine("Mesures mémoire/thermiques (maximum 120, intervalle ~5s) :")
                    s.samples.forEach { appendLine("${it.elapsed}ms;PSS=${it.pssMb}Mo;heap=${it.heapMb}Mo;batterie=${it.battery}%;tempBatterie=${it.temperatureC};thermal=${it.thermal}") }
                    s.logs.forEach { appendLine(it) }
                }
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) } }; vm.log("Rapport exporté : aucun identifiant caméra ni URL.")
            } catch (_: Exception) { vm.log("Export du rapport impossible.") }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel("Configuration et preuves") {
            OutlinedButton(onClick = { export.launch("maison-os-configuration.json") }) { Text("Exporter la configuration sans secrets") }
            OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain")) }) { Text("Restaurer une configuration") }
            Button(onClick = { report.launch("maison-os-diagnostic.txt") }) { Text("Exporter le rapport de diagnostic") }
            Text("L’export exclut mot de passe et URL RTSP, qui peut contenir des secrets dans son chemin. Les renseigner après restauration. Journal limité à 200 entrées, conservé uniquement pendant cette session.", fontSize = 12.sp)
        }
        Panel("Journal local") { s.logs.asReversed().forEach { Text(it, fontSize = 12.sp) } }
    }
}
