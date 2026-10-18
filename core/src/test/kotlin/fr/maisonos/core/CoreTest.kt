package fr.maisonos.core

import fr.maisonos.core.vendor.crypto.SofiaHash
import fr.maisonos.core.vendor.dvrip.*
import fr.maisonos.core.vendor.ptz.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.DataInputStream
import java.net.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

class CoreTest {
    private fun rejected(block: () -> Unit) { try { block(); fail("Should reject input") } catch (_: IllegalArgumentException) { } }
    @Test fun localEndpointsRejectInternetAndAmbiguity() {
        listOf("8.8.8.8", "127.0.0.1", "localhost", "192.168.1.01", "192.168.1.255", "0.0.0.0", "169.255.1.3", "172.32.1.3", "192.168.1.3.evil.com").forEach { rejected { LocalEndpoint.requireHost(it) } }
        listOf("192.168.1.2", "10.20.30.4", "172.16.1.9", "169.254.2.4").forEach { assertEquals(it, LocalEndpoint.requireHost(it)) }
    }
    @Test fun cameraMustNotRedirectToAnotherHost() {
        rejected { LocalEndpoint.requireUrl("http://192.168.1.3/onvif", setOf("http"), "192.168.1.2") }
        rejected { LocalEndpoint.requireUrl("https://example.com/", setOf("https")) }
        rejected { LocalEndpoint.requireUrl("file:///etc/passwd", setOf("http")) }
        rejected { LocalEndpoint.requireUrl("rtsp://192.168.1.2:70000/live", setOf("rtsp")) }
    }
    @Test fun credentialsAreEncodedAndNotPrinted() {
        val c = CameraConfig(host = "192.168.1.2", username = "a@b", password = "p:@/?#", rtspUrl = "rtsp://192.168.1.2:554/live?stream=1")
        val uri = URI(LocalEndpoint.authenticatedRtsp(c))
        assertEquals("a@b:p:@/?#", uri.userInfo)
        assertEquals("stream=1", uri.rawQuery)
        assertFalse(c.toString().contains("p:@"))
        assertEquals("", c.exportSafe().password)
        assertEquals("", c.exportSafe().rtspUrl)
        rejected { LocalEndpoint.authenticatedRtsp(c.copy(rtspUrl = "rtsp://user:pass@192.168.1.2/live")) }
    }
    @Test fun configurationValidatesPortsAndPaths() {
        CameraConfig(host = "192.168.1.2").validate()
        rejected { CameraConfig(host = "192.168.1.2", dvripPort = 0).validate() }
        rejected { CameraConfig(host = "192.168.1.2", onvifPath = "//other").validate() }
    }
    @Test fun goldenDiscoveryFrameMatchesReferenceWireBytes() {
        val b = DvripFrame.of(0u, 0u, 1530, byteArrayOf(), type = 0).encode()
        assertEquals("ff00000000000000000000000000fa0500000000", b.joinToString("") { "%02x".format(it) })
    }
    @Test fun headerRoundTripsLargeUnsignedSession() {
        val h = DvripHeader(1, 0xFFFFFFFFu, 4u, 1400, 100)
        assertEquals(h, DvripHeader.decode(h.encode()))
    }
    @Test fun corruptFramesAreRejectedBeforeAllocation() {
        val b = DvripHeader(1, 0u, 0u, 1001, 0).encode()
        b[0] = 0
        assertThrows(DvripFramingException::class.java) { DvripHeader.decode(b) }
        b[0] = 0xff.toByte(); for (i in 16..19) b[i] = 0xff.toByte()
        assertThrows(DvripFramingException::class.java) { DvripHeader.decode(b) }
    }
    @Test fun sofiaHashUsesKnownEmptyPasswordVector() { assertEquals("tlJwpbo6", SofiaHash.hash("")); assertEquals(8, SofiaHash.hash("something").length) }
    @Test fun plaintextPayloadTerminatorsAndBinaryRejection() {
        assertEquals("{\"Ret\":100}", DvripPayloads.decodeJsonOrNull(DvripPayloads.encodeJson("{\"Ret\":100}")))
        assertNull(DvripPayloads.decodeJsonOrNull(byteArrayOf(1, 2, 3)))
    }
    @Test fun compatibilityStopIsExplicitAndMustNotBeAssumedUniversal() {
        val stop = Json.parseToJsonElement(PtzRequestBuilder.buildCompatibilityStop(1u)).jsonObject
        val op = stop["OPPTZControl"]!!.jsonObject
        assertEquals("DirectionUp", op["Command"]!!.jsonPrimitive.content)
        assertEquals(-1, op["Parameter"]!!.jsonObject["Preset"]!!.jsonPrimitive.int)
        rejected { PtzRequestBuilder.build(PtzCommand.DIRECTION_LEFT, 1u, step = 11) }
    }
    @Test fun discoveryDoesNotTrustAdvertisedAddress() {
        val body = DvripPayloads.encodeJson("""{"NetWork.NetCommon":{"HostIP":"0x01020304","TCPPort":34567,"MAC":"aa"}}""")
        val b = DvripFrame.of(0u, 0u, 1531, body).encode()
        assertEquals("192.168.1.2", LanDiscovery.parseDvr(b, "192.168.1.2")!!.host)
        assertNull(LanDiscovery.parseDvr(b.copyOf(22), "192.168.1.2"))
        assertNull(LanDiscovery.parseDvr(b, "8.8.8.8"))
    }
    @Test fun onvifDiscoveryRequiresLocalMatchingEndpoint() {
        val xml = """<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"><s:Body><ProbeMatches><ProbeMatch><XAddrs>http://192.168.1.2:8899/onvif/device_service http://8.8.8.8/evil</XAddrs></ProbeMatch></ProbeMatches></s:Body></s:Envelope>"""
        val results = LanDiscovery.parseOnvif(xml.toByteArray(), "192.168.1.2")
        assertEquals(1, results.size); assertEquals(8899, results.single().port)
    }
    @Test fun xmlExternalEntitiesAreDisabled() {
        val xml = """<!DOCTYPE x [<!ENTITY secret SYSTEM "file:///etc/passwd">]><x>&secret;</x>"""
        assertThrows(Exception::class.java) { SafeXml.parse(xml) }
    }
    @Test fun motionIgnoresFirstFrameAndCanReset() {
        val m = MotionDetector()
        assertEquals(0.0, m.score(intArrayOf(0, 0)), 0.0001)
        assertEquals(1.0, m.score(intArrayOf(255, 255)), 0.0001)
        assertEquals(0.0, m.score(intArrayOf(255, 255)), 0.0001)
        m.reset(); assertEquals(0.0, m.score(intArrayOf(0, 0)), 0.0001)
    }
    @Test fun historyRemainsBounded() {
        val log = BoundedLog(2); log.add("a"); log.add("b"); log.add("c")
        assertEquals(listOf("b", "c"), log.snapshot())
    }
    @Test fun actualSocketLoginAndStopWorkWithFragmentedFixtureResponses() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val pool = Executors.newSingleThreadExecutor()
            val future = pool.submit {
                server.accept().use { s ->
                    val input = DataInputStream(s.getInputStream())
                    repeat(3) { index ->
                        val header = ByteArray(20).also(input::readFully)
                        val h = DvripHeader.decode(header)
                        val body = ByteArray(h.payloadLength).also(input::readFully)
                        val json = Json.parseToJsonElement(DvripPayloads.decodeJsonOrNull(body)!!).jsonObject
                        if (index == 0) { assertEquals(1000, h.messageId); assertEquals(SofiaHash.hash("test"), json["PassWord"]!!.jsonPrimitive.content) }
                        if (index == 1) assertEquals("DirectionRight", json["OPPTZControl"]!!.jsonObject["Command"]!!.jsonPrimitive.content)
                        if (index == 2) assertEquals(-1, json["OPPTZControl"]!!.jsonObject["Parameter"]!!.jsonObject["Preset"]!!.jsonPrimitive.int)
                        val reply = DvripFrame.of(1u, h.sequence, h.messageId + 1, DvripPayloads.encodeJson("""{"Ret":100,"SessionID":"0x00000001"}""")).encode()
                        // Force the client to handle TCP fragmentation, not one-read-per-frame.
                        reply.forEach { s.getOutputStream().write(byteArrayOf(it)) }
                    }
                }
            }
            try {
                DvripClient(RouteSocketFactory(server.localPort)).use { client ->
                    assertTrue(client.connect(CameraConfig(host = "192.168.1.2", username = "test", password = "test")).contains("acceptée"))
                    client.move(PtzCommand.DIRECTION_RIGHT, 1); client.stop()
                }
                future.get(5, TimeUnit.SECONDS)
            } finally { pool.shutdownNow() }
        }
    }
}

/** Test-only transport redirects the private fixture host to a loopback server. */
internal class RouteSocketFactory(private val fixturePort: Int) : SocketFactory() {
    override fun createSocket(): Socket = object : Socket() {
        override fun connect(endpoint: SocketAddress?, timeout: Int) = super.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), fixturePort), timeout)
    }
    override fun createSocket(host: String, port: Int): Socket = error("unused")
    override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int): Socket = error("unused")
    override fun createSocket(host: InetAddress, port: Int): Socket = error("unused")
    override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int): Socket = error("unused")
}
