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
import androidx.compose.ui.unit.*
import fr.maisonos.core.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun newsDate(epoch: Long) = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
@Composable private fun ArticleRow(article: NewsArticle, compact: Boolean = false) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(article.url))) } }.padding(vertical = if (compact) 4.dp else 10.dp),verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(article.title,fontSize = if (compact) 13.sp else 17.sp,maxLines = if (compact) 2 else 4)
        Text(article.source + article.publishedAt?.let { " · ${newsDate(it)}" }.orEmpty(),fontSize = 10.sp,color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable internal fun NewsWidget(home: HomeConfig, vm: ProbeViewModel, open: () -> Unit) {
    val state by vm.news.state.collectAsState()
    val city = home.weather.city.ifBlank { home.prayer.city }
    val localUrl = home.news.localFeed(city)
    val france = state.france?.takeIf { it.url == home.news.franceUrl }
    val local = state.local?.takeIf { it.url == localUrl }
    Column(Modifier.fillMaxSize()) {
        if (!home.news.enabled) Text("Actualités désactivées",fontSize = 13.sp)
        else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text("FRANCE",fontSize = 11.sp,color = MaterialTheme.colorScheme.primary)
                if (france != null && (state.error.isNotBlank() || System.currentTimeMillis() - france.fetchedAt > 1800000)) Text("En cache · ${newsDate(france.fetchedAt)}",fontSize = 10.sp,color = MaterialTheme.colorScheme.secondary)
                france?.items?.take(2)?.forEach { ArticleRow(it,true) }
                if (france == null) Text(if (state.busy) "Chargement…" else "Aucun titre disponible",fontSize = 12.sp)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(home.news.city.ifBlank { city }.ifBlank { "MA VILLE" },fontSize = 11.sp,color = MaterialTheme.colorScheme.primary,maxLines = 1)
                local?.items?.take(2)?.forEach { ArticleRow(it,true) }
                if (local == null) Text(if (localUrl == null) "Choisir votre ville" else if (state.busy) "Chargement…" else "Aucun titre local disponible",fontSize = 12.sp)
            }
        }
        TextButton(onClick = open) { Text("Lire / configurer") }
    }
}

@Composable internal fun NewsSettingsPanel(s: ProbeState, vm: ProbeViewModel) {
    val home = s.config.home
    var city by remember(home.news.city) { mutableStateOf(home.news.city) }
    var france by remember(home.news.franceUrl) { mutableStateOf(home.news.franceUrl) }
    var local by remember(home.news.localUrl) { mutableStateOf(home.news.localUrl) }
    var invalid by remember { mutableStateOf(false) }
    Panel("Actualités France et ma ville") {
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = home.news.enabled,onCheckedChange = { enabled -> vm.updateHome { it.copy(news = it.news.copy(enabled = enabled)) } }); Text("Actualiser les titres par Internet") }
        Field("Ville locale (vide = ville météo ou salat)",city,{ city = it.take(150) })
        Field("Flux RSS France (HTTPS)",france,{ france = it.take(2048) })
        TextButton(onClick = { france = "https://news.google.com/rss?hl=fr&gl=FR&ceid=FR:fr" }) { Text("Utiliser Google Actualités France") }
        Field("Flux RSS local personnel (facultatif)",local,{ local = it.take(2048) })
        Button(onClick = { try {
            val settings = home.news.copy(city = city.trim(),franceUrl = france.trim(),localUrl = local.trim()); settings.validate()
            vm.updateHome { it.copy(news = settings) }; invalid = false
        } catch (_: Exception) { invalid = true } }) { Text("Enregistrer les actualités") }
        if (invalid) Text("Vérifier les URL HTTPS des flux RSS.",color = MaterialTheme.colorScheme.error)
        Text("France : Franceinfo par défaut. Local : recherche Google Actualités sur le nom de votre ville, ou votre RSS personnalisé. Titres, sources, dates et liens uniquement ; les résultats locaux peuvent mentionner la ville sans concerner exclusivement votre quartier.",fontSize = 12.sp,color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable fun NewsScreen(s: ProbeState, vm: ProbeViewModel) {
    val home = s.config.home
    val state by vm.news.state.collectAsState()
    var settings by remember { mutableStateOf(false) }
    val city = home.weather.city.ifBlank { home.prayer.city }
    val localUrl = home.news.localFeed(city)
    val france = state.france?.takeIf { it.url == home.news.franceUrl }
    val local = state.local?.takeIf { it.url == localUrl }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { settings = !settings }) { Text(if (settings) "Voir les titres" else "Configurer") }
            Button(enabled = home.news.enabled && !state.busy,onClick = { vm.news.refresh(home.news,city,true) }) { Text(if (state.busy) "Chargement…" else "Actualiser") }
        }
        if (settings) Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { NewsSettingsPanel(s,vm) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.error.isNotBlank()) item { Text(state.error,fontSize = 12.sp,color = MaterialTheme.colorScheme.secondary) }
            item { Text("France",fontSize = 24.sp,color = MaterialTheme.colorScheme.primary); france?.let { Text("Titres récupérés le ${newsDate(it.fetchedAt)}${if (System.currentTimeMillis() - it.fetchedAt > 1800000) " · cache" else ""}",fontSize = 11.sp) } }
            items(france?.items.orEmpty(),key = { "fr/${it.url}" }) { ArticleRow(it) }
            if (france == null) item { Text("Pas de titre disponible. Les actualités nécessitent Internet ; aucun titre de démonstration n’est présenté comme réel.") }
            item { HorizontalDivider(); Text(home.news.city.ifBlank { city }.ifBlank { "Ma ville" },fontSize = 24.sp,color = MaterialTheme.colorScheme.primary); local?.let { Text("Titres récupérés le ${newsDate(it.fetchedAt)}",fontSize = 11.sp) } }
            items(local?.items.orEmpty(),key = { "local/${it.url}" }) { ArticleRow(it) }
            if (local == null) item { Text(if (localUrl == null) "Choisir une ville dans Configurer ou Météo." else "Aucun titre local disponible pour le moment.") }
        }
    }
}
