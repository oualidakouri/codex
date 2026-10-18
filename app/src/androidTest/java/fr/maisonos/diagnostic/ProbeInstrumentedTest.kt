package fr.maisonos.diagnostic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import fr.maisonos.core.CameraConfig
import fr.maisonos.core.SafeXml
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** These tests require an Android device or emulator. Compilation alone is not execution. */
class ProbeInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun simulationAndNavigationAreExplicit() {
        compose.onNodeWithText("Simulation", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Banc de simulation").assertExists()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithText("Simuler : tu as du courrier !").performClick()
        compose.onNodeWithText("Tu as du courrier !").assertExists()
        compose.onNodeWithText("Tu as du courrier !").performClick()
        compose.onNodeWithText("Journal", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Configuration et preuves").assertExists()
    }
    @Test fun encryptedConfigurationSurvivesNewStoreInstanceAndExportExcludesSecrets() {
        val store = ConfigStore(ApplicationProvider.getApplicationContext())
        val previous = store.load()
        try {
            val config = ProbeConfig(camera = CameraConfig(host = "192.168.1.2", username = "test-user", password = "instrumented-secret", rtspUrl = "rtsp://192.168.1.2/live?token=sensitive"))
            store.save(config)
            assertEquals(config, ConfigStore(ApplicationProvider.getApplicationContext()).load())
            val export = store.export(config)
            assertFalse(export.contains("instrumented-secret")); assertFalse(export.contains("sensitive"))
            val restored = store.parseImport(export)
            assertEquals(config.camera.host, restored.camera.host); assertEquals("", restored.camera.password)
        } finally { store.save(previous) }
    }
    @Test fun androidXmlParserAcceptsSoapAndRejectsDtd() {
        val root = SafeXml.parse("<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"><s:Body><Ret>100</Ret></s:Body></s:Envelope>")
        assertEquals("100", SafeXml.text(root, "Ret"))
        assertThrows(IllegalArgumentException::class.java) { SafeXml.parse("<!DOCTYPE x [<!ENTITY a SYSTEM 'file:///etc/passwd'>]><x>&a;</x>") }
    }
}
