@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package fr.maisonos.diagnostic

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.maisonos.core.*
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun PrayerScreen(s: ProbeState, vm: ProbeViewModel, now: Instant, day: PrayerDay?, next: PrayerMoment?, calculationFailed: Boolean) {
    val context = LocalContext.current
    val home = s.config.home
    val p = home.prayer
    var configure by remember { mutableStateOf(false) }
    var calendar by remember { mutableStateOf(false) }
    val importAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> vm.importMedia(uri, MediaKind.AUDIO) } }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed -> vm.log(if (allowed) "Notifications autorisées." else "Notifications refusées ; vérifier les restrictions dans les réglages Android.") }
    var volume by remember(p.volume) { mutableFloatStateOf(p.volume) }
    if (configure) {
        PrayerConfiguration(p, onClose = { configure = false }) { settings -> vm.updateHome { it.copy(prayer = settings) }; configure = false }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Panel(if (p.configured) p.city.ifBlank { "Position manuelle" } else "Votre lieu de prière") {
            if (next != null) {
                Text("${next.prayer.label}  ${timeText(next.epochMillis, p.zone)}", fontSize = 32.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Light)
                Text("dans ${countdown(next, now)}", fontSize = 24.sp)
            } else Text(if (calculationFailed) "Calcul indisponible pour ces paramètres. Modifier méthode, position ou ajustements." else "Choisissez une ville ou saisissez les coordonnées. Le calcul fonctionne ensuite sans Internet.")
            Text("${p.zone} · ${PrayerEngine.methodLabels.getOrElse(PrayerEngine.methods.indexOf(p.method)) { p.method }} · Asr ${if (p.hanafi) "hanafite" else "standard"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { configure = true }, modifier = Modifier.testTag("configure-prayers")) { Text("Ville et méthode de calcul") }
                OutlinedButton(enabled = p.configured, onClick = { calendar = true }) { Text("Calendrier mensuel") }
            }
            Text("Comparez les premiers horaires à votre mosquée et ajustez-les si nécessaire. Umm al-Qura : ajuster Isha de +30 min pendant Ramadan selon le calendrier retenu.", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
        }
        Panel("Les cinq prières") {
            day?.moments?.forEach { event ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(event.prayer.label, fontSize = 20.sp, color = if (event.prayer == next?.prayer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Text(if (event.prayer.name in p.enabled) "Adhan activé" else "Adhan désactivé", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    Text(timeText(event.epochMillis, p.zone), fontSize = 25.sp, fontWeight = FontWeight.Light)
                    Switch(checked = event.prayer.name in p.enabled, onCheckedChange = { enabled -> vm.updateHome { it.copy(prayer = it.prayer.copy(enabled = if (enabled) it.prayer.enabled + event.prayer.name else it.prayer.enabled - event.prayer.name)) } })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
            }
            if (day == null) Text("Aucun horaire affiché avant configuration valide.")
            day?.let { Text("Lever du soleil : ${timeText(it.sunrise, p.zone)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary) }
        }
        Panel("Récitation locale et adhan") {
            Text("Importez vos fichiers audio autorisés. Ils sont copiés dans le stockage privé de la tablette et restent disponibles après redémarrage.", fontSize = 12.sp)
            Button(onClick = { importAudio.launch(arrayOf("audio/*")) }) { Text("Importer une récitation") }
            Text("Récitation pour les cinq prières", fontWeight = FontWeight.Medium)
            MediaChoice(home.media.filter { it.kind == MediaKind.AUDIO }, p.audioId, "Aucune récitation") { id -> vm.updateHome { it.copy(prayer = it.prayer.copy(audioId = id)) } }
            Text("Récitation particulière pour Fajr", fontWeight = FontWeight.Medium)
            MediaChoice(home.media.filter { it.kind == MediaKind.AUDIO }, p.fajrAudioId, "Même récitation") { id -> vm.updateHome { it.copy(prayer = it.prayer.copy(fajrAudioId = id)) } }
            Text("Volume de l’application : ${(volume * 100).toInt()} %")
            Slider(value = volume, onValueChange = { volume = it }, onValueChangeFinished = { vm.updateHome { it.copy(prayer = it.prayer.copy(volume = volume)) } })
            Text("Le volume système des alarmes reste sous le contrôle d’Android.", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = p.audioId.isNotBlank(), onClick = { runCatching { AdhanPlayback.test(context, p.audioId) }.onFailure { vm.log("Lecture refusée par Android. Vérifier autorisations et fichier audio.") } }) { Text("Écouter / tester maintenant") }
                OutlinedButton(onClick = { AdhanPlayback.stop(context) }) { Text("Arrêter l’audio") }
                OutlinedButton(enabled = p.audioId.isNotBlank() && AdhanScheduler.exactAllowed(context), onClick = { runCatching { AdhanScheduler.scheduleTest(context) }.onSuccess { vm.log("Test d’adhan programmé dans 10 secondes.") }.onFailure { vm.log("Test d’alarme refusé par Android.") } }) { Text("Tester l’alarme dans 10 s") }
            }
            AdhanPlayback.error.collectAsState().value?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            context.getSharedPreferences("adhan-runtime", android.content.Context.MODE_PRIVATE).getString("last-error", null)?.let { Text("Dernier incident : $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            Text(if (AdhanScheduler.exactAllowed(context)) "✓ Alarmes exactes disponibles sur cet appareil" else "Autorisation d’alarmes exactes absente. La programmation est approximative et la lecture en arrière-plan peut être refusée.", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            if (Build.VERSION.SDK_INT >= 31) OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }) { Text("Autoriser les alarmes exactes") }
            if (Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Autoriser les notifications") }
            OutlinedButton(onClick = vm::reschedulePrayers) { Text("Reprogrammer les prochaines prières") }
            Text(s.diagnostics["Calcul salat"]?.detail.orEmpty(), fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
        }
    }
    if (calendar) PrayerCalendar(p) { calendar = false }
}

@Composable internal fun MediaChoice(media: List<LocalMedia>, selected: String, none: String, change: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(media.firstOrNull { it.id == selected }?.name ?: none, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(none) }, onClick = { change(""); open = false })
            media.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { change(item.id); open = false }) }
        }
    }
}

private data class City(val name: String, val lat: Double, val lon: Double, val zone: String)
private val cities = listOf(City("Paris",48.8566,2.3522,"Europe/Paris"), City("Lyon",45.764,4.8357,"Europe/Paris"), City("Marseille",43.2965,5.3698,"Europe/Paris"), City("Lille",50.6292,3.0573,"Europe/Paris"), City("Bruxelles",50.8503,4.3517,"Europe/Brussels"), City("Genève",46.2044,6.1432,"Europe/Zurich"), City("Casablanca",33.5731,-7.5898,"Africa/Casablanca"), City("Rabat",34.0209,-6.8416,"Africa/Casablanca"), City("Alger",36.7538,3.0588,"Africa/Algiers"), City("Tunis",36.8065,10.1815,"Africa/Tunis"))

@Composable private fun PrayerConfiguration(initial: PrayerSettings, onClose: () -> Unit, save: (PrayerSettings) -> Unit) {
    var p by remember { mutableStateOf(initial) }
    var latitude by remember { mutableStateOf(p.latitude?.toString().orEmpty()) }
    var longitude by remember { mutableStateOf(p.longitude?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var customFajr by remember { mutableStateOf(p.fajrAngle.toString()) }
    var customIsha by remember { mutableStateOf(p.ishaAngle.toString()) }
    val scope = rememberCoroutineScope()
    var validating by remember { mutableStateOf(false) }
        Surface(Modifier.fillMaxSize(), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Lieu et calcul hors ligne", fontSize = 24.sp)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Villes proposées — sélectionner volontairement", fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { cities.forEach { city -> AssistChip(onClick = { p = p.copy(city = city.name, zone = city.zone); latitude = city.lat.toString(); longitude = city.lon.toString() }, label = { Text(city.name) }, modifier = Modifier.testTag("city-${city.name}")) } }
                    Field("Nom du lieu", p.city, { p = p.copy(city = it) })
                    Field("Latitude (-89 à 89)", latitude, { latitude = it })
                    Field("Longitude (-180 à 180)", longitude, { longitude = it })
                    Field("Fuseau IANA (ex. Europe/Paris)", p.zone, { p = p.copy(zone = it.trim()) })
                    Text("Méthode de calcul")
                    Choice(PrayerEngine.methodLabels, PrayerEngine.methods.indexOf(p.method)) { p = p.copy(method = PrayerEngine.methods[it]) }
                    if (p.method == "OTHER") { Field("Angle Fajr", customFajr, { customFajr = it }); Field("Angle Isha", customIsha, { customIsha = it }) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = p.hanafi, onCheckedChange = { p = p.copy(hanafi = it) }); Text("Asr hanafite (sinon standard)") }
                    Text("Hautes latitudes")
                    val high = listOf("TWILIGHT_ANGLE", "SEVENTH_OF_THE_NIGHT", "MIDDLE_OF_THE_NIGHT")
                    Choice(listOf("Fraction selon les angles", "Septième de la nuit", "Milieu de la nuit"), high.indexOf(p.highLatitude)) { p = p.copy(highLatitude = high[it]) }
                    Text("Ajustements par prière (minutes)", fontWeight = FontWeight.Medium)
                    PrayerName.entries.forEach { prayer ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(prayer.label, Modifier.width(80.dp)); OutlinedButton(onClick = { p = p.copy(adjustments = p.adjustments + (prayer.name to ((p.adjustments[prayer.name] ?: 0) - 1).coerceAtLeast(-120))) }) { Text("−") }
                            Text("${p.adjustments[prayer.name] ?: 0}")
                            OutlinedButton(onClick = { p = p.copy(adjustments = p.adjustments + (prayer.name to ((p.adjustments[prayer.name] ?: 0) + 1).coerceAtMost(120))) }) { Text("+") }
                        }
                    }
                    Text("Silence horaire de l’adhan")
                    Choice(listOf("Désactivé") + (0..23).map { "%02d h".format(it) }, p.quietStart + 1) { p = p.copy(quietStart = it - 1) }
                    Choice(listOf("Désactivé") + (0..23).map { "%02d h".format(it) }, p.quietEnd + 1) { p = p.copy(quietEnd = it - 1) }
                    Text("Correction du calendrier hégirien")
                    Choice((-2..2).map { "$it jour(s)" }, p.hijriAdjustment + 2) { p = p.copy(hijriAdjustment = it - 2) }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(enabled = !validating, onClick = {
                        validating = true
                        scope.launch {
                        try {
                            val settings = p.copy(latitude = latitude.replace(',', '.').toDouble(), longitude = longitude.replace(',', '.').toDouble(), fajrAngle = customFajr.replace(',', '.').toDouble(), ishaAngle = customIsha.replace(',', '.').toDouble())
                            withContext(Dispatchers.Default) { settings.validate(); PrayerEngine.calculate(LocalDate.now(ZoneId.of(settings.zone)), settings) }
                            save(settings)
                        } catch (cancelled: CancellationException) { throw cancelled
                        } catch (_: Exception) { error = "Vérifier coordonnées, fuseau, angles et ajustements ; aucun horaire incohérent ne sera enregistré." }
                        finally { validating = false }
                        }
                    }, modifier = Modifier.testTag("save-prayer-settings")) { Text(if (validating) "Vérification…" else "Enregistrer les horaires") }
                    TextButton(onClick = onClose) { Text("Annuler") }
                }
            }
        }
}

@Composable internal fun Choice(labels: List<String>, selected: Int, change: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { open = true }) { Text(labels.getOrElse(selected) { "Choisir" }) }; DropdownMenu(expanded = open, onDismissRequest = { open = false }) { labels.forEachIndexed { index, text -> DropdownMenuItem(text = { Text(text) }, onClick = { change(index); open = false }) } } }
}

