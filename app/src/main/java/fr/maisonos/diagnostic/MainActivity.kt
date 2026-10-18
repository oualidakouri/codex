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
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            val vm: ProbeViewModel = viewModel(); model = vm
            val state by vm.state.collectAsStateWithLifecycle()
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF6AE0C2), background = Color(0xFF0B121B),
                surface = Color(0xFF141F2B), onSurface = Color(0xFFE6EDF5), secondary = Color(0xFF9FAFC4))) {
                LaunchedEffect(state.config.keepAwake, state.foreground) {
                    if (state.config.keepAwake && state.foreground) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                LaunchedEffect(state.sleeping) {
                    window.attributes = window.attributes.apply { screenBrightness = if (state.sleeping) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
                }
                MaisonProbe(state, vm)
            }
        }
    }
    override fun onResume() { super.onResume(); if (::model.isInitialized) model.foreground(true) }
    override fun onPause() { if (::model.isInitialized) model.foreground(false); super.onPause() }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (::model.isInitialized && (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE)) model.interact()
        return super.dispatchTouchEvent(event)
    }
}

private val tabs = listOf("Diagnostics", "Caméra LAN", "Présence & veille", "Simulation", "Journal")

@Composable
fun MaisonProbe(s: ProbeState, vm: ProbeViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var fullVideo by remember { mutableStateOf(false) }
    val widthPx = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width
    val wide = with(androidx.compose.ui.platform.LocalDensity.current) { widthPx.toDp() >= 720.dp }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(Modifier.fillMaxSize()) {
            if (wide && !fullVideo) Column(Modifier.width(190.dp).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("MAISON OS", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("LABORATOIRE LOCAL", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(20.dp))
                tabs.forEachIndexed { i, label ->
                    if (tab == i) Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text(label) }
                    else TextButton(onClick = { vm.stopVideo(); vm.stopPtz(); tab = i; vm.interact() }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
                Spacer(Modifier.weight(1f))
                Text("PREUVE DE FAISABILITÉ\nv0.1 · APK native\nAucun cloud requis", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(if (wide) 20.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!fullVideo) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (wide) tabs[tab] else "Maison OS · ${tabs[tab]}", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                            Text(if (s.simulation) "SIMULATION · aucune intégration matérielle validée" else "Tests réels · validation matérielle à effectuer", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                        }
                        TextButton(onClick = { vm.sleep() }) { Text("Veille") }
                    }
                    if (!wide) PrimaryScrollableTabRow(selectedTabIndex = tab) {
                        tabs.forEachIndexed { i, label -> Tab(selected = tab == i, onClick = { vm.stopVideo(); vm.stopPtz(); tab = i; vm.interact() }, text = { Text(label) }) }
                    }
                }
                when (tab) {
                    0 -> DiagnosticsPage(s, vm)
                    1 -> CameraPage(s, vm, fullVideo) { fullVideo = it }
                    2 -> PresencePage(s, vm)
                    3 -> SimulationPage(s, vm)
                    else -> JournalPage(s, vm)
                }
            }
        }
        // Kept mounted while the software screensaver overlays the foreground app.
        if (s.cameraEnabled && s.foreground) Box(Modifier.align(Alignment.BottomEnd)) { PresenceCamera(vm, showPreview = false) }
        if (s.sleeping) SleepScreen(vm)
        if (s.animation) Box(Modifier.fillMaxSize().background(Color(0xF00B121B)).clickable { vm.dismissAnimation() }, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("✉", fontSize = 72.sp, color = MaterialTheme.colorScheme.primary)
                Text("Tu as du courrier !", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text("SIMULATION · événement local", color = MaterialTheme.colorScheme.secondary)
                Text("Toucher pour fermer")
            }
        }
    }
}

@Composable private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            content()
        }
    }
}

@Composable private fun Status(d: Diagnostic) {
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
                        setOnPreparedListener { it.start(); vm.record("Audio / adhan", Evidence.RUNNING, "Lecture du fichier personnel local. Audibilité à vérifier ; programmation adhan non implémentée.") }
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
            Text("Sans Internet : couper l’accès WAN du routeur en conservant le Wi-Fi, puis refaire découverte, vidéo, PTZ et présence. Le cloud de développement ne peut pas certifier ce test.", fontSize = 12.sp)
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
            s.discovered.forEach { found ->
                OutlinedButton(onClick = {
                    camera { old -> old.copy(host = found.host,
                        dvripPort = if (found.protocol == "DVRIP") found.port else old.dvripPort,
                        onvifPort = if (found.protocol == "ONVIF") found.port else old.onvifPort,
                        onvifPath = if (found.protocol == "ONVIF") runCatching { java.net.URI(found.endpoint).path }.getOrDefault(old.onvifPath) else old.onvifPath) }
                }) { Text("${found.host} · ${found.protocol} : ${found.port} → utiliser") }
            }
            Text(s.diagnostics["Wi-Fi / découverte"]!!.detail, fontSize = 12.sp)
        }
        Panel("Caméra locale · ajout manuel") {
            Text("Enregistrer avant les tests. L’URL RTSP exacte dépend du firmware : utiliser de préférence le sous-flux basse résolution ou l’URI retournée par ONVIF.", fontSize = 12.sp)
            Field("Nom", c.name, { v -> camera { it.copy(name = v) } })
            Field("Adresse IPv4 locale", c.host, { v -> camera { it.copy(host = v.trim()) } })
            Field("Identifiant", c.username, { v -> camera { it.copy(username = v) } })
            OutlinedTextField(value = c.password, onValueChange = { v -> camera { it.copy(password = v) } }, label = { Text("Mot de passe") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            PortField("Port DVRIP", c.dvripPort) { v -> camera { it.copy(dvripPort = v) } }
            PortField("Port ONVIF HTTP", c.onvifPort) { v -> camera { it.copy(onvifPort = v) } }
            Field("Chemin ONVIF", c.onvifPath, { v -> camera { it.copy(onvifPath = v) } })
            Field("URL RTSP sans identifiants", c.rtspUrl, { v -> camera { it.copy(rtspUrl = v.trim()) } })
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

@Composable private fun Field(label: String, value: String, changed: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = changed, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}
@Composable private fun PortField(label: String, value: Int, changed: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(value = text, onValueChange = { text = it; changed(it.toIntOrNull() ?: 0) }, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable private fun PtzControls(s: ProbeState, vm: ProbeViewModel) {
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
            Text("Réveil par toucher ou mouvement si l’analyse est active. Aucun usage de Device Admin et aucun contournement de Doze. Horaires de salat : non configurés à ce stade.")
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
            Text("Calcul salat, automatisations configurables, import de vidéos et futurs widgets seront développés après validation de cette APK.", fontSize = 12.sp)
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

@Composable private fun SleepScreen(vm: ProbeViewModel) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(1000) } }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable { vm.wake() }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 88.sp, fontWeight = FontWeight.Light, color = Color(0xFF7D8795))
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.FRENCH)), color = Color(0xFF586271))
            Text("Prochaine salat : non configurée · phase ultérieure", color = Color(0xFF586271))
            Text("Toucher pour réveiller", color = Color(0xFF404956), fontSize = 12.sp)
        }
    }
}
