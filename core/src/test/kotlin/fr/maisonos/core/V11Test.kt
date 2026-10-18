package fr.maisonos.core

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.pow

class V11Test {
    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 255) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
    private fun contrast(a: Long, b: Long): Double {
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x,y) + 0.05) / (minOf(x,y) + 0.05)
    }
    @Test fun allThemeTextAndButtonsMeetNormalTextContrast() {
        val themes = ThemeCatalog.themes + listOf(ThemeCatalog.forHome(HomeConfig()), ThemeCatalog.forHome(HomeConfig(dark = false)))
        themes.forEach { p ->
            for (background in listOf(p.background, p.surface)) {
                assertTrue("${p.name}: text", contrast(p.text, background) >= 4.5)
                assertTrue("${p.name}: muted", contrast(p.muted, background) >= 4.5)
                assertTrue("${p.name}: accent", contrast(p.accent, background) >= 4.5)
            }
            assertTrue("${p.name}: button", contrast(p.onAccent, p.accent) >= 4.5)
        }
    }
    @Test fun cameraUpgradePlacesBothOnFirstPageAndKeepsOtherWidgets() {
        val cameras = listOf(CameraConfig(host = "192.168.1.2"),CameraConfig(host = "192.168.1.3"))
        val old = HomeConfig(cameras = cameras, maxStreams = 1)
        val next = old.withCameraTiles(); next.validate()
        assertEquals(2,next.maxStreams)
        for ((columns,widgets) in listOf(6 to next.profile().landscape,4 to next.profile().portrait)) {
            val tiles = widgets.filter { it.kind == WidgetKind.CAMERA }
            assertEquals(cameras.map { it.id }.toSet(),tiles.map { it.cameraId }.toSet())
            assertTrue(tiles.all { it.y == 0 && it.page == 0 && it.w == columns / 2 })
            assertTrue(widgets.indices.all { i -> widgets.indices.all { j -> i == j || !LayoutEngine.overlaps(widgets[i],widgets[j]) } })
        }
        assertTrue(next.profile().landscape.map { it.id }.containsAll(old.profile().landscape.map { it.id }))
        assertEquals(next,next.withCameraTiles())
    }
    private val release = """{"versionCode":12,"versionName":"1.2.0","apkUrl":"https://example.com/app.apk","sha256":"${"a".repeat(64)}","packageName":"fr.maisonos.diagnostic","size":123456}"""
    @Test fun updateManifestRejectsWrongPackageHttpUnboundedDownloadAndInvalidHash() {
        assertEquals(12,AppRelease.parse(release).versionCode)
        for (bad in listOf(release.replace("https://", "http://"), release.replace("fr.maisonos.diagnostic","other.app"), release.replace("123456","999999999"),release.replace("a".repeat(64),"bad"))) {
            assertThrows(IllegalArgumentException::class.java) { AppRelease.parse(bad) }
        }
        assertThrows(IllegalArgumentException::class.java) { requireHttps("https://user:secret@example.com/manifest.json") }
        assertThrows(IllegalArgumentException::class.java) { AppRelease.parse(" ".repeat(65537)) }
    }
    @Test fun weatherUsesOutdoorValuesAndRejectsStaleOrInvalidPayloads() {
        val now = 1792320000000L
        val json = """{"current":{"time":${now / 1000},"temperature_2m":14.5,"apparent_temperature":12.1,"relative_humidity_2m":81,"wind_speed_10m":17.2,"weather_code":61},"daily":{"temperature_2m_max":[17.2],"temperature_2m_min":[9.3]}}"""
        val settings = WeatherSettings(city = "Fixture city", latitude = 48.0, longitude = 2.0)
        val r = WeatherReading.parse(json, settings, now)
        assertEquals(14.5,r.temperature,0.001); assertEquals("Fixture city",r.city); assertEquals("Pluie",r.description)
        assertEquals(now,r.observedAt)
        assertThrows(IllegalArgumentException::class.java) { WeatherReading.parse(json, settings, now + 259200000) }
        assertThrows(IllegalArgumentException::class.java) { WeatherReading.parse(json.replace("14.5","999"), settings, now) }
    }
    @Test fun vendorRtspEscapesSecretsAndKeepsSubstreamSeparateFromFullscreen() {
        val c = CameraConfig(host = "192.168.1.2",username = "user&?",password = "key /#&?",rtspVendorPath = true,rtspUrl = "rtsp://192.168.1.2:554/stream=1",mainRtspUrl = "rtsp://192.168.1.2:554/stream=0")
        val sub = LocalEndpoint.authenticatedRtsp(c)
        assertEquals("rtsp://192.168.1.2:554/user=user%26%3F&password=key%20%2F%23%26%3F&channel=1&stream=1.sdp",sub)
        assertTrue(LocalEndpoint.authenticatedRtsp(c.copy(rtspUrl = c.mainRtspUrl)).endsWith("stream=0.sdp"))
        assertEquals("",c.exportSafe().password); assertEquals("",c.exportSafe().rtspUrl)
        assertEquals("rtsp://192.168.1.2:554/sub",LocalEndpoint.withoutRtspCredentials("rtsp://user:secret@192.168.1.2:554/sub","192.168.1.2"))
    }
    @Test fun cameraErrorsRemainHelpfulWithoutDisclosingPeerMessages() {
        val secret = "rtsp://user:private-password@192.168.1.2/sub"
        assertFalse(CameraTroubleshooting.failure(IllegalStateException(secret)).contains("private-password"))
        assertTrue(CameraTroubleshooting.failure(SocketTimeoutException(secret)).contains("Délai"))
        val (hint,retry) = CameraTroubleshooting.rtsp(IllegalStateException("401 $secret"))
        assertFalse(retry); assertTrue(hint.contains("identifiants")); assertFalse(hint.contains("private-password"))
        assertTrue(CameraTroubleshooting.failure(CameraProtocolFailure(CameraFailureKind.AUTHENTICATION,203)).contains("203"))
    }
    private val key = "0123456789abcdef"
    private val stateFixture = "000055aa00000002000000080000003b00000000332e33000000000000000000000000f24799fe0fff17cc9f964a40989b0d2831836d84bd28cb4b20619eb84882ca2a0c5b768b0000aa55"
    private fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    @Test fun tuyaFrameMatchesIndependentTinyTuyaReference() {
        // TinyTuya 3c0a687d442dc68e13eeafad39062526b20c109a, MIT.
        val expected = "000055aa000000010000000700000067332e3300000000000000000000000093c6f8bb93cf4b1bd16f5beaf6f2df6fddb3ea4965b74d64814492e7a1c63d03b319a43cebbd3d1c5a0bccc924b522c18004250af01ca360dc165d3c6fdd04d9c8bb6ef2e4d1cb65dcab54d2d904a59dc3ca5f270000aa55"
        val json = """{"devId":"fixture-id","uid":"fixture-id","t":"1700000000","dps":{"1":true}}"""
        assertArrayEquals(bytes(expected),Tuya33Codec.frame(7,1,json,key))
        val state = Tuya33Codec.read(DataInputStream(ByteArrayInputStream(bytes(stateFixture))),key)!!
        assertFalse(state.getValue("dps").jsonObject.getValue("1").jsonPrimitive.boolean)
    }
    @Test fun tuyaRejectsCorruptionOversizedFramesAndWrongKeys() {
        val corrupted = bytes(stateFixture).also { it[25] = (it[25].toInt() xor 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) { Tuya33Codec.read(DataInputStream(ByteArrayInputStream(corrupted)),key) }
        val oversized = ByteBuffer.allocate(16).putInt(0x55aa).putInt(1).putInt(8).putInt(Int.MAX_VALUE).array()
        assertThrows(IllegalArgumentException::class.java) { Tuya33Codec.read(DataInputStream(ByteArrayInputStream(oversized)),key) }
        assertThrows(Exception::class.java) { Tuya33Codec.read(DataInputStream(ByteArrayInputStream(bytes(stateFixture))),"different-key123") }
    }
    @Test fun tuyaCommandAcknowledgementDoesNotFalselyConfirmRequestedState() {
        ServerSocket(0,1,InetAddress.getLoopbackAddress()).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            val future = executor.submit {
                server.accept().use { socket ->
                    val input = DataInputStream(socket.getInputStream())
                    fun readCommand(): Int {
                        val header = ByteArray(16).also(input::readFully); val b = ByteBuffer.wrap(header)
                        assertEquals(0x55aa,b.int); b.int; val cmd = b.int; val size = b.int
                        assertTrue(size in 8..65536); input.readFully(ByteArray(size)); return cmd
                    }
                    assertEquals(7,readCommand())
                    socket.getOutputStream().write(Tuya33Codec.wrap(7,1,ByteArray(4)));socket.getOutputStream().flush()
                    assertEquals(10,readCommand())
                    bytes(stateFixture).forEach { socket.getOutputStream().write(byteArrayOf(it)) };socket.getOutputStream().flush()
                }
            }
            try {
                val d = LocalSwitch(kind = SwitchKind.TUYA_33,host = "192.168.1.2",deviceId = "fixture-id",password = key)
                assertFalse(SmartLifeClient(d,RouteSocketFactory(server.localPort)).set(true))
                future.get(5,TimeUnit.SECONDS)
            } finally { executor.shutdownNow() }
        }
    }
    @Test fun backupsRemoveLocalTuyaKeysAsWellAsCameraPasswords() {
        val cfg = HomeConfig(switches = listOf(LocalSwitch(kind = SwitchKind.TUYA_33,host = "192.168.1.2",deviceId = "fixture-id",password = key)))
        cfg.validate(); assertEquals("",cfg.exportSafe().switches.single().password)
        assertFalse(cfg.switches.single().toString().contains(key))
    }
}
