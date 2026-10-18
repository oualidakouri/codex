@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package fr.maisonos.diagnostic

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import fr.maisonos.core.*
import kotlinx.coroutines.*
import java.time.*
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val destinations = listOf("Accueil", "Caméras", "Interrupteurs", "Météo", "Salat", "Douas", "Événements", "Diagnostics", "Paramètres")
private val glyphs = listOf("⌂", "◉", "⏻", "☀", "☾", "۞", "↔", "◌", "⚙")
internal val french: Locale = Locale.FRENCH
internal fun timeText(epoch: Long, zone: String) = Instant.ofEpochMilli(epoch).atZone(ZoneId.of(zone)).format(DateTimeFormatter.ofPattern("HH:mm"))
internal fun countdown(event: PrayerMoment?, now: Instant): String {
    if (event == null) return "—"
    val seconds = ((event.epochMillis - now.toEpochMilli()) / 1000).coerceAtLeast(0)
    return "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
}

@Composable fun MaisonApp(s: ProbeState, vm: ProbeViewModel) {
    val context = LocalContext.current
    val home = s.config.home
    var page by rememberSaveable { mutableStateOf("Accueil") }
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(s.report) {
        if (!restored && s.report != "Lecture du matériel…") { page = home.lastPage.takeIf { it in destinations } ?: "Accueil"; restored = true }
    }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { now = Instant.now(); delay(1000) } }
    val zone = runCatching { ZoneId.of(home.prayer.zone) }.getOrDefault(ZoneId.systemDefault())
    val date = now.atZone(zone).toLocalDate()
    val days by produceState<Result<List<PrayerDay>>?>(null, home.prayer, date) {
        value = null
        value = withContext(Dispatchers.Default) { runCatching { if (home.prayer.configured) (-1..2).map { PrayerEngine.calculate(date.plusDays(it.toLong()), home.prayer) } else emptyList() } }
    }
    val day = days?.getOrNull()?.firstOrNull { it.date == date }
    val next = days?.getOrNull()?.flatMap { it.moments }?.sortedBy { it.epochMillis }?.firstOrNull { it.epochMillis > now.toEpochMilli() }
    val playing by AdhanPlayback.title.collectAsState()
    LaunchedEffect(playing) { if (playing != null) vm.wake("adhan") }
    val wide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() >= 760.dp }
    LaunchedEffect(home.presenceOnLaunch, s.foreground) {
        if (home.presenceOnLaunch && s.foreground && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && s.resources?.protect != true) vm.enablePresence(true)
    }
    fun navigate(label: String) { vm.stopVideo(); vm.stopPtz(); vm.interact(); page = label; vm.updateHome { it.copy(lastPage = label) } }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)))) {
        Wallpaper(home.wallpaperId)
        Row(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (wide) Column(Modifier.width(168.dp).fillMaxHeight().background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MAISON", fontSize = 24.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("O S  /  CHEZ VOUS", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(22.dp))
                destinations.forEachIndexed { i, label ->
                    Surface(color = if (page == label) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("nav-$label").clickable { navigate(label) }) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(glyphs[i], fontSize = 21.sp, color = MaterialTheme.colorScheme.primary)
                            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = if (page == label) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("LOCAL PAR NATURE", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                Text("Maison OS ${BuildConfig.VERSION_NAME}\n${home.cameras.size} caméra(s)", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(if (wide) 22.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (page == "Accueil") "Bienvenue à la maison" else page, fontSize = if (wide) 28.sp else 23.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (page == "Accueil") date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", french)).replaceFirstChar { it.uppercase() } else "Votre maison, en local", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    if (wide && next != null) Text("${next.prayer.label}  ${timeText(next.epochMillis, home.prayer.zone)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = vm::sleep) { Text("☾ Veille") }
                }
                if (!wide) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    destinations.forEach { label -> FilterChip(selected = page == label, onClick = { navigate(label) }, label = { Text(label) }, modifier = Modifier.testTag("nav-$label")) }
                }
                Box(Modifier.weight(1f)) {
                    when (page) {
                        "Accueil" -> Dashboard(s, vm, now, day, next, wide, playing == null, ::navigate)
                        "Caméras" -> HomeCameraPage(s, vm)
                        "Interrupteurs" -> SwitchesScreen(s, vm)
                        "Météo" -> WeatherScreen(s, vm)
                        "Salat" -> PrayerScreen(s, vm, now, day, next, days?.exceptionOrNull() != null)
                        "Douas" -> DuasScreen(s, vm, date)
                        "Événements" -> EventsScreen(s, vm)
                        "Diagnostics" -> DiagnosticTabs(s, vm)
                        else -> HomeSettingsScreen(s, vm, ::navigate)
                    }
                }
            }
        }
        if (s.cameraEnabled && s.foreground) Box(Modifier.align(Alignment.BottomEnd)) { PresenceCamera(vm, false) }
        if (s.sleeping) Box(Modifier.fillMaxSize().background(Color.Black).clickable { vm.wake() }, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(now.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 96.sp, fontWeight = FontWeight.Light, color = Color(0xFF8993A2))
                Text(date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", french)), color = Color(0xFF586271))
                Text(if (next != null) "${next.prayer.label} dans ${countdown(next, now)}" else "Configurer la salat dans les réglages", color = Color(0xFF8993A2), fontSize = 22.sp)
                Text("Toucher pour réveiller", fontSize = 12.sp, color = Color(0xFF404956))
            }
        }
        if (s.animation) EventOverlay(s, vm)
        val update by vm.updates.state.collectAsState()
        if (update.prompt && s.foreground && !s.sleeping && !s.animation && playing == null) UpdateDialog(update, vm.updates)
        if (playing != null) Dialog(onDismissRequest = { AdhanPlayback.stop(context) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("☾", fontSize = 72.sp, color = MaterialTheme.colorScheme.primary)
                    Text(playing.orEmpty(), fontSize = 36.sp, fontWeight = FontWeight.Light)
                    Spacer(Modifier.height(20.dp)); Text("Un moment pour vous recueillir", color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(32.dp)); Button(onClick = { AdhanPlayback.stop(context) }) { Text("Arrêter la lecture") }
                }
            }
        }
    }
}

