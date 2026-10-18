package fr.maisonos.core

import fr.maisonos.core.vendor.dvrip.*
import kotlinx.serialization.json.*
import java.net.*
import java.util.UUID

/** Single bounded broadcast/multicast window; no password attempts and no subnet sweep. */
class LanDiscovery(private val bind: (DatagramSocket) -> Unit = {}) {
    fun search(broadcast: String, isCancelled: () -> Boolean = { false }): List<FoundCamera> {
        val found = linkedMapOf<String, FoundCamera>()
        DatagramSocket(null).use { dvr ->
            DatagramSocket(null).use { onvif ->
                for (s in listOf(dvr, onvif)) {
                    s.reuseAddress = true; s.broadcast = true; s.soTimeout = 150
                    // Some DVRIP firmwares reply to fixed port 34569, not the source port.
                    s.bind(InetSocketAddress(if (s === dvr) 34569 else 0)); bind(s)
                }
                val probe = DvripFrame.of(0u, 0u, 1530, byteArrayOf(), type = 0).encode()
                dvr.send(DatagramPacket(probe, probe.size, InetAddress.getByName(broadcast), 34569))
                val xml = """<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" xmlns:a="http://schemas.xmlsoap.org/ws/2004/08/addressing" xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery" xmlns:dn="http://www.onvif.org/ver10/network/wsdl"><s:Header><a:MessageID>uuid:${UUID.randomUUID()}</a:MessageID><a:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</a:To><a:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</a:Action></s:Header><s:Body><d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe></s:Body></s:Envelope>""".toByteArray()
                onvif.send(DatagramPacket(xml, xml.size, InetAddress.getByName("239.255.255.250"), 3702))
                val end = System.nanoTime() + 5_000_000_000L
                val buf = ByteArray(16384)
                while (!isCancelled() && System.nanoTime() < end) {
                    for (s in listOf(dvr, onvif)) {
                        try {
                            val packet = DatagramPacket(buf, buf.size); s.receive(packet)
                            val host = packet.address.hostAddress ?: continue
                            runCatching { LocalEndpoint.requireHost(host) }.getOrNull() ?: continue
                            val bytes = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                            if (s === dvr) parseDvr(bytes, host)?.let { found["$host/DVRIP"] = it }
                            else parseOnvif(bytes, host).forEach { found["$host/ONVIF"] = it }
                        } catch (_: SocketTimeoutException) { /* bounded polling */ }
                    }
                }
            }
        }
        return found.values.toList()
    }
    companion object {
        fun parseDvr(bytes: ByteArray, sender: String): FoundCamera? = runCatching {
            val h = DvripHeader.decode(bytes)
            require(h.messageId in 1530..1531 && h.payloadLength <= bytes.size - 20)
            val text = DvripPayloads.decodeJsonOrNull(bytes.copyOfRange(20, 20 + h.payloadLength)) ?: return null
            val j = Json.parseToJsonElement(text).jsonObject
            val data = (j["NetWork.NetCommon"] as? JsonObject) ?: j
            require(data.containsKey("HostIP") || data.containsKey("SerialNo") || data.containsKey("MAC"))
            val port = (data["TCPPort"] as? JsonPrimitive)?.intOrNull ?: 34567
            require(port in 1..65535); LocalEndpoint.requireHost(sender)
            FoundCamera(sender, "DVRIP", port)
        }.getOrNull()
        fun parseOnvif(bytes: ByteArray, sender: String): List<FoundCamera> = runCatching {
            LocalEndpoint.requireHost(sender)
            val root = SafeXml.parse(bytes.toString(Charsets.UTF_8))
            SafeXml.all(root, "ProbeMatch").flatMap { SafeXml.text(it, "XAddrs").trim().split(Regex("\\s+")) }.mapNotNull { url ->
                runCatching {
                    val u = LocalEndpoint.requireUrl(url, setOf("http", "https"), sender)
                    FoundCamera(sender, "ONVIF", if (u.port != -1) u.port else if (u.scheme == "https") 443 else 80, url)
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }
}
