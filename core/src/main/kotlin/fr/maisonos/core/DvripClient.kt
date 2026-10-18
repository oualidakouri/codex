package fr.maisonos.core

import fr.maisonos.core.vendor.crypto.SofiaHash
import fr.maisonos.core.vendor.dvrip.*
import fr.maisonos.core.vendor.ptz.*
import kotlinx.serialization.json.*
import java.io.Closeable
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

/** Serialized request/response control socket. No credential guesses, no hidden login retries. */
class DvripClient(private val sockets: SocketFactory = SocketFactory.getDefault()) : PtzPort {
    @Volatile private var socket: Socket? = null
    private var session = 0u
    private var sequence = 0u
    private var lastLogin = 0L
    @Synchronized fun connect(config: CameraConfig): String {
        config.validate()
        require(System.nanoTime() - lastLogin > 5_000_000_000L || lastLogin == 0L) { "Attendre 5 secondes entre deux authentifications." }
        close()
        lastLogin = System.nanoTime()
        socket = sockets.createSocket()
        try {
            checkNotNull(socket).apply {
                soTimeout = 2000
                connect(InetSocketAddress(config.host, config.dvripPort), 2000)
            }
            val answer = request(1000, buildJsonObject {
                put("EncryptType", "MD5"); put("LoginType", "DVRIP-Web")
                put("UserName", config.username); put("PassWord", SofiaHash.hash(config.password))
            })
            val code = answer["Ret"]?.jsonPrimitive?.intOrNull
            if (code != 100) throw CameraProtocolFailure(CameraFailureKind.AUTHENTICATION, code?.takeIf { it in 0..9999 })
            session = answer["SessionID"]!!.jsonPrimitive.content.removePrefix("0x").toUInt(16)
            check(session != 0u) { "Session DVRIP invalide" }
            return "Authentification DVRIP acceptée ; session locale ouverte. Vidéo DVRIP non implémentée."
        } catch (e: Exception) { close(); throw e }
    }
    @Synchronized fun keepAlive() { command(1006, "KeepAlive") }
    @Synchronized fun move(direction: PtzCommand, speed: Int) {
        check(session != 0u)
        checked(requestText(1400, PtzRequestBuilder.build(direction, session, step = speed)))
    }
    override fun move(direction: PanTilt, speed: Double) {
        require(speed in 0.0..1.0)
        move(when (direction) { PanTilt.LEFT -> PtzCommand.DIRECTION_LEFT; PanTilt.RIGHT -> PtzCommand.DIRECTION_RIGHT; PanTilt.UP -> PtzCommand.DIRECTION_UP; PanTilt.DOWN -> PtzCommand.DIRECTION_DOWN }, (speed * 10).toInt().coerceIn(1, 10))
    }
    @Synchronized override fun stop() {
        if (socket != null && session != 0u) checked(requestText(1400, PtzRequestBuilder.buildCompatibilityStop(session)))
    }
    private fun command(id: Int, name: String) = checked(request(id, buildJsonObject { put("Name", name); put("SessionID", "0x%08X".format(session.toLong())) }))
    private fun checked(j: JsonObject) { check(j["Ret"]?.jsonPrimitive?.intOrNull == 100) { "Commande DVRIP refusée" } }
    private fun request(id: Int, payload: JsonObject) = requestText(id, payload.toString())
    private fun requestText(id: Int, payload: String): JsonObject {
        val s = checkNotNull(socket) { "Session fermée" }
        val seq = sequence++
        s.getOutputStream().apply { write(DvripFrame.of(session, seq, id, DvripPayloads.encodeJson(payload)).encode()); flush() }
        val input = DataInputStream(s.getInputStream())
        // Reject oversized/unrelated frames; never allocate a peer-provided unbounded length.
        repeat(8) {
            val header = ByteArray(20).also(input::readFully)
            val h = DvripHeader.decode(header)
            require(h.payloadLength <= 262144) { "Réponse DVRIP trop volumineuse" }
            val bytes = ByteArray(h.payloadLength).also(input::readFully)
            if (h.messageId == id + 1) {
                val json = DvripPayloads.decodeJsonOrNull(bytes) ?: error("Réponse chiffrée/non JSON non prise en charge")
                return Json.parseToJsonElement(json).jsonObject
            }
        }
        error("Réponse DVRIP attendue absente")
    }
    /** Closing the socket also interrupts a blocked read when the app leaves foreground. */
    override fun close() { val s = socket; socket = null; session = 0u; sequence = 0u; runCatching { s?.close() } }
}
