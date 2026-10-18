@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package fr.maisonos.diagnostic

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import fr.maisonos.core.*

@Composable internal fun QuranControls(settings: QuranSettings, compact: Boolean = false, configure: () -> Unit = {}) {
    val context = LocalContext.current
    val audio by QuranPlayback.state.collectAsState()
    val adhan by AdhanPlayback.active.collectAsState()
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    Text(if (audio.active) audio.title else settings.title, fontSize = if (compact) 14.sp else 22.sp, maxLines = 2)
    if (audio.buffering) Text("Chargement du flux…",fontSize = 12.sp)
    else if (audio.playing) Text("Lecture en cours",fontSize = 12.sp,color = MaterialTheme.colorScheme.primary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = !adhan && !audio.buffering && (!audio.playing || audio.title != settings.title) && runCatching { settings.playbackUrl() }.isSuccess, onClick = {
            if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            QuranPlayback.play(context,settings)
        }, modifier = Modifier.testTag("quran-play")) { Text("▶ Lecture") }
        OutlinedButton(enabled = audio.active || audio.buffering, onClick = { QuranPlayback.stop(context) }, modifier = Modifier.testTag("quran-stop")) { Text("■ Arrêter") }
        if (compact) TextButton(onClick = configure) { Text("Choisir") }
    }
    if (audio.error.isNotBlank()) Text(audio.error,fontSize = 11.sp,color = MaterialTheme.colorScheme.error)
    if (adhan) Text("Adhan prioritaire",fontSize = 11.sp,color = MaterialTheme.colorScheme.secondary)
}

@Composable internal fun QuranUrlPanel(s: ProbeState, vm: ProbeViewModel) {
    val q = s.config.home.quran
    var url by remember(q.url) { mutableStateOf(q.url) }
    var invalid by remember { mutableStateOf(false) }
    Panel("Lecteur de Coran · flux personnel") {
        Text("Collez une URL audio HTTP(S) : MP3, AAC ou playlist HLS (.m3u8). Un flux de radio est lu en continu ; le choix de sourate est proposé par le catalogue MP3Quran.",fontSize = 12.sp)
        Field("URL réseau du Coran",url,{ url = it.take(2048); invalid = false })
        Button(onClick = { try { requireAudioUrl(url.trim()); vm.updateHome { it.copy(quran = it.quran.copy(custom = true,url = url.trim())) }; invalid = false } catch (_: Exception) { invalid = true } },modifier = Modifier.testTag("save-quran-url")) { Text("Enregistrer mon flux") }
        if (invalid) Text("URL audio HTTP(S) valide requise, sans identifiants intégrés.",color = MaterialTheme.colorScheme.error)
        QuranControls(q)
    }
}

@Composable fun QuranScreen(s: ProbeState, vm: ProbeViewModel) {
    val q = s.config.home.quran
    val catalog by vm.quranCatalog.state.collectAsState()
    var search by remember { mutableStateOf("") }
    var selectingSurah by remember { mutableStateOf(false) }
    var volume by remember(q.volume) { mutableFloatStateOf(q.volume) }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(14.dp)) {
        QuranUrlPanel(s,vm)
        Panel("Récitants et sourates · MP3Quran") {
            Text("Sept récitants connus et les noms des 114 sourates sont disponibles dès le départ. Actualiser charge le catalogue MP3Quran ; l’audio reste diffusé par ses serveurs et nécessite le réseau. Choisir un récitant puis une sourate.",fontSize = 12.sp)
            Button(enabled = !catalog.busy,onClick = vm.quranCatalog::refresh) { Text(if (catalog.busy) "Chargement du catalogue…" else "Charger / actualiser les récitants") }
            if (catalog.error.isNotBlank()) Text(catalog.error,fontSize = 12.sp,color = MaterialTheme.colorScheme.error)
            if (catalog.catalog.recitations.isNotEmpty()) {
                Field("Rechercher un récitant",search,{ search = it.take(100) })
                val results = catalog.catalog.recitations.filter { search.isBlank() || it.name.contains(search,true) || it.reading.contains(search,true) }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                    items(results.take(100),key = { "${it.reciterId}/${it.moshafId}" }) { r ->
                        TextButton(onClick = {
                            val number = q.surah.takeIf { it in r.surahs } ?: r.surahs.first()
                            val name = catalog.catalog.surahs.firstOrNull { it.id == number }?.name.orEmpty()
                            vm.updateHome { it.copy(quran = it.quran.copy(custom = false,reciterId = r.reciterId,moshafId = r.moshafId,reciter = r.name,server = r.server,surah = number,surahName = name)) }
                        },modifier = Modifier.fillMaxWidth()) { Text("${if (!q.custom && q.reciterId == r.reciterId && q.moshafId == r.moshafId) "✓ " else ""}${r.name} · ${r.reading}") }
                    }
                }
                OutlinedButton(enabled = q.reciterId > 0,onClick = { selectingSurah = true }) { Text("Choisir la sourate : ${q.surah} · ${q.surahName}") }
                if (q.server.isNotBlank()) OutlinedButton(onClick = { vm.updateHome { it.copy(quran = it.quran.copy(custom = false)) } }) { Text("Utiliser le récitant sélectionné") }
            }
            TextButton(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://mp3quran.net/ar/api"))) }) { Text("Source du catalogue : MP3Quran") }
        }
        Panel("Lecture et volume") {
            Text("Volume de l’application : ${(volume * 100).toInt()} %")
            Slider(value = volume,onValueChange = { volume = it; QuranPlayback.volume(context,it) },onValueChangeFinished = { vm.updateHome { it.copy(quran = it.quran.copy(volume = volume)) } })
            Text("La lecture continue pendant la veille et les changements d’écran, avec une notification d’arrêt. L’adhan interrompt la récitation ; elle ne reprend pas automatiquement. Internet ou l’accès à votre serveur est nécessaire.",fontSize = 12.sp,color = MaterialTheme.colorScheme.secondary)
        }
    }
    if (selectingSurah) AlertDialog(onDismissRequest = { selectingSurah = false },title = { Text("Choisir une sourate") },text = {
        val available = catalog.catalog.recitations.firstOrNull { it.reciterId == q.reciterId && it.moshafId == q.moshafId }?.surahs.orEmpty()
        LazyColumn(Modifier.heightIn(max = 340.dp)) { items(catalog.catalog.surahs.filter { it.id in available },key = { it.id }) { surah ->
            TextButton(onClick = { vm.updateHome { it.copy(quran = it.quran.copy(custom = false,surah = surah.id,surahName = surah.name)) }; selectingSurah = false },modifier = Modifier.fillMaxWidth()) { Text("${surah.id} · ${surah.name}") }
        } }
    },confirmButton = { TextButton(onClick = { selectingSurah = false }) { Text("Fermer") } })
}