@Composable private fun Wallpaper(id: String) {
    if (id.isBlank()) return
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = LocalFiles.mediaFile(context, id)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1; while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1000) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop); Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.45f))) }
}

@Composable private fun Dashboard(s: ProbeState, vm: ProbeViewModel, now: Instant, day: PrayerDay?, next: PrayerMoment?, wide: Boolean, audioFree: Boolean, navigate: (String) -> Unit) {
    val home = s.config.home
    val profile = home.profile()
    var edit by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable(profile.id) { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var properties by remember { mutableStateOf<HomeWidget?>(null) }
    var preview by remember { mutableStateOf<CameraConfig?>(null) }
    val columns = if (wide) 6 else 4
    val widgets = if (wide) profile.landscape else profile.portrait
    val visible = widgets.filter { it.page == page.coerceAtMost(profile.pages - 1) }
    fun updateLayout(change: (List<HomeWidget>) -> List<HomeWidget>) {
        vm.updateHome { cfg -> cfg.copy(profiles = cfg.profiles.map { p -> if (p.id != profile.id) p else if (wide) p.copy(landscape = change(p.landscape)) else p.copy(portrait = change(p.portrait)) }) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(profile.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (profile.pages > 1) repeat(profile.pages) { index -> FilterChip(selected = page == index, onClick = { page = index }, label = { Text("${index + 1}") }) }
            OutlinedButton(onClick = { edit = !edit }, modifier = Modifier.testTag("edit-dashboard")) { Text(if (edit) "Verrouiller" else "Personnaliser") }
            if (edit) {
                Button(onClick = { adding = true }) { Text("+ Widget") }
                TextButton(onClick = { if (profile.pages < 20) vm.updateHome { it.copy(profiles = it.profiles.map { p -> if (p.id == profile.id) p.copy(pages = p.pages + 1) else p }) } }) { Text("+ Page") }
            }
        }
        if (edit) Text("Touchez une carte ou son bouton ⋯ pour choisir sa position et sa taille avec les boutons. Le glisser-déposer reste disponible par appui long.", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val cellWidth = maxWidth / columns
            val cellHeight = 105.dp
            val cellWidthPx = with(LocalDensity.current) { cellWidth.toPx() }
            val cellHeightPx = with(LocalDensity.current) { cellHeight.toPx() }
            val height = ((visible.maxOfOrNull { it.y + it.h } ?: 2) * 105).dp
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(Modifier.height(height).fillMaxWidth()) {
                    visible.forEach { widget -> key(widget.id) {
                        var drag by remember { mutableStateOf(Offset.Zero) }
                        var resize by remember { mutableStateOf(Offset.Zero) }
                        var inView by remember { mutableStateOf(false) }
                        val cameraIndex = visible.filter { it.kind == WidgetKind.CAMERA }.indexOfFirst { it.id == widget.id }
                        val streaming = !edit && preview == null && s.foreground && !s.sleeping && !s.animation && audioFree && s.resources?.protect != true && home.liveDashboard && cameraIndex < home.maxStreams && inView
                        Box(Modifier.offset { IntOffset((widget.x * cellWidthPx + drag.x).roundToInt(), (widget.y * cellHeightPx + drag.y).roundToInt()) }
                            .width(cellWidth * widget.w).height(cellHeight * widget.h).padding(6.dp).testTag("widget-${widget.kind.name}")
                            .onGloballyPositioned { coordinates -> val rect = coordinates.boundsInWindow(); inView = rect.width > 8 && rect.height > 8 }
                            .then(if (edit) Modifier.clickable { properties = widget }.pointerInput(widget.id, widget.x, widget.y, columns) {
                                detectDragGesturesAfterLongPress(onDrag = { change, amount -> change.consume(); drag += amount }, onDragCancel = { drag = Offset.Zero }, onDragEnd = {
                                    val x = widget.x + (drag.x / cellWidthPx).roundToInt(); val y = widget.y + (drag.y / cellHeightPx).roundToInt(); drag = Offset.Zero
                                    updateLayout { LayoutEngine.move(it, widget.id, x, y, columns = columns) }
                                })
                            } else Modifier)) {
                            Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = widget.opacity), border = if (edit) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null) {
                                Box(Modifier.fillMaxSize().padding(when (widget.kind) { WidgetKind.CAMERA -> 10.dp; WidgetKind.SHORTCUTS -> 12.dp; else -> 18.dp })) {
                                    WidgetBody(widget, home, s, vm, now, day, next, streaming, navigate) { camera -> vm.selectCamera(camera); preview = camera }
                                    if (edit) TextButton(onClick = { properties = widget }, modifier = Modifier.align(Alignment.TopEnd).sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("widget-settings-${widget.kind.name}")) { Text("⋯", fontSize = 24.sp) }
                                }
                            }
                            if (edit) Box(Modifier.align(Alignment.BottomEnd).size(48.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 12.dp, bottomEnd = 16.dp)).pointerInput(widget.id, widget.w, widget.h) {
                                detectDragGestures(onDrag = { change, amount -> change.consume(); resize += amount }, onDragCancel = { resize = Offset.Zero }, onDragEnd = {
                                    val w = widget.w + (resize.x / cellWidthPx).roundToInt(); val h = widget.h + (resize.y / cellHeightPx).roundToInt(); resize = Offset.Zero
                                    updateLayout { LayoutEngine.move(it, widget.id, widget.x, widget.y, w, h, columns) }
                                })
                            }, contentAlignment = Alignment.Center) { Text("↘", color = MaterialTheme.colorScheme.onPrimary) }
                        }
                    } }
                    if (visible.isEmpty()) Text("Cette page est vide. Personnaliser → + Widget.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
    if (adding) AlertDialog(onDismissRequest = { adding = false }, title = { Text("Ajouter un widget") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) { WidgetKind.entries.forEach { kind -> TextButton(onClick = {
            val widget = HomeWidget(kind = kind, page = page, x = 0, y = 0, w = if (kind == WidgetKind.SHORTCUTS) columns else 3.coerceAtMost(columns), h = if (kind == WidgetKind.TABLET) 1 else 2)
            vm.updateHome { cfg -> cfg.copy(profiles = cfg.profiles.map { p -> if (p.id != profile.id) p else p.copy(landscape = LayoutEngine.pack(p.landscape + widget.copy(w = minOf(widget.w, 6)), 6), portrait = LayoutEngine.pack(p.portrait + widget.copy(w = minOf(widget.w, 4)), 4)) }) }
            adding = false
        }) { Text(kind.label) } } }
    }, confirmButton = { TextButton(onClick = { adding = false }) { Text("Fermer") } })
    properties?.let { w -> AlertDialog(onDismissRequest = { properties = null }, title = { Text(w.kind.label) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Position · colonne ${w.x + 1}, ligne ${w.y + 1}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = w.x > 0, onClick = { properties = w.copy(x = w.x - 1) }) { Text("←") }
                OutlinedButton(enabled = w.x + w.w < columns, onClick = { properties = w.copy(x = w.x + 1) }) { Text("→") }
                OutlinedButton(enabled = w.y > 0, onClick = { properties = w.copy(y = w.y - 1) }) { Text("↑") }
                OutlinedButton(enabled = w.y < 990, onClick = { properties = w.copy(y = w.y + 1) }) { Text("↓") }
            }
            Text("Largeur : ${w.w} · hauteur : ${w.h}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = w.w > 1, onClick = { properties = w.copy(w = w.w - 1) }) { Text("Largeur −") }
                OutlinedButton(enabled = w.w < columns, onClick = { properties = w.copy(w = w.w + 1, x = w.x.coerceAtMost(columns - w.w - 1)) }) { Text("Largeur +") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = w.h > 1, onClick = { properties = w.copy(h = w.h - 1) }) { Text("Hauteur −") }
                OutlinedButton(enabled = w.h < 6, onClick = { properties = w.copy(h = w.h + 1) }) { Text("Hauteur +") }
            }
            Text("Transparence")
            Slider(value = w.opacity, onValueChange = { v -> properties = w.copy(opacity = v) }, valueRange = 0.3f..1f)
            if (w.kind == WidgetKind.CAMERA) home.cameras.forEach { c -> TextButton(onClick = { properties = w.copy(cameraId = c.id) }) { Text("${if (w.cameraId == c.id) "✓ " else ""}${c.name}") } }
            if (profile.pages > 1) { Text("Page"); Row { repeat(profile.pages) { p -> TextButton(onClick = { properties = w.copy(page = p) }) { Text("${if (w.page == p) "✓" else ""}${p + 1}") } } } }
        }
    }, confirmButton = { TextButton(onClick = { updateLayout { list -> LayoutEngine.move(list.map { if (it.id == w.id) w else it }, w.id, w.x, w.y, w.w, w.h, columns) }; properties = null }) { Text("Enregistrer") } }, dismissButton = { TextButton(onClick = {
        vm.updateHome { cfg -> cfg.copy(profiles = cfg.profiles.map { p -> if (p.id != profile.id) p else p.copy(landscape = p.landscape.filterNot { it.id == w.id }, portrait = p.portrait.filterNot { it.id == w.id }) }) }; properties = null
    }) { Text("Supprimer") } }) }
    preview?.let { camera -> CameraFullscreen(camera, s, vm) { preview = null } }
}

