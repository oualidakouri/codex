package fr.maisonos.core

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class OnvifTest {
    private fun reply(body: String) = MockResponse().setBody("""<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" xmlns:tt="http://www.onvif.org/ver10/schema"><s:Body>$body</s:Body></s:Envelope>""")
    @Test fun capabilitiesStreamAndBoundedPtzUseSoapAndWsse() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(reply("<GetCapabilitiesResponse><Capabilities><Media><XAddr>http://192.168.1.2/media</XAddr></Media><PTZ><XAddr>http://192.168.1.2/ptz</XAddr></PTZ></Capabilities></GetCapabilitiesResponse>"))
            server.enqueue(reply("<GetProfilesResponse><Profiles token=\"profile1\"><PTZConfiguration token=\"ptz1\"/></Profiles></GetProfilesResponse>"))
            server.enqueue(reply("<GetStreamUriResponse><MediaUri><Uri>rtsp://192.168.1.2:554/substream</Uri></MediaUri></GetStreamUriResponse>"))
            server.enqueue(reply("<GetNodesResponse><PTZNode><SupportedPTZSpaces><ContinuousPanTiltVelocitySpace/></SupportedPTZSpaces></PTZNode></GetNodesResponse>"))
            server.enqueue(reply("<ContinuousMoveResponse/>")); server.enqueue(reply("<StopResponse/>"))
            val config = CameraConfig(host = "192.168.1.2", username = "my-user", password = "my-secret")
            val client = OnvifClient(config, RouteSocketFactory(server.port))
            try {
                val session = client.inspect()
                assertTrue(session.continuousPanTilt); assertEquals("rtsp://192.168.1.2:554/substream", session.streamUrl)
                client.move(0.25, -0.25); client.stop()
                val first = server.takeRequest().body.readUtf8()
                assertFalse(first.contains("my-secret"))
                val xml = SafeXml.parse(first)
                val nonce = Base64.getDecoder().decode(SafeXml.text(xml, "Nonce"))
                val created = SafeXml.text(xml, "Created")
                val expected = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest(nonce + created.toByteArray() + "my-secret".toByteArray()))
                assertEquals(expected, SafeXml.text(xml, "Password"))
                repeat(3) { server.takeRequest() }
                val move = server.takeRequest().body.readUtf8()
                assertTrue(move.contains("PT0.5S")); assertTrue(move.contains("profile1"))
                assertTrue(server.takeRequest().body.readUtf8().contains("<tptz:Stop>"))
            } finally { client.close() }
        }
    }
    @Test fun publicCapabilitiesEndpointIsRejectedBeforeContact() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(reply("<GetCapabilitiesResponse><Media><XAddr>http://8.8.8.8/media</XAddr></Media></GetCapabilitiesResponse>"))
            val client = OnvifClient(CameraConfig(host = "192.168.1.2"), RouteSocketFactory(server.port))
            try { assertThrows(IllegalArgumentException::class.java) { client.inspect() }; assertEquals(1, server.requestCount) }
            finally { client.close() }
        }
    }
    @Test fun httpRedirectCannotSendCameraCredentialsOutsideLan() {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://example.com/"))
            val client = OnvifClient(CameraConfig(host = "192.168.1.2"), RouteSocketFactory(server.port))
            try { assertThrows(IllegalStateException::class.java) { client.inspect() }; assertEquals(1, server.requestCount) }
            finally { client.close() }
        }
    }
    @Test fun unsupportedPtzIsNotAdvertisedAndSoapFaultFails() {
        MockWebServer().use { server ->
            server.start(); server.enqueue(reply("<s:Fault><s:Reason>NotAuthorized</s:Reason></s:Fault>"))
            val client = OnvifClient(CameraConfig(host = "192.168.1.2"), RouteSocketFactory(server.port))
            try { assertThrows(IllegalStateException::class.java) { client.inspect() } }
            finally { client.close() }
        }
    }
    @Test fun httpDigestChallengeUsesHashAndNeverPlainPassword() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(401).setHeader("WWW-Authenticate", "Digest realm=\"camera\", nonce=\"nonce1\", qop=\"auth\", algorithm=MD5"))
            server.enqueue(reply("<GetCapabilitiesResponse><Media><XAddr>http://192.168.1.2/media</XAddr></Media></GetCapabilitiesResponse>"))
            server.enqueue(reply("<GetProfilesResponse><Profiles token=\"p1\"/></GetProfilesResponse>"))
            server.enqueue(reply("<GetStreamUriResponse><Uri>rtsp://192.168.1.2/sub</Uri></GetStreamUriResponse>"))
            val client = OnvifClient(CameraConfig(host = "192.168.1.2", username = "user", password = "secret"), RouteSocketFactory(server.port))
            try {
                assertFalse(client.inspect().continuousPanTilt)
                assertNull(server.takeRequest().getHeader("Authorization"))
                val auth = server.takeRequest().getHeader("Authorization")!!
                assertFalse(auth.contains("secret"))
                fun field(name: String) = Regex("$name=\"([^\"]+)\"").find(auth)!!.groupValues[1]
                fun md5(v: String) = MessageDigest.getInstance("MD5").digest(v.toByteArray()).joinToString("") { "%02x".format(it) }
                val expected = md5("${md5("user:camera:secret")}:nonce1:00000001:${field("cnonce")}:auth:${md5("POST:${field("uri")}")}")
                assertEquals(expected, field("response"))
            } finally { client.close() }
        }
    }
}
