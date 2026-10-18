package fr.maisonos.diagnostic

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.maisonos.core.*

@Composable fun SwitchesScreen(s: ProbeState, vm: ProbeViewModel) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<LocalSwitch?>(null) }
    var remove by remember { mutableStateOf<LocalSwitch?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { editing = LocalSwitch() }) { Text("+ Ajouter un interrupteur") }
            OutlinedButton(onClick = {
                val intent = context.packageManager.getLaunchIntentForPackage("com.tuya.smartlife")
                if (intent != null) context.startActivity(intent) else vm.log("Smart Life n’est pas installé sur cette tablette.")
            }) { Text("Ouvrir Smart Life") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Panel("Smart Life / Tuya") {
                Text("Vos interrupteurs ont leur propre espace : allumer, éteindre, consulter l’état et classer par pièce.", fontSize = 14.sp)
                Text("Un appareil associé à Smart Life n’est pas automatiquement accessible en local. Le connecteur actuel prend en charge Tuya 3.3 avec son identifiant, sa clé locale et le point de données du relais. Les firmwares 3.4/3.5, Zigbee via passerelle et commandes cloud ne sont pas encore intégrés.", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                Text("La clé locale est un secret de l’appareil, différent du mot de passe Smart Life. Saisissez-la uniquement dans la tablette. Les commandes réelles restent à valider avec vos modèles.", fontSize = 12.sp)
                if (s.config.home.switches.isEmpty()) OutlinedButton(onClick = { vm.updateHome { it.copy(switches = it.switches + LocalSwitch(name = "Lampe de démonstration")) } }, modifier = Modifier.testTag("add-simulated-switch")) { Text("Essayer un interrupteur simulé") }
            } }
            items(s.config.home.switches, key = { it.id }) { device ->
                val state = s.switchStates[device.id] ?: SwitchUi(message = if (device.kind == SwitchKind.SIMULATION) "SIMULATION · aucun appareil connecté" else "État non vérifié")
                Panel(device.name) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(device.room.ifBlank { "Sans pièce" }, color = MaterialTheme.colorScheme.secondary)
                        Text(if (device.kind == SwitchKind.SIMULATION) "SIMULATION" else "LOCAL · TUYA 3.3", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                    Text(when (state.on) { true -> "Allumé"; false -> "Éteint"; null -> "État inconnu" }, fontSize = 28.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(enabled = !state.busy, onClick = { vm.switchCommand(device, true) }, modifier = Modifier.testTag("switch-on-${device.id}")) { Text("Allumer") }
                        OutlinedButton(enabled = !state.busy, onClick = { vm.switchCommand(device, false) }) { Text("Éteindre") }
                        OutlinedButton(enabled = !state.busy, onClick = { vm.switchCommand(device) }) { Text("Lire l’état") }
                        TextButton(onClick = { editing = device }) { Text("Configurer") }
                        TextButton(onClick = { remove = device }) { Text("Supprimer") }
                    }
                    Text(state.message, fontSize = 12.sp)
                }
            }
        }
    }
    editing?.let { original -> SwitchEditor(original, { editing = null }) { device ->
        vm.updateHome { home -> home.copy(switches = home.switches.filterNot { it.id == device.id } + device) }; editing = null
    } }
    remove?.let { device -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("Supprimer ${device.name} ?") }, text = { Text("L’appareil est retiré de Maison OS. Aucun ordre n’est envoyé à l’interrupteur.") }, confirmButton = { TextButton(onClick = { vm.updateHome { it.copy(switches = it.switches.filterNot { it.id == device.id }) }; remove = null }) { Text("Supprimer") } }, dismissButton = { TextButton(onClick = { remove = null }) { Text("Annuler") } }) }
}

@Composable private fun SwitchEditor(initial: LocalSwitch, close: () -> Unit, save: (LocalSwitch) -> Unit) {
    var device by remember(initial.id) { mutableStateOf(initial) }
    var point by remember { mutableStateOf(initial.dataPoint.toString()) }
    var reveal by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.95f)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Interrupteur Smart Life", fontSize = 24.sp)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field("Nom", device.name, { device = device.copy(name = it.take(80)) })
                    Field("Pièce", device.room, { device = device.copy(room = it.take(80)) })
                    Choice(listOf("Simulation · aucun appareil commandé", "Appareil local Tuya 3.3"), if (device.kind == SwitchKind.SIMULATION) 0 else 1) { device = device.copy(kind = if (it == 0) SwitchKind.SIMULATION else SwitchKind.TUYA_33) }
                    if (device.kind == SwitchKind.TUYA_33) {
                        Field("IPv4 locale de l’interrupteur", device.host, { device = device.copy(host = it.trim()) })
                        Field("Identifiant de l’appareil (device ID)", device.deviceId, { device = device.copy(deviceId = it.trim().take(128)) })
                        OutlinedTextField(device.password, { device = device.copy(password = it) }, label = { Text("Clé locale Tuya · 16 caractères") },
                            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = { TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "Masquer" else "Afficher") } }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Field("Point de données du relais (ex. 1)", point, { point = it })
                        Text("Le numéro dépend du modèle. Utilisez le point booléen du relais documenté pour votre appareil. Pas de tentative automatique sur d’autres points.", fontSize = 12.sp)
                        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.tuya.com/en/docs/iot"))) }) { Text("Documentation officielle Tuya") }
                    }
                    if (error) Text("Vérifier le nom, l’IPv4, l’identifiant, la clé locale de 16 octets et le point de données (1 à 255).", color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { try {
                        val result = device.copy(dataPoint = point.toInt()); result.validate()
                        require(result.kind == SwitchKind.SIMULATION || result.password.toByteArray().size == 16)
                        save(result)
                    } catch (_: Exception) { error = true } }) { Text("Enregistrer") }
                    TextButton(onClick = close) { Text("Annuler") }
                }
            }
        }
    }
}
