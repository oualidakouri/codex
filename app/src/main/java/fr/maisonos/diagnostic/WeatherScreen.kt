@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package fr.maisonos.diagnostic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import fr.maisonos.core.*
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Suppress("MissingPermission", "DEPRECATION")
private suspend fun cityFromLocation(context: Context): WeatherCity {
    check(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    val manager = context.getSystemService(LocationManager::class.java)
    check(manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
    val location: Location = withTimeout(25000) {
        if (Build.VERSION.SDK_INT >= 30) suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            manager.getCurrentLocation(LocationManager.NETWORK_PROVIDER, cancellation, ContextCompat.getMainExecutor(context)) { value ->
                if (continuation.isActive) {
                    if (value != null) continuation.resume(value) else continuation.resumeWithException(IllegalStateException("Position indisponible"))
                }
            }
        } else manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.takeIf { System.currentTimeMillis() - it.time < 7200000 } ?: error("Choisir une ville manuellement")
    }
    val name = withContext(Dispatchers.IO) { runCatching {
        Geocoder(context, Locale.FRENCH).getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()?.let { it.locality ?: it.subAdminArea }
    }.getOrNull() }
    return WeatherCity(name ?: "Ma position actuelle", location.latitude, location.longitude)
}

@Composable fun WeatherScreen(s: ProbeState, vm: ProbeViewModel) {
    val settings = s.config.home.weather
    val state by vm.weather.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var locating by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf("") }
    fun choose(city: WeatherCity) { vm.updateHome { it.copy(weather = WeatherSettings(city = city.name, latitude = city.latitude, longitude = city.longitude)) } }
    fun locate() { scope.launch {
        locating = true; locationError = ""
        try { choose(cityFromLocation(context)) }
        catch (_: Exception) { locationError = "Position indisponible. Activer la localisation Android et Internet, ou choisir votre ville ci-dessous." }
        finally { locating = false }
    } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed -> if (allowed) locate() else locationError = "Localisation refusée. La recherche par ville fonctionne sans cette autorisation." }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Panel("La météo de votre ville") {
            Text("Température extérieure, ressenti, vent et conditions météo. La température de batterie reste un indicateur distinct dans Diagnostics.", fontSize = 13.sp)
            if (settings.configured) {
                Text(settings.city, fontSize = 25.sp, color = MaterialTheme.colorScheme.primary)
                WeatherWidget(settings, vm, Instant.now(), detailed = true) {}
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = settings.enabled, onCheckedChange = { enabled -> vm.updateHome { it.copy(weather = it.weather.copy(enabled = enabled)) } }); Text("Actualiser la météo par Internet") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(enabled = !locating, onClick = { permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) { Text(if (locating) "Recherche de ma position…" else "Utiliser ma position actuelle") }
                OutlinedButton(enabled = s.config.home.prayer.configured, onClick = { val p = s.config.home.prayer; choose(WeatherCity(p.city.ifBlank { "Lieu de salat" }, p.latitude!!, p.longitude!!)) }) { Text("Utiliser ma ville de salat") }
                OutlinedButton(enabled = settings.configured && !state.loading && settings.enabled, onClick = { vm.weather.refresh(settings, true) }) { Text("Actualiser maintenant") }
            }
            if (locationError.isNotBlank()) Text(locationError, color = MaterialTheme.colorScheme.error)
            Text("La position est demandée une seule fois après votre action. Aucune localisation continue. Seules les coordonnées sont envoyées au service météo.", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
        }
        Panel("Choisir une ville sans géolocalisation") {
            Field("Ville", query, { query = it.take(100) })
            Button(enabled = query.trim().length >= 2 && !state.searching, onClick = { vm.weather.search(query) }) { Text(if (state.searching) "Recherche…" else "Rechercher ma ville") }
            state.cities.forEach { city -> OutlinedButton(onClick = { choose(city) }) { Text(city.name) } }
            if (state.error.isNotBlank()) Text(state.error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            Text("Données Open-Meteo · météo actuelle et prévisions des trois prochains jours. Actualisation toutes les 30 minutes au premier plan. Sans Internet, les dernières données de cette ville restent datées et marquées en cache.", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            TextButton(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://open-meteo.com/"))) }) { Text("Source : Open-Meteo (CC BY 4.0)") }
        }
    }
}

@Composable internal fun WeatherWidget(settings: WeatherSettings, vm: ProbeViewModel, now: Instant, detailed: Boolean = false, configure: () -> Unit) {
    val state by vm.weather.state.collectAsState()
    val reading = state.reading?.takeIf { it.latitude == settings.latitude && it.longitude == settings.longitude }
    if (!settings.configured) {
        Text("La météo de votre ville", fontSize = 20.sp)
        TextButton(onClick = configure) { Text("Choisir ma ville") }
    } else if (reading == null) {
        Text(settings.city, fontSize = 17.sp, maxLines = 1)
        Text(if (!settings.enabled) "Météo désactivée" else if (state.loading) "Chargement…" else "Météo indisponible", fontSize = 16.sp)
        TextButton(onClick = configure) { Text("Réglages météo") }
    } else {
        Text(settings.city, fontSize = 15.sp, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${"%.0f".format(reading.temperature)}°", fontSize = 46.sp, color = MaterialTheme.colorScheme.primary)
            Column { Text(reading.description, fontSize = 14.sp); Text("${"%.0f".format(reading.low)}° / ${"%.0f".format(reading.high)}° · ${"%.0f".format(reading.wind)} km/h", fontSize = 12.sp) }
        }
        val zone = runCatching { ZoneId.of(reading.zone) }.getOrDefault(ZoneId.systemDefault())
        val today = now.atZone(zone).toLocalDate()
        val future = reading.forecast.filter { runCatching { java.time.LocalDate.parse(it.date) > today }.getOrDefault(false) }.take(3)
        if (future.isEmpty()) Text("Prévisions à actualiser",fontSize = 11.sp,color = MaterialTheme.colorScheme.secondary)
        future.forEach { day ->
            val date = java.time.LocalDate.parse(day.date)
            val label = if (date == today.plusDays(1)) "Demain" else date.format(DateTimeFormatter.ofPattern("EEE dd/MM",Locale.FRENCH))
            Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label,fontSize = if (detailed) 16.sp else 12.sp)
                Text("${"%.0f".format(day.low)}° / ${"%.0f".format(day.high)}° · ${day.description} · ${day.rainChance?.let { "$it % pluie" } ?: "pluie n/d"}",fontSize = if (detailed) 15.sp else 11.sp)
            }
            if (detailed) Text("Conseil indicatif : ${day.clothing}",fontSize = 12.sp,color = MaterialTheme.colorScheme.secondary)
        }
        val cached = now.toEpochMilli() - reading.fetchedAt > 3600000 || state.error.isNotBlank() || !settings.enabled
        val date = Instant.ofEpochMilli(reading.observedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
        Text("${if (cached) "En cache · " else ""}Open-Meteo · $date", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.clickable(onClick = configure))
    }
}