@Composable private fun PrayerCalendar(p: PrayerSettings, close: () -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now(ZoneId.of(p.zone))) }
    val days by produceState<List<PrayerDay>>(emptyList(), month, p) { value = withContext(Dispatchers.Default) { (1..month.lengthOfMonth()).mapNotNull { runCatching { PrayerEngine.calculate(month.atDay(it), p) }.getOrNull() } } }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f), shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { month = month.minusMonths(1) }) { Text("←") }
                    Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", french)), fontSize = 22.sp)
                    TextButton(onClick = { month = month.plusMonths(1) }) { Text("→") }
                }
                Row { Text("Jour", Modifier.weight(1f)); PrayerName.entries.forEach { Text(it.label, Modifier.weight(1f), fontSize = 12.sp) } }
                LazyColumn(Modifier.weight(1f)) { items(days) { day -> Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) { Text("${day.date.dayOfMonth}", Modifier.weight(1f)); day.moments.forEach { Text(timeText(it.epochMillis, p.zone), Modifier.weight(1f), fontSize = 12.sp) } } } }
                if (days.size != month.lengthOfMonth()) Text("Certains jours ne sont pas calculables avec ces paramètres.", fontSize = 11.sp)
                TextButton(onClick = close) { Text("Fermer") }
            }
        }
    }
}

