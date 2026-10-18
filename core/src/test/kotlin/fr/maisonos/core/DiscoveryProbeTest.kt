package fr.maisonos.core

import fr.maisonos.core.vendor.dvrip.*
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.io.DataInputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DiscoveryProbeTest {
    @Test fun directRtspDiscoveryAcceptsFragmentedUnauthorizedResponseWithoutTryingCredentials() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val pool = Executors.newSingleThreadExecutor()
            val future = pool.submit {
                server.accept().use { socket ->
                    socket.soTimeout = 1500
                    val text = StringBuilder()
                    val input = socket.getInputStream()
                    while (!text.endsWith("\r\n\r\n")) { text.append(input.read().toChar()); require(text.length < 1024) }
                    assertTrue(text.startsWith("OPTIONS rtsp://192.168.1.2:554/ RTSP/1.0"))
                    assertFalse(text.contains("Authorization"))
                    "RTSP/1.0 401\r\n".toByteArray().forEach { socket.getOutputStream().write(it.toInt()) }
                }
            }
            try { assertTrue(LanUnicastProbe(RouteSocketFactory(server.localPort)).rtsp("192.168.1.2")); future.get(3,TimeUnit.SECONDS) }
            finally { pool.shutdownNow() }
        }
    }
    @Test fun directDvripDiscoveryNegotiatesWithoutLoggingIn() {
        ServerSocket(0,1,InetAddress.getLoopbackAddress()).use { server ->
            val pool = Executors.newSingleThreadExecutor()
            val future = pool.submit {
                server.accept().use { socket ->
                    val input = DataInputStream(socket.getInputStream())
                    val bytes = ByteArray(20).also(input::readFully);val h = DvripHeader.decode(bytes)
                    assertEquals(1010,h.messageId)
                    val payload = ByteArray(h.payloadLength).also(input::readFully)
                    assertEquals("{}",DvripPayloads.decodeJsonOrNull(payload))
                    socket.getOutputStream().write(DvripFrame.of(0u,0u,1011,DvripPayloads.encodeJson("{\"Ret\":100}")).encode())
                }
            }
            try { assertTrue(LanUnicastProbe(RouteSocketFactory(server.localPort)).dvrip("192.168.1.2")); future.get(3,TimeUnit.SECONDS) }
            finally { pool.shutdownNow() }
        }
    }
}
