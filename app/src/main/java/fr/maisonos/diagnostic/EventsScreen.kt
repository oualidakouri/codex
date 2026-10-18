@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package fr.maisonos.diagnostic

import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.*
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import fr.maisonos.core.*

@Composable fun EventsScreen(s: ProbeState, vm: ProbeViewModel) {
    var edited by remember { mutableStateOf<LocalRule?>(null) }
    val home = s.config.home
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel("Des événements qui donnent vie à votre maison") {
            Text("Une règle associe un événement local à un message et, si choisi, un son ou une vidéo personnelle. Les essais manuels sont toujours marqués SIMULATION.")
            Text("Exemple : importer une vidéo dans Paramètres, créer une règle Mouvement caméra frontale, choisir cette vidéo puis toucher Tester en simulation. Les connectiques sont reportées à une future intégration Home Assistant.", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            Button(onClick = { edited = LocalRule() }) { Text("+ Nouvelle règle") }
            Text("Déclencheurs V1 : courrier simulé, mouvement de la caméra frontale et test manuel. Aucun capteur Wi-Fi externe n’est prétendu connecté.", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        }
        home.rules.forEach { rule -> Panel(rule.name) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(when (rule.trigger) { "presence" -> "Mouvement caméra frontale"; "manual" -> "Déclenchement manuel"; else -> "Courrier · simulation locale" })
                    Text("Durée ${rule.durationSeconds}s · délai ${rule.delaySeconds}s · repos ${rule.cooldownSeconds}s", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                }
                Switch(checked = rule.enabled, onCheckedChange = { enabled -> vm.updateHome { it.copy(rules = it.rules.map { r -> if (r.id == rule.id) r.copy(enabled = enabled) else r }) } })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.testRule(rule) }) { Text("Tester en simulation") }
                OutlinedButton(onClick = { edited = rule }) { Text("Modifier") }
                TextButton(onClick = { vm.updateHome { it.copy(rules = it.rules.filterNot { r -> r.id == rule.id }) } }) { Text("Supprimer") }
            }
        } }
        Panel("Bibliothèque de médias") { Text("Importer vos sons, vidéos MP4 ou fonds d’écran dans Paramètres → Médias locaux. Aucun téléchargement externe n’est nécessaire.") }
    }
    edited?.let { original ->
        var rule by remember(original.id) { mutableStateOf(original) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { edited = null }, title = { Text("Événement local") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Message / nom de la règle", rule.name, { rule = rule.copy(name = it) })
                val triggers = listOf("mailbox", "presence", "manual")
                Choice(listOf("Courrier simulé", "Mouvement caméra frontale", "Manuel"), triggers.indexOf(rule.trigger)) { rule = rule.copy(trigger = triggers[it]) }
                Text("Média associé (facultatif)")
                MediaChoice(home.media, rule.mediaId, "Message uniquement") { rule = rule.copy(mediaId = it) }
                NumberSetting("Durée (secondes)", rule.durationSeconds, 1..300) { rule = rule.copy(durationSeconds = it) }
                NumberSetting("Délai (secondes)", rule.delaySeconds, 0..300) { rule = rule.copy(delaySeconds = it) }
                NumberSetting("Repos entre événements (secondes)", rule.cooldownSeconds, 0..86400) { rule = rule.copy(cooldownSeconds = it) }
                Text("Volume ${(rule.volume * 100).toInt()} %")
                Slider(value = rule.volume, onValueChange = { rule = rule.copy(volume = it) })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(onClick = {
            if (rule.name.isBlank() || rule.durationSeconds !in 1..300 || rule.cooldownSeconds !in 0..86400 || rule.delaySeconds !in 0..300) error = "Vérifier le nom et les durées."
            else { vm.updateHome { it.copy(rules = if (it.rules.any { r -> r.id == rule.id }) it.rules.map { r -> if (r.id == rule.id) rule else r } else it.rules + rule) }; edited = null }
        }) { Text("Enregistrer") } }, dismissButton = { TextButton(onClick = { edited = null }) { Text("Annuler") } })
    }
}

@Composable internal fun NumberSetting(label: String, value: Int, range: IntRange, change: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(value = text, onValueChange = { text = it; change(it.toIntOrNull()?.coerceIn(range) ?: range.first) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable fun EventOverlay(s: ProbeState, vm: ProbeViewModel) {
    val rule = s.eventRule
    val media = s.config.home.media.firstOrNull { it.id == rule?.mediaId }
    Dialog(onDismissRequest = vm::dismissAnimation, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF09111B)) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (s.eventSimulated) "SIMULATION · événement local" else "Mouvement local · caméra frontale", color = Color(0xFF6AE0C2), fontSize = 12.sp)
                    TextButton(onClick = vm::dismissAnimation) { Text("Fermer") }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (media != null) LocalEventPlayer(media, rule?.volume ?: 0.8f, vm)
                    else Text("✉", fontSize = 100.sp, color = Color(0xFF6AE0C2))
                }
                Text(rule?.name ?: "Tu as du courrier !", fontSize = 32.sp, fontWeight = FontWeight.Light, color = Color.White)
                Text("Retour automatique au tableau de bord", fontSize = 12.sp, color = Color(0xFF9FAFC4))
            }
        }
    }
}

@Composable private fun LocalEventPlayer(media: LocalMedia, volume: Float, vm: ProbeViewModel) {
    val context = LocalContext.current
    if (media.kind == MediaKind.IMAGE) {
        val bitmap by produceState<android.graphics.Bitmap?>(null, media.id) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                LocalFiles.bitmap(context, media.id)
            }.getOrNull()
        } }
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit) }
        return
    }
    val player = remember(media.id) { ExoPlayer.Builder(context).build().apply {
        this.volume = volume
        setMediaItem(MediaItem.fromUri(Uri.fromFile(LocalFiles.mediaFile(context, media.id))))
        prepare(); playWhenReady = true
    } }
    DisposableEffect(player) {
        val listener = object : Player.Listener { override fun onPlayerError(error: PlaybackException) { vm.log("Média événementiel illisible (${error.errorCodeName}).") } }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    if (media.kind == MediaKind.VIDEO) AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = false } }, modifier = Modifier.fillMaxSize())
    else Text("♫", fontSize = 100.sp, color = Color(0xFF6AE0C2))
}