@Composable fun DuasScreen(s: ProbeState, vm: ProbeViewModel, date: LocalDate) {
    val context = LocalContext.current
    val home = s.config.home
    val all = remember { Duas.all(context) }
    var filter by remember { mutableIntStateOf(0) }
    val daily = Duas.daily(context, date, home.duaOffset)
    var selected by remember { mutableStateOf<Dua?>(null) }
    val shown = when (filter) { 1 -> all.filter { it.id in home.favorites }; 2 -> home.duaHistory.mapNotNull { id -> all.firstOrNull { it.id == id } }; else -> listOf(daily) + all.filterNot { it.id == daily.id } }
    LaunchedEffect(daily.id) { vm.updateHome { it.copy(duaHistory = (listOf(daily.id) + it.duaHistory).distinct().take(60)) } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Invocations", "Favoris", "Historique").forEachIndexed { i, label -> FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label) }) }
            OutlinedButton(onClick = { vm.updateHome { it.copy(duaOffset = it.duaOffset + 1) } }) { Text("Autre doua") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Invocations coraniques · texte arabe Tanzil · changement quotidien hors ligne.", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary) }
            items(shown, key = { it.id }) { dua -> Panel(dua.title) {
                Text(dua.arabic, Modifier.fillMaxWidth(), fontSize = 28.sp, lineHeight = 46.sp, textAlign = TextAlign.Right, style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl))
                Text(dua.source, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = { vm.updateHome { it.copy(favorites = if (dua.id in it.favorites) it.favorites - dua.id else it.favorites + dua.id) } }) { Text(if (dua.id in home.favorites) "★ Favori" else "☆ Ajouter aux favoris") }
                    TextButton(onClick = { selected = dua; vm.updateHome { it.copy(duaHistory = (listOf(dua.id) + it.duaHistory).distinct().take(60)) } }) { Text("Agrandir") }
                }
            } }
            if (shown.isEmpty()) item { Text("Aucune invocation dans cette liste pour le moment.") }
            item { Text("Les textes sont reproduits sans modification. Les mentions et liens de sources sont disponibles dans Paramètres → Sources et licences. Aucune invocation ou référence de hadith générée.", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary) }
        }
    }
    selected?.let { dua -> Dialog(onDismissRequest = { selected = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) { Column(Modifier.padding(30.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(dua.arabic, Modifier.fillMaxWidth(), fontSize = 36.sp, lineHeight = 60.sp, textAlign = TextAlign.Right, style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl))
            Text(dua.source, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { selected = null }) { Text("Fermer") }
        } }
    } }
}