@Composable private fun WidgetBody(w: HomeWidget, home: HomeConfig, s: ProbeState, vm: ProbeViewModel, now: Instant, day: PrayerDay?, next: PrayerMoment?, streaming: Boolean, navigate: (String) -> Unit, openCamera: (CameraConfig) -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.of(home.prayer.zone)
    val date = now.atZone(zone).toLocalDate()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(w.kind.label.uppercase(french), fontSize = 10.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
        when (w.kind) {
            WidgetKind.CLOCK -> {
                Text(now.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = (if (w.w >= 3) 54 else 34).sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp)
                Text(date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", french)), fontSize = 14.sp)
                Text(runCatching { HijrahDate.from(date.plusDays(home.prayer.hijriAdjustment.toLong())).format(DateTimeFormatter.ofPattern("d MMMM yyyy", french)) }.getOrDefault("Date hégirienne indisponible"), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            }
            WidgetKind.NEXT_PRAYER -> {
                Text(next?.prayer?.label ?: "Bienvenue", fontSize = 34.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.primary)
                if (next != null) { Text("dans ${countdown(next, now)}", fontSize = 24.sp); Text("${timeText(next.epochMillis, home.prayer.zone)} · ${home.prayer.city.ifBlank { "Position manuelle" }}", fontSize = 12.sp) }
                else { Text("Configurez votre ville pour calculer les horaires hors ligne.", fontSize = 13.sp); TextButton(onClick = { navigate("Salat") }) { Text("Configurer la salat") } }
            }
            WidgetKind.PRAYERS -> {
                if (day == null) Text("Ville non configurée ou calcul indisponible.", fontSize = 13.sp)
                day?.moments?.forEach { event -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(event.prayer.label, fontSize = 14.sp, color = if (event.prayer == next?.prayer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text(timeText(event.epochMillis, home.prayer.zone), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                } }
            }
            WidgetKind.DUA -> {
                val dua = Duas.daily(context, date, home.duaOffset)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(dua.arabic, fontSize = 21.sp, lineHeight = 34.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth(), style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl))
                }
                Text(dua.source, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { navigate("Douas") })
            }
            WidgetKind.TABLET -> {
                Text("Batterie ${s.resources?.battery ?: "—"}% · mémoire ${s.resources?.pssMb ?: "—"} Mo", fontSize = 15.sp)
                Text("Température de la batterie : ${s.resources?.temperatureC?.let { "$it °C" } ?: "indisponible"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                Text(if (s.wifi.contains("indisponible")) "Wi-Fi indisponible" else "Réseau local · ${home.cameras.size} caméra(s)", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            }
            WidgetKind.CAMERA -> {
                val camera = if (w.cameraId.isBlank()) home.cameras.firstOrNull() else home.cameras.firstOrNull { it.id == w.cameraId }
                if (camera == null) TextButton(onClick = { navigate("Caméras") }) { Text("Ajouter une caméra") }
                else {
                    Text(camera.name, fontSize = 14.sp)
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        if (streaming && camera.rtspUrl.isNotBlank()) RtspVideo(camera, vm, Modifier.fillMaxSize())
                        else Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text(if (camera.rtspUrl.isBlank()) "Configurer le flux RTSP" else "◉  Ouvrir la caméra", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary) }
                        Box(Modifier.matchParentSize().clickable { if (camera.rtspUrl.isBlank()) navigate("Caméras") else openCamera(camera) })
                    }
                }
            }
            WidgetKind.WEATHER -> WeatherWidget(home.weather, vm, now) { navigate("Météo") }
            WidgetKind.SWITCHES -> {
                Text("${home.switches.size} interrupteur(s)", fontSize = 22.sp)
                TextButton(onClick = { navigate("Interrupteurs") }) { Text("Ouvrir les commandes") }
            }
            WidgetKind.MAILBOX -> {
                Text("✉", fontSize = 36.sp, color = MaterialTheme.colorScheme.primary)
                Text("Mode simulation · aucun capteur connecté", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                TextButton(onClick = { vm.testRule(home.rules.firstOrNull() ?: LocalRule()) }) { Text("Tester l’événement") }
            }
            WidgetKind.SHORTCUTS -> FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { navigate("Caméras") }) { Text("◉ Caméras") }
                TextButton(onClick = { navigate("Salat") }) { Text("☾ Salat") }
                TextButton(onClick = { navigate("Interrupteurs") }) { Text("⏻ Interrupteurs") }
                TextButton(onClick = vm::sleep) { Text("Veille") }
            }
        }
    }
}

@Composable private fun DiagnosticTabs(s: ProbeState, vm: ProbeViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState())) { listOf("Tablette", "Présence & veille", "Simulation", "Journal").forEachIndexed { i, label -> FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(label) }, modifier = Modifier.padding(end = 8.dp)) } }
        Box(Modifier.weight(1f)) { when (tab) { 0 -> DiagnosticsPage(s, vm); 1 -> PresencePage(s, vm); 2 -> SimulationPage(s, vm); else -> JournalPage(s, vm) } }
    }
}
