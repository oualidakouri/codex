package fr.maisonos.diagnostic

import android.content.Context
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.toArgb
import androidx.test.platform.app.InstrumentationRegistry
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
        val fixture = ProbeConfig(home = HomeConfig(liveDashboard = false, rules = listOf(LocalRule(durationSeconds = 60)), updates = UpdateSettings(enabled = false), news = NewsSettings(enabled = false)))
        compose.runOnUiThread { vm.save(fixture) }
        compose.waitUntil(20000) { vm.state.value.config == fixture && !vm.state.value.busy }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("nav-Accueil").performClick()
    }
    @After fun restore() {
        QuranPlayback.stop(context); AdhanPlayback.stop(context); AdhanPlayback.active.value = false; AdhanScheduler.cancel(context)
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
        val relay = LocalSwitch(kind = SwitchKind.TUYA_33, host = "192.168.1.3", deviceId = "fixture", password = "tuya-test-secret")
        val config = ProbeConfig(camera = camera, home = HomeConfig(cameras = listOf(camera), liveDashboard = false, switches = listOf(relay)))
        val store = ConfigStore(context); store.save(config)
        assertEquals(config, ConfigStore(context).load())
        val export = store.export(config)
        assertFalse(export.contains("instrumented-secret")); assertFalse(export.contains("sensitive")); assertFalse(export.contains("main-secret"))
        assertFalse(export.contains("tuya-test-secret"))
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
        assertEquals(5, migrated.version); assertEquals(300,migrated.idleSeconds)
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
    @Test fun updateApkMustHaveFutureVersionCorrectPackageAndSameSignature() {
        val appUpdates = androidx.lifecycle.ViewModelProvider(compose.activity)[ProbeViewModel::class.java].updates
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        for (name in listOf("future", "wrong-signer", "wrong-package", "same-version")) {
            val file = File(context.cacheDir, "fixture-$name.apk")
            try {
                assets.open("$name.apk").use { input -> file.outputStream().use { input.copyTo(it) } }
                val release = AppRelease(BuildConfig.VERSION_CODE.toLong() + 1, "fixture", "https://example.com/app.apk", "a".repeat(64), context.packageName, file.length())
                if (name == "future") appUpdates.verifyArchive(file, release)
                else assertThrows(IllegalArgumentException::class.java) { appUpdates.verifyArchive(file, release) }
            } finally { file.delete() }
        }
    }
    @Test fun darkAndLightThemesApplyReadableTextToNavigationAndRoot() {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[ProbeViewModel::class.java]
        for (theme in listOf("forest", "sand")) {
            compose.runOnUiThread { vm.updateHome { it.copy(theme = theme) } }
            compose.waitUntil(15000) { vm.state.value.config.home.theme == theme }
            compose.waitForIdle()
            val expected = ThemeCatalog.forHome(vm.state.value.config.home).text.toInt()
            for (node in listOf(compose.onNodeWithText("Bienvenue à la maison"), compose.onNodeWithText("Coran", useUnmergedTree = true))) {
                val layouts = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(expected,layouts.single().layoutInput.style.color.toArgb())
            }
        }
    }
    @Test fun dailyDuaIsArabicOnlyAndSwitchesTabIsRemoved() {
        compose.onNodeWithTag("nav-Interrupteurs").assertDoesNotExist()
        compose.onNodeWithTag("nav-Douas").performClick()
        val dua = Duas.daily(context, java.time.LocalDate.now(), 0)
        compose.onNodeWithText(dua.french).assertDoesNotExist()
        compose.onNodeWithTag("nav-Coran").performClick()
        compose.onNodeWithText("Récitants et sourates · MP3Quran").assertExists()
    }
    @Test fun embeddedQuranCatalogHasSevenRealRecitersAnd114SurahNames() {
        val catalog = kotlinx.serialization.json.Json.decodeFromString<QuranCatalog>(context.assets.open("quran-catalog.json").bufferedReader().use { it.readText() })
        assertEquals(7,catalog.recitations.size)
        assertEquals((1..114).toList(),catalog.surahs.map { it.id })
        assertTrue(catalog.recitations.all { it.server.startsWith("https://") && it.surahs == (1..114).toList() })
        assertTrue(catalog.recitations.any { it.name == "Mishary Alafasi" })
        assertEquals("الفاتحة",catalog.surahs.first().name)
    }
    @Test fun networkQuranPlaybackSupportsSeekVolumeAndStop() {
        val samples = 960000
        val wav = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray());wav.putInt(36 + samples*2);wav.put("WAVEfmt ".toByteArray());wav.putInt(16);wav.putShort(1);wav.putShort(1);wav.putInt(8000);wav.putInt(16000);wav.putShort(2);wav.putShort(16);wav.put("data".toByteArray());wav.putInt(samples*2)
        repeat(samples) { i -> wav.putShort((kotlin.math.sin(i * 2 * Math.PI * 440 / 8000) * 500).toInt().toShort()) }
        val server = java.net.ServerSocket(0,2,java.net.InetAddress.getByName("127.0.0.1"))
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        executor.submit {
            while (!server.isClosed) try {
                server.accept().use { socket ->
                    socket.soTimeout = 10000
                    val input = socket.getInputStream().bufferedReader()
                    var offset = 0
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Range: bytes=",true)) offset = line.substringAfter('=').substringBefore('-').toInt().coerceIn(0,wav.array().size-1)
                    }
                    socket.getOutputStream().use { output ->
                        val status = if (offset > 0) "206 Partial Content" else "200 OK"
                        val range = if (offset > 0) "Content-Range: bytes $offset-${wav.array().size-1}/${wav.array().size}\r\n" else ""
                        output.write("HTTP/1.1 $status\r\nContent-Type: audio/wav\r\nAccept-Ranges: bytes\r\n${range}Content-Length: ${wav.array().size-offset}\r\nConnection: close\r\n\r\n".toByteArray())
                        output.write(wav.array(),offset,wav.array().size-offset);output.flush()
                    }
                }
            } catch (_: java.io.IOException) { if (server.isClosed) break }
        }
        try {
            val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[ProbeViewModel::class.java]
            val q = QuranSettings(url = "http://127.0.0.1:${server.localPort}/recitation.wav")
            compose.runOnUiThread { vm.updateHome { it.copy(quran = q) } }
            compose.waitUntil(15000) { vm.state.value.config.home.quran == q }
            compose.onNodeWithTag("nav-Coran").performClick()
            compose.onNodeWithTag("quran-play").performScrollTo().performClick()
            compose.waitUntil(30000) { QuranPlayback.state.value.playing }
            assertTrue(QuranPlayback.state.value.active)
            compose.waitUntil(15000) { QuranPlayback.state.value.seekable && QuranPlayback.state.value.durationMs > 100000 }
            compose.onNodeWithTag("quran-timeline").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(45000f) }
            compose.waitUntil(15000) { QuranPlayback.state.value.positionMs >= 44000 }
            compose.onNodeWithTag("quran-volume-down").performScrollTo().performClick()
            compose.waitUntil(15000) { QuranPlayback.state.value.volume < 0.75f }
            compose.onNodeWithTag("quran-stop").performScrollTo().performClick()
            compose.waitUntil(10000) { !QuranPlayback.state.value.active }
        } finally { QuranPlayback.stop(context);server.close();executor.shutdownNow() }
    }
    @Test fun dashboardAudioButtonsChangePersistedSurahReciterAndVolume() {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[ProbeViewModel::class.java]
        val catalog = vm.quranCatalog.state.value.catalog
        val first = catalog.recitations.first()
        val q = QuranSettings(custom = false,reciterId = first.reciterId,moshafId = first.moshafId,reciter = first.name,server = first.server,surah = 1)
        compose.runOnUiThread { vm.updateHome { it.copy(quran = q,profiles = listOf(LayoutProfile(landscape = listOf(HomeWidget(kind = WidgetKind.QURAN,w = 6,h = 5))))) } }
        compose.waitUntil(15000) { vm.state.value.config.home.quran == q }
        compose.onNodeWithTag("quran-volume-down").performScrollTo().performClick()
        compose.waitUntil(15000) { vm.state.value.config.home.quran.volume < 0.75f }
        compose.onNodeWithTag("quran-volume-up").performScrollTo().performClick()
        compose.waitUntil(15000) { vm.state.value.config.home.quran.volume > 0.75f }
        compose.onNodeWithTag("quran-surah-next").performScrollTo().performClick()
        compose.waitUntil(15000) { vm.state.value.config.home.quran.surah == 2 }
        compose.onNodeWithTag("quran-surah-prev").performScrollTo().performClick()
        compose.waitUntil(15000) { vm.state.value.config.home.quran.surah == 1 }
        compose.onNodeWithTag("quran-reciter-next").performScrollTo().performClick()
        compose.waitUntil(15000) { vm.state.value.config.home.quran.reciterId != first.reciterId }
        compose.onNodeWithTag("quran-reciter-prev").performScrollTo().performClick()
        compose.waitUntil(15000) { ConfigStore(context).load().home.quran.reciterId == first.reciterId }
        compose.onNodeWithTag("quran-timeline").assertIsNotEnabled()
    }
    @Test fun pumpkinDemoClosesAndCannotSurviveBackgroundOrInterruptAdhan() {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[ProbeViewModel::class.java]
        compose.onNodeWithTag("nav-Paramètres").performClick()
        compose.onNodeWithTag("pumpkin-demo").performScrollTo().performClick()
        compose.onNodeWithText("SIMULATION · regard animé sans caméra").assertExists()
        assertTrue(vm.state.value.pumpkinActive)
        compose.onNodeWithTag("pumpkin-close").performClick()
        compose.onNodeWithTag("pumpkin-overlay").assertDoesNotExist()
        compose.runOnUiThread { vm.startPumpkin(true); vm.foreground(false) }
        compose.waitUntil(10000) { !vm.state.value.pumpkinActive }
        compose.runOnUiThread { vm.foreground(true); AdhanPlayback.active.value = true; vm.startPumpkin(true) }
        assertFalse(vm.state.value.pumpkinActive)
        compose.runOnUiThread { AdhanPlayback.active.value = false }
    }
    @Test fun automaticPumpkinOpensFrontCameraAndStopsInBackground() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName,android.Manifest.permission.CAMERA)
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[ProbeViewModel::class.java]
        compose.runOnUiThread { vm.interact(); vm.updateHome { it.copy(pumpkin = PumpkinSettings(enabled = true,idleSeconds = 5,durationSeconds = 60)) } }
        compose.waitUntil(20000) { vm.state.value.pumpkinActive }
        compose.waitUntil(30000) { vm.state.value.pumpkinFaceFrames > 0 || vm.state.value.pumpkinCameraError }
        assertFalse("Camera binding or frame analysis failed: " + vm.state.value.diagnostics["Citrouille"]?.detail,vm.state.value.pumpkinCameraError)
        assertTrue("No front-camera frame analyzed",vm.state.value.pumpkinFaceFrames > 0)
        assertFalse(vm.state.value.pumpkinSimulated)
        // Virtual camera frames demonstrate the pipeline, never a real visitor's face.
        compose.runOnUiThread { vm.foreground(false) }
        compose.waitUntil(10000) { !vm.state.value.pumpkinActive && !vm.state.value.cameraEnabled }
        compose.runOnUiThread { vm.foreground(true); vm.updateHome { it.copy(pumpkin = PumpkinSettings()) } }
    }
    @Test fun pumpkinShaderDrawsTransparent3dGeometryAndMovesPupils() {
        val display = android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        assertTrue(android.opengl.EGL14.eglInitialize(display,version,0,version,1))
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1);val count = IntArray(1)
        val attributes = intArrayOf(android.opengl.EGL14.EGL_RENDERABLE_TYPE,4,android.opengl.EGL14.EGL_SURFACE_TYPE,1,
            android.opengl.EGL14.EGL_RED_SIZE,8,android.opengl.EGL14.EGL_GREEN_SIZE,8,android.opengl.EGL14.EGL_BLUE_SIZE,8,android.opengl.EGL14.EGL_ALPHA_SIZE,8,android.opengl.EGL14.EGL_DEPTH_SIZE,16,android.opengl.EGL14.EGL_NONE)
        assertTrue(android.opengl.EGL14.eglChooseConfig(display,attributes,0,configs,0,1,count,0))
        val eglContext = android.opengl.EGL14.eglCreateContext(display,configs[0],android.opengl.EGL14.EGL_NO_CONTEXT,intArrayOf(android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION,2,android.opengl.EGL14.EGL_NONE),0)
        val surface = android.opengl.EGL14.eglCreatePbufferSurface(display,configs[0],intArrayOf(android.opengl.EGL14.EGL_WIDTH,320,android.opengl.EGL14.EGL_HEIGHT,200,android.opengl.EGL14.EGL_NONE),0)
        try {
            assertTrue(android.opengl.EGL14.eglMakeCurrent(display,surface,surface,eglContext))
            val renderer = PumpkinRenderer();renderer.onSurfaceCreated(null,null);renderer.onSurfaceChanged(null,320,200)
            fun snapshot(): ByteArray {
                renderer.onDrawFrame(null)
                val pixels = ByteBuffer.allocateDirect(320*200*4)
                android.opengl.GLES20.glReadPixels(0,0,320,200,android.opengl.GLES20.GL_RGBA,android.opengl.GLES20.GL_UNSIGNED_BYTE,pixels)
                return ByteArray(pixels.capacity()).also { pixels.get(it) }
            }
            val center = snapshot();assertNull(renderer.error);assertEquals(0,center[3].toInt())
            assertTrue(center.indices.count { it % 4 == 3 && center[it].toInt() != 0 } > 5000)
            renderer.targetX = 1f;renderer.targetY = 0.5f
            repeat(12) { renderer.onDrawFrame(null) }
            val moved = snapshot()
            val colors = IntArray(320*200) { i ->
                val j = ((199-i/320)*320+i%320)*4
                ((center[j+3].toInt() and 255) shl 24) or ((center[j].toInt() and 255) shl 16) or ((center[j+1].toInt() and 255) shl 8) or (center[j+2].toInt() and 255)
            }
            android.graphics.Bitmap.createBitmap(colors,320,200,android.graphics.Bitmap.Config.ARGB_8888).let { bitmap ->
                File(context.cacheDir,"pumpkin-render.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            }
            assertTrue(center.indices.count { center[it] != moved[it] } > 100)
            assertTrue(renderer.frames >= 14)
        } finally {
            android.opengl.EGL14.eglMakeCurrent(display,android.opengl.EGL14.EGL_NO_SURFACE,android.opengl.EGL14.EGL_NO_SURFACE,android.opengl.EGL14.EGL_NO_CONTEXT)
            android.opengl.EGL14.eglDestroySurface(display,surface);android.opengl.EGL14.eglDestroyContext(display,eglContext);android.opengl.EGL14.eglReleaseThread()
        }
    }
    @Test fun v11MigrationRemovesSwitchWidgetsAndKeepsExistingLayoutIds() {
        val text = """{"version":3,"home":{"lastPage":"Interrupteurs","news":{"enabled":false},"profiles":[{"id":"existing","name":"Mon profil","landscape":[{"id":"clock-id","kind":"CLOCK","x":0,"y":0,"w":3,"h":2},{"id":"old-switch","kind":"SWITCHES","x":3,"y":0,"w":3,"h":2}],"portrait":[{"id":"clock-id","kind":"CLOCK","x":0,"y":0,"w":3,"h":2},{"id":"old-switch","kind":"SWITCHES","x":0,"y":2,"w":3,"h":2}]}]}}"""
        val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey("maison.config.v1", null)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        File(context.filesDir,"probe-config.enc").writeBytes(cipher.iv + cipher.doFinal(text.toByteArray()))
        val migrated = ConfigStore(context).load()
        assertEquals(5,migrated.version);assertEquals("Accueil",migrated.home.lastPage)
        val widgets = migrated.home.profile().landscape
        assertTrue(widgets.any { it.id == "clock-id" && it.kind == WidgetKind.CLOCK })
        assertTrue(widgets.none { it.kind == WidgetKind.SWITCHES })
        assertTrue(widgets.any { it.kind == WidgetKind.QURAN });assertTrue(widgets.any { it.kind == WidgetKind.NEWS })
    }
    @Test fun widgetSizeCanBeChangedWithButtonsWithoutDragging() {
        val old = ConfigStore(context).load().home.profile().landscape.first { it.kind == WidgetKind.CLOCK }
        compose.onNodeWithTag("edit-dashboard").performClick()
        compose.onNodeWithTag("widget-settings-CLOCK").performClick()
        compose.onNodeWithText("Largeur −").performClick()
        compose.onNodeWithText("Enregistrer").performClick()
        compose.waitUntil(15000) { ConfigStore(context).load().home.profile().landscape.first { it.id == old.id }.w == old.w - 1 }
    }
    @Test fun v1ConfigurationUpgradeKeepsBothCameraSecretsAndAddsTwoHomeTiles() {
        val text = """{"version":2,"camera":{"id":"first","host":"192.168.1.2","username":"fixture","password":"first-secret"},"home":{"cameras":[{"id":"first","host":"192.168.1.2","username":"fixture","password":"first-secret"},{"id":"second","host":"192.168.1.3","username":"fixture","password":"second-secret"}],"maxStreams":1,"lastPage":"Caméras","theme":"classic"}}"""
        val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey("maison.config.v1", null)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        File(context.filesDir,"probe-config.enc").writeBytes(cipher.iv + cipher.doFinal(text.toByteArray()))
        val migrated = ConfigStore(context).load()
        assertEquals(5,migrated.version); assertEquals("first-secret",migrated.camera.password)
        assertEquals(listOf("first-secret","second-secret"),migrated.home.cameras.map { it.password })
        assertEquals(2,migrated.home.maxStreams); assertEquals("Caméras",migrated.home.lastPage)
        for (tiles in listOf(migrated.home.profile().landscape,migrated.home.profile().portrait)) {
            assertEquals(setOf("first","second"),tiles.filter { it.kind == WidgetKind.CAMERA && it.y == 0 && it.page == 0 }.map { it.cameraId }.toSet())
        }
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
