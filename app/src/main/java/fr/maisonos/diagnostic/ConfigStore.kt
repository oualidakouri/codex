package fr.maisonos.diagnostic

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import fr.maisonos.core.CameraConfig
import fr.maisonos.core.HomeConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class ProbeConfig(
    val version: Int = 2,
    val camera: CameraConfig = CameraConfig(),
    val idleSeconds: Int = 60,
    val motionThreshold: Float = 0.07f,
    val keepAwake: Boolean = true,
    val home: HomeConfig = HomeConfig(),
) {
    fun validate() {
        require(version == 2 && idleSeconds in 15..3600 && motionThreshold in 0.01f..0.5f)
        if (camera.host.isNotBlank()) camera.validate()
        home.validate()
    }
    fun exportSafe() = copy(camera = camera.exportSafe(), home = home.exportSafe())
    fun migrated(): ProbeConfig {
        require(version in 1..2)
        val cameras = if (version == 1 && home.cameras.isEmpty() && camera.host.isNotBlank()) listOf(camera) else home.cameras
        val profiles = if (version == 1 && cameras.isNotEmpty()) home.profiles.map { profile ->
            val widgets = cameras.map { c -> fr.maisonos.core.HomeWidget(kind = fr.maisonos.core.WidgetKind.CAMERA, y = 6, cameraId = c.id) }
            profile.copy(landscape = fr.maisonos.core.LayoutEngine.pack(profile.landscape + widgets, 6), portrait = fr.maisonos.core.LayoutEngine.pack(profile.portrait + widgets, 4))
        } else home.profiles
        return copy(version = 2, home = home.copy(cameras = cameras, profiles = profiles))
    }
}

/** Whole configuration encrypted atomically. Backups/exports never carry camera credentials. */
class ConfigStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "probe-config.enc"))
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("maison.config.v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("maison.config.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    fun load(): ProbeConfig {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return ProbeConfig()
        val bytes = file.readFully()
        require(bytes.size in 29..1048576)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return json.decodeFromString<ProbeConfig>(c.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)).migrated().also { it.validate() }
    }
    fun save(config: ProbeConfig) {
        config.validate()
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = c.iv + c.doFinal(json.encodeToString(config).toByteArray())
        require(bytes.size <= 1048576) { "Configuration trop volumineuse" }
        val out = file.startWrite()
        try { out.write(bytes); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out); throw e }
    }
    fun export(config: ProbeConfig): String = json.encodeToString(config.exportSafe())
    fun parseImport(text: String): ProbeConfig {
        require(text.toByteArray().size <= 1048576) { "Fichier trop volumineux" }
        return json.decodeFromString<ProbeConfig>(text).migrated().exportSafe().also { it.validate() }
    }
}
