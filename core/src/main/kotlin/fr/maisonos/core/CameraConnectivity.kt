package fr.maisonos.core

import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.io.IOException
import javax.net.SocketFactory

enum class CameraFailureKind { AUTHENTICATION, ONVIF_PATH, ONVIF_FAULT, PROTOCOL }
class CameraProtocolFailure(val kind: CameraFailureKind, val code: Int? = null) : IllegalStateException("Camera protocol failure")

object CameraTroubleshooting {
    fun failure(error: Throwable): String {
        val causes = generateSequence(error) { it.cause }.take(8).toList()
        val protocol = causes.filterIsInstance<CameraProtocolFailure>().firstOrNull()
        return when {
            protocol?.kind == CameraFailureKind.AUTHENTICATION -> "Identifiants refusés${protocol.code?.let { " (code $it)" }.orEmpty()}. Vérifier le compte local de cette caméra ; il peut différer du compte iCSee. Aucun autre mot de passe n’est essayé."
            protocol?.kind == CameraFailureKind.ONVIF_PATH -> "La caméra répond en HTTP, mais pas au chemin ONVIF configuré. Vérifier le port et l’activation ONVIF dans iCSee. RTSP reste indépendant."
            protocol?.kind == CameraFailureKind.ONVIF_FAULT -> "Réponse ONVIF reçue, commande refusée. Vérifier compte ONVIF, date/heure et fonctions annoncées."
            causes.any { it is SocketTimeoutException } -> "Délai réseau dépassé. Vérifier isolation Wi-Fi invité/IoT, port, alimentation et services locaux. Une bande 5 GHz différente ne bloque pas à elle seule le réseau local."
            causes.any { it is ConnectException } -> "Connexion refusée sur ce port. L’adresse peut répondre au scan alors que ce service est désactivé ou utilise un autre port."
            causes.any { it is IllegalArgumentException } -> "Réglage ou réponse incompatible. Vérifier IPv4, port, URL et protocole de la caméra."
            else -> "Communication interrompue ou réponse incompatible. Tester les ports, puis DVRIP et RTSP séparément ; ONVIF n’est pas obligatoire pour afficher une vidéo."
        }
    }
    fun rtsp(error: Throwable): Pair<String, Boolean> {
        val causes = generateSequence(error) { it.cause }.take(8).toList()
        // Inspect only known status numbers; never expose URI/message text to the UI or logs.
        val status = causes.firstNotNullOfOrNull { Regex("\\b(401|403|404|461)\\b").find(it.message.orEmpty())?.value }
        return when (status) {
            "401", "403" -> "RTSP : identifiants refusés. Vérifier le compte local et essayer le format iCSee/Xiongmai si ce firmware l’exige. Reconnexion automatique suspendue." to false
            "404" -> "RTSP : le chemin vidéo n’existe pas. Obtenir l’URI par ONVIF ou tester le format iCSee/Xiongmai dans la fiche caméra." to false
            "461" -> "RTSP : transport TCP refusé par la caméra. Vérifier les réglages RTSP du firmware." to false
            else -> failure(error) to true
        }
    }
}

data class CameraPortResult(val label: String, val port: Int, val reachable: Boolean, val detail: String)
class CameraConnectivity(private val sockets: SocketFactory = SocketFactory.getDefault()) {
    /** No login attempts: a TCP opening proves reachability, never ONVIF compatibility. */
    fun inspect(camera: CameraConfig): List<CameraPortResult> {
        camera.validate()
        val rtsp = if (camera.rtspUrl.isBlank()) 554 else LocalEndpoint.requireUrl(camera.rtspUrl, setOf("rtsp"), camera.host).port.takeIf { it > 0 } ?: 554
        return listOf("DVRIP" to camera.dvripPort, "RTSP" to rtsp, "ONVIF configuré" to camera.onvifPort, "HTTP (ONVIF possible)" to 80).distinctBy { it.second }.map { (label, port) ->
            try {
                sockets.createSocket().use { it.connect(InetSocketAddress(camera.host, port), 1500) }
                CameraPortResult(label, port, true, "Port TCP accessible ; protocole et authentification restent à tester.")
            } catch (_: SocketTimeoutException) { CameraPortResult(label, port, false, "Pas de réponse dans le délai ; filtrage réseau ou service absent possible.")
            } catch (_: IOException) { CameraPortResult(label, port, false, "Connexion TCP impossible ; service désactivé, port différent ou réseau filtré possible.") }
        }
    }
}
