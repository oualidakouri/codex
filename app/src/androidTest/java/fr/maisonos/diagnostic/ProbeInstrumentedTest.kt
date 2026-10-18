package fr.maisonos.diagnostic

import android.content.Context
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import fr.maisonos.core.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher

/** Run only on a development device. Every test restores the previous configuration. */
@OptIn(ExperimentalTestApi::class)
class ProbeInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var previous: ProbeConfig
    @Before fun prepare() {
        val store = ConfigStore(context); previous = store.load()
        // The software emulator can spend several seconds rendering a new window.
        // Exercise the configurable duration with a longer rule before asserting its label.
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[ProbeViewModel::class.java]
        compose.waitUntil(20000) { vm.state.value.report != "Lecture du matériel…" }
        val fixture = ProbeConfig(home = HomeConfig(liveDashboard = false, rules = listOf(LocalRule(durationSeconds = 60))))
        compose.runOnUiThread { vm.save(fixture) }
        compose.waitUntil(20000) { vm.state.value.config == fixture && !vm.state.value.busy }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("nav-Accueil").performClick()
    }
    @After fun restore() {
        AdhanPlayback.stop(context); AdhanScheduler.cancel(context)
        if (::previous.isInitialized) ConfigStore(context).save(previous)
    }
    @Test fun simulationAndNavigationAreExplicit() {
        compose.onNodeWithTag("nav-Événements").performClick()
        compose.onNodeWithText("Tester en simulation").performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasText("SIMULATION · événement local"), 15000)
        compose.onNodeWithText("SIMULATION · événement local").assertExists()
        compose.onNode(hasText("Tu as du courrier !") and hasAnyAncestor(isDialog())).assertExists()
        compose.onNodeWithText("Fermer").performClick()
        compose.onNodeWithTag("nav-Diagnostics").performClick()
        compose.onNodeWithText("Journal").performClick()
        compose.onNodeWithText("Configuration et preuves").assertExists()
    }
    @Test fun activityRecreationRestoresForegroundState() {
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15000) {
            androidx.lifecycle.ViewModelProvider(compose.activity)[ProbeViewModel::class.java].state.value.foreground
        }
        assertTrue(androidx.lifecycle.ViewModelProvider(compose.activity)[ProbeViewModel::class.java].state.value.foreground)
    }
    @Test fun encryptedConfigurationSurvivesNewStoreInstanceAndExportExcludesAllCameraSecrets() {
        val camera = CameraConfig(host = "192.168.1.2", username = "test-user", password = "instrumented-secret", rtspUrl = "rtsp://192.168.1.2/live?token=sensitive", mainRtspUrl = "rtsp://192.168.1.2/main?token=main-secret")
        val config = ProbeConfig(camera = camera, home = HomeConfig(cameras = listOf(camera), liveDashboard = false))
        val store = ConfigStore(context); store.save(config)
        assertEquals(config, ConfigStore(context).load())
        val export = store.export(config)
        assertFalse(export.contains("instrumented-secret")); assertFalse(export.contains("sensitive")); assertFalse(export.contains("main-secret"))
        val restored = store.parseImport(export)
        assertEquals(camera.host, restored.camera.host); assertEquals("", restored.camera.password)
        assertEquals("", restored.home.cameras.single().rtspUrl)
    }
    @Test fun androidXmlParserAcceptsSoapAndRejectsDtd() {
        val root = SafeXml.parse("<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"><s:Body><Ret>100</Ret></s:Body></s:Envelope>")
        assertEquals("100", SafeXml.text(root, "Ret"))
        assertThrows(IllegalArgumentException::class.java) { SafeXml.parse("<!DOCTYPE x [<!ENTITY a SYSTEM 'file:///etc/passwd'>]><x>&a;</x>") }
    }
    @Test fun legacyDiagnosticConfigurationMigratesCameraAndPasswordWithoutReset() {
        val text = """{"version":1,"camera":{"name":"Legacy","host":"192.168.1.2","username":"user","password":"old-secret","rtspUrl":"rtsp://192.168.1.2/sub"},"idleSeconds":300,"motionThreshold":0.08,"keepAwake":true}"""
        val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey("maison.config.v1", null)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        File(context.filesDir,"probe-config.enc").writeBytes(cipher.iv + cipher.doFinal(text.toByteArray()))
        val migrated = ConfigStore(context).load()
        assertEquals(2, migrated.version); assertEquals(300,migrated.idleSeconds)
        assertEquals("old-secret", migrated.home.cameras.single().password)
        assertTrue(migrated.home.profile().landscape.any { it.kind == WidgetKind.CAMERA })
    }
    @Test fun userCanConfigureCityAndSeeFivePrayerTimes() {
        compose.onNodeWithTag("nav-Salat").performClick()
        compose.onNodeWithTag("configure-prayers").performClick()
        compose.onNodeWithTag("city-Paris").performClick()
        compose.onNodeWithTag("save-prayer-settings").performClick()
        compose.waitUntil(15000) { ConfigStore(context).load().home.prayer.latitude == 48.8566 }
        compose.onNodeWithText("Les cinq prières").assertExists()
        compose.onNodeWithText("Fajr").assertExists(); compose.onNodeWithText("Isha").assertExists()
        assertEquals("Europe/Paris", ConfigStore(context).load().home.prayer.zone)
    }
    @Test fun dashboardDragChangesPersistedGridPosition() {
        compose.onNodeWithTag("edit-dashboard").performClick()
        val original = ConfigStore(context).load().home.profile().landscape.first { it.kind == WidgetKind.CLOCK }
        compose.onNodeWithTag("widget-CLOCK").performTouchInput {
            down(Offset(30f, 20f)); advanceEventTime(700)
            moveBy(Offset(190f, 0f), 200); up()
        }
        compose.waitUntil(15000) {
            ConfigStore(context).load().home.profile().landscape.first { it.kind == WidgetKind.CLOCK }.x != original.x
        }
        val stored = ConfigStore(context).load().home.profile().landscape
        assertTrue(stored.indices.all { i -> stored.indices.all { j -> i == j || !LayoutEngine.overlaps(stored[i],stored[j]) } })
    }
    @Test fun localDuasHaveArabicFrenchAndIdentifiedSources() {
        val duas = Duas.all(context)
        assertEquals(7, duas.size); assertEquals(7,duas.map { it.id }.distinct().size)
        assertTrue(duas.all { it.source.startsWith("Coran ") && it.arabic.any { char -> char in '\u0600'..'\u06ff' } && it.french.isNotBlank() })
        assertEquals(Duas.daily(context, java.time.LocalDate.of(2026,10,18)), Duas.daily(context, java.time.LocalDate.of(2026,10,18)))
        assertNotEquals(Duas.daily(context, java.time.LocalDate.of(2026,10,18)).id, Duas.daily(context, java.time.LocalDate.of(2026,10,19)).id)
    }
    @Test fun zipBackupRestoresMediaAndRejectsTraversal() {
        val id = UUID.randomUUID().toString(); val mediaFile = LocalFiles.mediaFile(context,id);mediaFile.writeBytes(byteArrayOf(1,2,3,4))
        val zip = File(context.cacheDir,"test-backup.zip")
        val bad = File(context.cacheDir,"test-bad.zip")
        try {
            val media = LocalMedia(id,"fixture",MediaKind.AUDIO,4)
            val config = ProbeConfig(home = HomeConfig(media = listOf(media)))
            LocalFiles.backup(context,Uri.fromFile(zip),config)
            val restored = LocalFiles.restore(context,Uri.fromFile(zip))
            assertEquals(config.home.media, restored.home.media)
            assertArrayEquals(byteArrayOf(1,2,3,4),LocalFiles.mediaFile(context,id).readBytes())
            ZipOutputStream(bad.outputStream()).use { out -> out.putNextEntry(ZipEntry("../escape.txt"));out.write("bad".toByteArray());out.closeEntry() }
            assertThrows(IllegalArgumentException::class.java) { LocalFiles.restore(context,Uri.fromFile(bad)) }
        } finally { mediaFile.delete();zip.delete();bad.delete() }
    }
    @Test fun actualAlarmReceiverStartsLocalAudioPlayback() {
        Assume.assumeTrue("Exact alarm permission required on this device",AdhanScheduler.exactAllowed(context))
        val id = UUID.randomUUID().toString();val file = LocalFiles.mediaFile(context,id)
        val samples = 8000
        val b = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray());b.putInt(36 + samples*2);b.put("WAVEfmt ".toByteArray());b.putInt(16);b.putShort(1);b.putShort(1);b.putInt(8000);b.putInt(16000);b.putShort(2);b.putShort(16);b.put("data".toByteArray());b.putInt(samples*2)
        repeat(samples) { i -> b.putShort((kotlin.math.sin(i * 2 * Math.PI * 440 / 8000) * 2000).toInt().toShort()) }
        file.writeBytes(b.array())
        try {
            val home = HomeConfig(media = listOf(LocalMedia(id,"Test tone",MediaKind.AUDIO,file.length())), prayer = PrayerSettings(audioId = id))
            ConfigStore(context).save(ProbeConfig(home = home))
            val started = System.currentTimeMillis()
            AdhanScheduler.scheduleTest(context,1500)
            compose.waitUntil(30000) { context.getSharedPreferences("adhan-runtime",Context.MODE_PRIVATE).getLong("last-prepared",0) >= started }
            assertTrue(context.getSharedPreferences("adhan-runtime",Context.MODE_PRIVATE).getLong("last-test-fired",0) >= started)
        } finally { AdhanPlayback.stop(context);file.delete() }
    }
}
