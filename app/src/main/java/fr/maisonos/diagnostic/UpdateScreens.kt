package fr.maisonos.diagnostic

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.testTag
import fr.maisonos.core.*

@Composable internal fun UpdateSettingsPanel(s: ProbeState, vm: ProbeViewModel) {
    val state by vm.updates.state.collectAsState()
    val settings = s.config.home.updates
    var url by remember(settings.manifestUrl) { mutableStateOf(settings.manifestUrl) }
    var invalid by remember { mutableStateOf(false) }
    Panel("Mises à jour de Maison OS") {
        Text("Version installée : ${BuildConfig.VERSION_NAME}", fontSize = 17.sp)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(checked = settings.enabled, onCheckedChange = { enabled -> vm.updateHome { it.copy(updates = it.updates.copy(enabled = enabled)) } })
            Text("Vérifier à l’ouverture de l’application")
        }
        Text("Une nouvelle version est proposée à l’ouverture. Vos caméras, identifiants et dispositions sont conservés. Android vous demande de confirmer l’installation.", fontSize = 12.sp)
        Button(enabled = !state.busy, onClick = { vm.checkUpdates(true) }, modifier = Modifier.testTag("check-updates")) { Text(if (state.busy) "Vérification / téléchargement…" else "Rechercher une mise à jour") }
        state.release?.let { release -> OutlinedButton(onClick = vm.updates::show) { Text("Voir la version ${release.versionName}") } }
        if (state.message.isNotBlank()) Text(state.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        Field("URL HTTPS du manifeste de mise à jour", url, { url = it.take(2048); invalid = false })
        OutlinedButton(onClick = { try { requireHttps(url.trim()); vm.updateHome { it.copy(updates = it.updates.copy(manifestUrl = url.trim())) }; invalid = false } catch (_: Exception) { invalid = true } }) { Text("Enregistrer l’URL") }
        if (invalid) Text("Saisir une URL HTTPS valide, sans identifiants.", color = MaterialTheme.colorScheme.error)
        Text("La source par défaut est le dépôt GitHub de Maison OS. Aucun fichier n’est installé silencieusement : empreinte et signature sont contrôlées avant l’installateur Android.", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable internal fun UpdateDialog(state: UpdateUi, updates: AppUpdates) {
    val release = state.release ?: return
    AlertDialog(onDismissRequest = updates::dismiss, title = { Text("Maison OS ${release.versionName} disponible") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(release.notes.ifBlank { "Nouvelle version de Maison OS." })
            Text("Vos données sont conservées. Installation confirmée dans Android.", fontSize = 12.sp)
            if (state.busy) { LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth()); Text("${state.progress} %") }
            if (state.message.isNotBlank()) Text(state.message, fontSize = 12.sp)
        }
    }, confirmButton = { Button(enabled = !state.busy, onClick = { if (state.ready != null) updates.install() else updates.download() }) { Text(if (state.ready != null) "Installer" else "Télécharger la mise à jour") } }, dismissButton = { TextButton(onClick = updates::dismiss) { Text(if (state.busy) "Continuer dans l’application" else "Plus tard") } })
}
