package fr.maisonos.diagnostic

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.maisonos.core.*

@Composable fun HomeCameraPage(s: ProbeState, vm: ProbeViewModel) {
    var editing by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<CameraConfig?>(null) }
    var deleting by remember { mutableStateOf<CameraConfig?>(null) }
    val cameras = s.config.home.cameras
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(enabled = !s.busy, onClick = { vm.newCamera(); editing = true }, modifier = Modifier.testTag("add-camera")) { Text("+ Ajouter / rechercher une caméra") }
            OutlinedButton(enabled = !s.busy && s.config.camera.host.isNotBlank(), onClick = { editing = true }) { Text("Réglages de la caméra sélectionnée") }
        }
        Text("${cameras.size} caméra(s) enregistrée(s) · aperçu limité à ${s.config.home.maxStreams} flux simultané(s). La découverte n’a pas cette limite.", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(cameras, key = { it.id }) { camera -> Panel(camera.name) {
                Text("${camera.room.ifBlank { "Emplacement non renseigné" }} · ${camera.host}", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                Text(if (camera.rtspUrl.isBlank()) "Flux RTSP à renseigner ou obtenir via ONVIF" else "Flux configuré · connexion vérifiée à l’ouverture", fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !s.busy && camera.rtspUrl.isNotBlank(), onClick = { vm.selectCamera(camera); preview = camera }) { Text("Ouvrir la vidéo") }
                    OutlinedButton(enabled = !s.busy, onClick = { vm.selectCamera(camera); editing = true }) { Text("Configurer / tester") }
                    TextButton(onClick = { deleting = camera }) { Text("Supprimer") }
                }
            } }
            if (cameras.isEmpty()) item { Panel("Vos caméras, sans cloud") { Text("Ajoutez les caméras par découverte ou adresse IP. Si une caméra ne répond pas au scan, l’ajout manuel reste indépendant.") } }
            item { Panel("Une caméra absente du scan ?") {
                Text("Deux ports sur la même adresse correspondent souvent à DVRIP et ONVIF, pas à deux caméras. Essayez la recherche approfondie ou ajoutez la seconde par son IPv4 réelle. Un équipement peut être visible dans iCSee via son cloud sans proposer les mêmes services locaux.", fontSize = 13.sp)
                Text("Aucun compte constructeur n’est requis. Choisissez un sous-flux basse résolution pour les widgets et, si disponible, un flux principal distinct pour le plein écran.", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            } }
        }
    }
    if (editing) Dialog(onDismissRequest = { vm.stopVideo(); vm.stopPtz(); editing = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Caméra locale", fontSize = 24.sp); TextButton(onClick = { vm.stopVideo(); vm.stopPtz(); editing = false }) { Text("Fermer") } }
            Box(Modifier.weight(1f)) { CameraPage(s, vm, false) {} }
        } }
    }
    preview?.let { original ->
        val camera = s.config.home.cameras.firstOrNull { it.id == original.id } ?: original
        CameraFullscreen(camera, s, vm) { preview = null }
    }
    deleting?.let { camera -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Supprimer ${camera.name} ?") }, text = { Text("La caméra est retirée de Maison OS. Aucun réglage matériel n’est modifié.") }, confirmButton = { TextButton(onClick = { vm.removeCamera(camera.id); deleting = null }) { Text("Supprimer") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } }) }
}

@Composable internal fun CameraFullscreen(camera: CameraConfig, s: ProbeState, vm: ProbeViewModel, close: () -> Unit) {
    var main by remember(camera.id) { mutableStateOf(true) }
    val stream = if (main && camera.mainRtspUrl.isNotBlank()) camera.copy(rtspUrl = camera.mainRtspUrl) else camera
    LaunchedEffect(s.busy, camera.id) { if (!s.busy && s.config.camera.id == camera.id && !s.video) vm.startVideo() }
    DisposableEffect(camera.id) { onDispose { vm.stopVideo(); vm.stopPtz() } }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Black, contentColor = androidx.compose.ui.graphics.Color.White) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(camera.name, fontSize = 22.sp, modifier = Modifier.padding(top = 8.dp))
                    if (camera.mainRtspUrl.isNotBlank()) OutlinedButton(onClick = { main = !main }) { Text(if (main) "Passer au sous-flux" else "Passer au flux principal") }
                    OutlinedButton(enabled = !s.busy, onClick = vm::inspectOnvif) { Text("ONVIF / PTZ") }
                    OutlinedButton(enabled = !s.busy, onClick = vm::connectDvrip) { Text("DVRIP / PTZ") }
                    TextButton(onClick = close) { Text("Fermer") }
                }
                if (s.foreground && !s.sleeping && !s.animation && s.resources?.protect != true && s.video) key(stream.rtspUrl) { RtspVideo(stream, vm, Modifier.fillMaxWidth().weight(1f)) }
                else Box(Modifier.weight(1f)) { Text("Vidéo en pause ou connexion en cours.") }
                PtzControls(s, vm)
            }
        }
    }
}
