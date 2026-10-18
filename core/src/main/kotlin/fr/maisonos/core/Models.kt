package fr.maisonos.core

import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
data class CameraConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Ma caméra",
    val room: String = "",
    val host: String = "",
    val username: String = "",
    val password: String = "",
    val dvripPort: Int = 34567,
    val onvifPort: Int = 8899,
    val onvifPath: String = "/onvif/device_service",
    val rtspUrl: String = "",
    val mainRtspUrl: String = "",
    val rtspVendorPath: Boolean = false,
    val dvripPtzConfirmed: Boolean = false,
) {
    fun validate() {
        LocalEndpoint.requireHost(host)
        require(dvripPort in 1..65535 && onvifPort in 1..65535) { "Port invalide" }
        require(onvifPath.startsWith('/') && !onvifPath.startsWith("//") && !onvifPath.contains('?')) { "Chemin ONVIF invalide" }
        if (rtspUrl.isNotBlank()) LocalEndpoint.requireUrl(rtspUrl, setOf("rtsp"), host)
        if (mainRtspUrl.isNotBlank()) LocalEndpoint.requireUrl(mainRtspUrl, setOf("rtsp"), host)
        require(username.length <= 128 && password.length <= 256 && name.length <= 100)
    }
    fun exportSafe() = copy(password = "", rtspUrl = "", mainRtspUrl = "", dvripPtzConfirmed = false)
    override fun toString() = "CameraConfig(credentials=redacted)"
}

/** This first probe intentionally accepts only literal private LAN IPv4s. No DNS or cloud. */
object LocalEndpoint {
    fun requireHost(host: String): String {
        val p = host.split('.')
        require(p.size == 4 && p.all { it.isNotEmpty() && it.all(Char::isDigit) && it.toIntOrNull() in 0..255 && (it == "0" || !it.startsWith('0')) }) {
            "Saisir une IPv4 locale littérale (sans nom DNS)."
        }
        val a = p.map(String::toInt)
        require(a[0] == 10 || (a[0] == 172 && a[1] in 16..31) || (a[0] == 192 && a[1] == 168) || (a[0] == 169 && a[1] == 254)) {
            "Seules les adresses privées du réseau local sont autorisées."
        }
        require(a[3] != 255 && a[3] != 0) { "Adresse de diffusion/réseau non acceptée par cette version." }
        return host
    }
    fun requireUrl(url: String, schemes: Set<String>, expectedHost: String? = null): URI {
        val uri = URI(url)
        require(uri.scheme in schemes && uri.host != null && uri.fragment == null) { "URL locale invalide" }
        requireHost(uri.host)
        require(expectedHost == null || uri.host == expectedHost) { "L’URL doit viser la caméra sélectionnée." }
        require(uri.port == -1 || uri.port in 1..65535)
        return uri
    }
    fun authenticatedRtsp(config: CameraConfig): String {
        val u = requireUrl(config.rtspUrl, setOf("rtsp"), config.host)
        require(u.rawUserInfo == null) { "Renseigner les identifiants dans les champs dédiés, pas dans l’URL." }
        fun component(value: String) = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        if (config.rtspVendorPath) {
            val stream = Regex("stream=([01])").find(u.rawPath.orEmpty() + "?" + u.rawQuery.orEmpty())?.groupValues?.get(1) ?: "1"
            return "rtsp://${u.host}:${if (u.port > 0) u.port else 554}/user=${component(config.username)}&password=${component(config.password)}&channel=1&stream=$stream.sdp"
        }
        val credentials = component(config.username) + ":" + component(config.password)
        return "rtsp://$credentials@${u.rawAuthority}${u.rawPath.orEmpty()}${u.rawQuery?.let { "?$it" }.orEmpty()}"
    }
    fun withoutRtspCredentials(url: String, host: String): String {
        val u = requireUrl(url, setOf("rtsp"), host)
        require(!Regex("(?:password|passwd|token|key)=", RegexOption.IGNORE_CASE).containsMatchIn(u.rawPath.orEmpty() + u.rawQuery.orEmpty())) { "L’URI retournée contient un secret hors du champ dédié" }
        return "rtsp://${u.host}${if (u.port > 0) ":${u.port}" else ""}${u.rawPath.orEmpty()}${u.rawQuery?.let { "?$it" }.orEmpty()}"
    }
}

enum class Evidence { NOT_TESTED, RUNNING, OBSERVED, FAILED, SIMULATED, UNAVAILABLE }
data class Diagnostic(val title: String, val evidence: Evidence = Evidence.NOT_TESTED, val detail: String = "En attente de validation matérielle")
data class FoundCamera(val host: String, val protocol: String, val port: Int, val endpoint: String = "")

/** No image retention: compares a downsampled luminance grid and discards old data. */
class MotionDetector {
    private var previous: IntArray? = null
    fun reset() { previous = null }
    fun score(samples: IntArray): Double {
        require(samples.isNotEmpty() && samples.all { it in 0..255 })
        val old = previous
        previous = samples.copyOf()
        if (old == null || old.size != samples.size) return 0.0
        return samples.indices.sumOf { kotlin.math.abs(samples[it] - old[it]).toDouble() } / (samples.size * 255)
    }
}

/** Retains a strict bound even during a multi-day diagnostics run. */
class BoundedLog(private val limit: Int = 200) {
    init { require(limit > 0) }
    private val lines = ArrayDeque<String>()
    @Synchronized fun add(message: String) { if (lines.size == limit) lines.removeFirst(); lines.addLast(message) }
    @Synchronized fun snapshot() = lines.toList()
}
