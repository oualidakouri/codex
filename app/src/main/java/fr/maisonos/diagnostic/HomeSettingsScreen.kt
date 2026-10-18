@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package fr.maisonos.diagnostic

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import fr.maisonos.core.*
import kotlinx.coroutines.*

@Composable fun HomeSettingsScreen(s: ProbeState, vm: ProbeViewModel, navigate: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val home = s.config.home
    var kind by remember { mutableStateOf(MediaKind.AUDIO) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importMedia(uri, kind) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) scope.launch {
        try { vm.backup(uri); vm.log("Sauvegarde ZIP créée avec paramètres et médias, sans mots de passe ni URL RTSP.") }
        catch (_: Exception) { vm.log("Sauvegarde impossible. Vérifier l’espace disponible et la limite de 256 Mo.") }
    } }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.restore(uri) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        vm.updateHome { it.copy(presenceOnLaunch = allowed) }; vm.enablePresence(allowed)
    }
    var newProfile by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    var reset by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }
    var restoreConfirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel("L’apparence de votre maison") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 3) {
                ThemeCatalog.themes.forEach { theme ->
                    Surface(color = androidx.compose.ui.graphics.Color(theme.background), contentColor = androidx.compose.ui.graphics.Color(theme.text), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        border = BorderStroke(if (home.theme == theme.id) 3.dp else 1.dp, if (home.theme == theme.id) androidx.compose.ui.graphics.Color(theme.accent) else androidx.compose.ui.graphics.Color(theme.muted)),
                        modifier = Modifier.width(185.dp).height(100.dp).clickable { vm.updateHome { it.copy(theme = theme.id, dark = theme.dark) } }) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Text(if (home.theme == theme.id) "✓ ${theme.name}" else theme.name, fontSize = 15.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(theme.accent, theme.surface, theme.muted).forEach { color -> Box(Modifier.size(24.dp).background(androidx.compose.ui.graphics.Color(color), androidx.compose.foundation.shape.CircleShape)) } }
                        }
                    }
                }
            }
            Text("Fond d’écran personnel")
            MediaChoice(home.media.filter { it.kind == MediaKind.IMAGE }, home.wallpaperId, "Fond uni") { id -> vm.updateHome { it.copy(wallpaperId = id) } }
            Text("La disposition et la transparence se règlent depuis Accueil → Personnaliser. Les dispositions paysage et portrait sont conservées séparément.", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        }
        UpdateSettingsPanel(s, vm)
        Panel("Météo de ma ville") {
            Text(if (home.weather.configured) home.weather.city else "Choisissez votre ville ou utilisez la position actuelle.")
            Button(onClick = { navigate("Météo") }) { Text("Configurer ma météo") }
            Text("Température extérieure via Open-Meteo. La température de batterie est affichée séparément dans l’état de la tablette.", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        }
        Panel("Profils de dashboard") {
            home.profiles.forEach { profile -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = profile.id == home.profile().id, onClick = { vm.updateHome { it.copy(activeProfile = profile.id) } })
                Text("${profile.name} · ${profile.pages} page(s)", Modifier.weight(1f))
                if (home.profiles.size > 1) TextButton(onClick = { vm.updateHome { cfg -> cfg.copy(profiles = cfg.profiles.filterNot { it.id == profile.id }, activeProfile = if (cfg.activeProfile == profile.id) "" else cfg.activeProfile) } }) { Text("Supprimer") }
            } }
            Button(onClick = { newProfile = true }) { Text("+ Nouveau profil") }
            OutlinedButton(onClick = { reset = true }) { Text("Réinitialiser la disposition du profil actif") }
        }
        Panel("Caméras et consommation") {
            Button(onClick = { vm.updateHome { it.withCameraTiles() } }) { Text("Afficher mes deux caméras en haut de l’accueil") }
            Choice(listOf("1 flux simultané · économie", "2 flux simultanés · mosaïque"), home.maxStreams - 1) { count -> vm.updateHome { it.copy(maxStreams = count + 1) } }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = home.liveDashboard, onCheckedChange = { live -> vm.updateHome { it.copy(liveDashboard = live) } }); Text("Aperçus en direct dans les widgets") }
            Text("Utiliser les sous-flux basse résolution. Les vidéos non visibles, la veille et les alertes thermiques suspendent les lecteurs. Le plein écran peut utiliser une URL principale distincte.", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        }
        Panel("Écran mural et présence") {
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = s.config.keepAwake, onCheckedChange = { vm.save(s.config.copy(keepAwake = it)) }); Text("Maintenir l’écran actif au premier plan") }
            Choice(listOf("Veille après 30 s", "Veille après 1 min", "Veille après 5 min", "Veille après 15 min"), listOf(30, 60, 300, 900).indexOf(s.config.idleSeconds)) { vm.save(s.config.copy(idleSeconds = listOf(30, 60, 300, 900)[it])) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = home.presenceOnLaunch, onCheckedChange = { enabled -> if (enabled) cameraPermission.launch(Manifest.permission.CAMERA) else { vm.updateHome { it.copy(presenceOnLaunch = false) }; vm.enablePresence(false) } })
                Text("Réveil par mouvement au premier plan")
            }
            Text("Analyse frontale locale, aucune image conservée. La veille est un écran sombre logiciel ; une extinction physique empêche cette détection.", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            Button(onClick = vm::sleep) { Text("Tester la veille") }
            OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } }) { Text("Choisir Maison OS comme lanceur (facultatif)") }
            OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }) { Text("Réglages Android de batterie") }
            Text("Le lanceur peut permettre le retour au démarrage ; le comportement dépend du constructeur. Les alarmes exactes et notifications se règlent dans Salat.", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
        }
        Panel("Médias locaux") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { kind = MediaKind.AUDIO; picker.launch(arrayOf("audio/*")) }) { Text("+ Audio") }
                OutlinedButton(onClick = { kind = MediaKind.VIDEO; picker.launch(arrayOf("video/mp4", "video/webm")) }) { Text("+ Vidéo") }
                OutlinedButton(onClick = { kind = MediaKind.IMAGE; picker.launch(arrayOf("image/png", "image/jpeg", "image/webp")) }) { Text("+ Image") }
            }
            home.media.forEach { media -> Text("${media.name} · ${media.kind.name.lowercase()} · ${"%.1f".format(media.bytes / 1048576.0)} Mo", fontSize = 12.sp) }
            if (home.media.isEmpty()) Text("Bibliothèque vide. Importer une récitation pour l’adhan, un fond d’écran ou une vidéo événementielle.", fontSize = 12.sp)
            Text("100 Mo maximum par fichier. Les formats décodables dépendent de la tablette.", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
        }
        Panel("Sauvegarde et restauration") {
            Button(onClick = { backup.launch("maison-os-v1-sauvegarde.zip") }) { Text("Exporter paramètres et médias (ZIP)") }
            OutlinedButton(onClick = { restoreConfirm = true }) { Text("Restaurer une sauvegarde ZIP") }
            Text("L’archive contient les profils, réglages, règles, favoris et médias. Les mots de passe et URL RTSP sont exclus : les ressaisir après restauration. Limite totale 256 Mo.", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        }
        Panel("À propos de Maison OS ${BuildConfig.VERSION_NAME}") {
            Text("Application Android native, locale et sans serveur obligatoire. Paramètres chiffrés avec Android Keystore. Les caméras sont testées séparément selon leurs protocoles.", fontSize = 12.sp)
            OutlinedButton(onClick = { licenses = true }) { Text("Sources et licences") }
            s.logs.lastOrNull()?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary) }
        }
    }
    if (newProfile) AlertDialog(onDismissRequest = { newProfile = false }, title = { Text("Nouveau profil") }, text = { Field("Nom", profileName, { profileName = it.take(80) }) }, confirmButton = { TextButton(enabled = profileName.isNotBlank(), onClick = {
        val profile = LayoutProfile(name = profileName.trim()); vm.updateHome { it.copy(profiles = it.profiles + profile, activeProfile = profile.id) }; newProfile = false; profileName = ""
    }) { Text("Créer") } }, dismissButton = { TextButton(onClick = { newProfile = false }) { Text("Annuler") } })
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text("Réinitialiser ce profil ?") }, text = { Text("Les autres profils et vos équipements seront conservés.") }, confirmButton = { TextButton(onClick = {
        vm.updateHome { cfg -> val id = cfg.profile().id; cfg.copy(profiles = cfg.profiles.map { if (it.id == id) LayoutProfile(id = id, name = it.name) else it }) }; reset = false
    }) { Text("Réinitialiser") } }, dismissButton = { TextButton(onClick = { reset = false }) { Text("Annuler") } })
    if (restoreConfirm) AlertDialog(onDismissRequest = { restoreConfirm = false }, title = { Text("Restaurer une sauvegarde ?") }, text = { Text("Les réglages actuels seront remplacés. Exportez-les d’abord si vous souhaitez les conserver.") }, confirmButton = { TextButton(onClick = { restoreConfirm = false; restore.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Choisir l’archive") } }, dismissButton = { TextButton(onClick = { restoreConfirm = false }) { Text("Annuler") } })
    if (licenses) {
        val notice = remember { context.assets.open("SOURCES.txt").bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { licenses = false }, title = { Text("Sources et licences") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(notice, fontSize = 12.sp)
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://tanzil.net"))) }) { Text("Consulter Tanzil (Internet facultatif)") }
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://quranenc.com/en/browse/french_montada/"))) }) { Text("Consulter la traduction et ses mises à jour") }
            }
        }, confirmButton = { TextButton(onClick = { licenses = false }) { Text("Fermer") } })
    }
}
