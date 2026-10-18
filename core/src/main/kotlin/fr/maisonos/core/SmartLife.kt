package fr.maisonos.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.UUID
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.net.SocketFactory

@Serializable enum class SwitchKind { SIMULATION, TUYA_33 }
@Serializable data class LocalSwitch(val id: String = UUID.randomUUID().toString(), val name: String = "Mon interrupteur", val room: String = "",
    val kind: SwitchKind = SwitchKind.SIMULATION, val host: String = "", val deviceId: String = "", val password: String = "", val dataPoint: Int = 1) {
    fun validate() {
        require(name.isNotBlank() && name.length <= 80 && room.length <= 80 && dataPoint in 1..255)
        if (kind == SwitchKind.TUYA_33) {
            LocalEndpoint.requireHost(host); require(deviceId.length in 1..128)
            // Empty is allowed for a backup restored without secrets. Connection still requires 16 bytes.
            require(password.isEmpty() || (password.toByteArray(Charsets.UTF_8).size == 16 && password.none(Char::isISOControl)))
        }
    }
    override fun toString() = "LocalSwitch(credentials=redacted)"
}

/** Tuya 3.3 framing follows TinyTuya (MIT, Jason Cox); no cloud login or key extraction. */
object Tuya33Codec {
    private val version = "3.3".toByteArray() + ByteArray(12)
    fun encrypt(data: ByteArray, key: String): ByteArray = cipher(Cipher.ENCRYPT_MODE, data, key)
    fun decrypt(data: ByteArray, key: String): ByteArray = cipher(Cipher.DECRYPT_MODE, data, key)
    private fun cipher(mode: Int, data: ByteArray, key: String): ByteArray {
        require(key.toByteArray().size == 16)
        return Cipher.getInstance("AES/ECB/PKCS5Padding").apply { init(mode, SecretKeySpec(key.toByteArray(), "AES")) }.doFinal(data)
    }
    fun frame(command: Int, sequence: Int, json: String, key: String): ByteArray {
        require(command == 7 || command == 10)
        val payload = (if (command == 7) version else byteArrayOf()) + encrypt(json.toByteArray(), key)
        return wrap(command, sequence, payload)
    }
    fun wrap(command: Int, sequence: Int, payload: ByteArray): ByteArray {
        require(payload.size <= 65524)
        val data = ByteBuffer.allocate(16 + payload.size).putInt(0x55aa).putInt(sequence).putInt(command).putInt(payload.size + 8).put(payload).array()
        return data + ByteBuffer.allocate(8).putInt(CRC32().apply { update(data) }.value.toInt()).putInt(0xaa55).array()
    }
    fun read(input: DataInputStream, key: String): JsonObject? {
        val header = ByteArray(16).also(input::readFully); val h = ByteBuffer.wrap(header)
        require(h.int == 0x55aa); h.int; h.int
        val size = h.int; require(size in 12..65536)
        val body = ByteArray(size).also(input::readFully)
        val tail = ByteBuffer.wrap(body, size - 8, 8)
        val crc = tail.int; require(tail.int == 0xaa55)
        require(CRC32().apply { update(header); update(body, 0, size - 8) }.value.toInt() == crc)
        require(ByteBuffer.wrap(body, 0, 4).int == 0) { "Commande Tuya refusée" }
        var payload = body.copyOfRange(4, size - 8)
        if (payload.isEmpty()) return null // An ACK alone is not confirmation of relay state.
        if (payload.size >= 15 && payload.take(3).toByteArray().contentEquals("3.3".toByteArray())) payload = payload.copyOfRange(15, payload.size)
        val json = decrypt(payload, key)
        require(json.size <= 65536)
        return Json.parseToJsonElement(json.toString(Charsets.UTF_8)).jsonObject
    }
}

interface SwitchPort { fun read(): Boolean; fun set(on: Boolean): Boolean }
class SmartLifeClient(private val device: LocalSwitch, private val sockets: SocketFactory = SocketFactory.getDefault()) : SwitchPort {
    private fun exchange(on: Boolean?): Boolean {
        device.validate(); require(device.kind == SwitchKind.TUYA_33 && device.password.toByteArray().size == 16)
        val json = buildJsonObject {
            put("devId", device.deviceId); put("uid", device.deviceId); put("t", (System.currentTimeMillis() / 1000).toString())
            if (on == null) put("gwId", device.deviceId)
            else putJsonObject("dps") { put(device.dataPoint.toString(), on) }
        }
        sockets.createSocket().use { socket ->
            socket.soTimeout = 3000; socket.connect(InetSocketAddress(device.host, 6668), 2000)
            val output = socket.getOutputStream()
            output.write(Tuya33Codec.frame(if (on == null) 10 else 7, 1, json.toString(), device.password)); output.flush()
            val input = DataInputStream(socket.getInputStream())
            var queried = on == null
            repeat(4) {
                val response = Tuya33Codec.read(input, device.password)
                val value = (response?.get("dps") as? JsonObject)?.get(device.dataPoint.toString())?.jsonPrimitive?.booleanOrNull
                if (value != null) return value
                if (!queried) {
                    // Confirm the relay state by a read, never by assuming the requested command succeeded.
                    output.write(Tuya33Codec.frame(10, 2, buildJsonObject { put("gwId", device.deviceId); put("devId", device.deviceId); put("uid", device.deviceId); put("t", (System.currentTimeMillis()/1000).toString()) }.toString(), device.password))
                    output.flush(); queried = true
                }
            }
            error("Aucun état booléen pour ce point de données")
        }
    }
    override fun read() = exchange(null)
    override fun set(on: Boolean) = exchange(on)
}
