package fr.maisonos.diagnostic

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import fr.maisonos.core.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.*
import java.time.LocalDate
import java.util.UUID
import java.util.zip.*

object LocalFiles {
    fun bitmap(context: Context, id: String): android.graphics.Bitmap? {
        val file = mediaFile(context, id)
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1000) sample *= 2
        return android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
    }
    fun mediaFile(context: Context, id: String): File {
        require(UUID.fromString(id).toString() == id)
        return File(File(context.filesDir, "media").apply { mkdirs() }, "$id.bin")
    }
    fun import(context: Context, uri: Uri, kind: MediaKind): LocalMedia {
        val id = UUID.randomUUID().toString()
        val file = mediaFile(context, id)
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { q -> if (q.moveToFirst()) q.getString(0) else null }?.take(180) ?: "Média local"
        try {
            val bytes = context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { out -> copyLimited(input, out, 100 * 1024 * 1024L) } }
            require(bytes > 0)
            return LocalMedia(id, name, kind, bytes)
        } catch (e: Exception) { file.delete(); throw e }
    }
    fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long {
        val buf = ByteArray(16384); var total = 0L
        while (true) { val n = input.read(buf); if (n < 0) break; total += n; require(total <= limit) { "Média trop volumineux" }; output.write(buf, 0, n) }
        return total
    }
    fun backup(context: Context, uri: Uri, config: ProbeConfig) {
        val store = ConfigStore(context)
        context.contentResolver.openOutputStream(uri)!!.use { raw ->
            ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                zip.putNextEntry(ZipEntry("configuration.json")); zip.write(store.export(config).toByteArray()); zip.closeEntry()
                var total = 0L
                config.home.media.forEach { media ->
                    val file = mediaFile(context, media.id)
                    if (file.exists()) {
                        total += file.length(); require(total <= 256 * 1024 * 1024L)
                        zip.putNextEntry(ZipEntry("media/${media.id}.bin"))
                        file.inputStream().use { copyLimited(it, zip, 100 * 1024 * 1024L) }; zip.closeEntry()
                    }
                }
            }
        }
    }
    /** Stage the whole archive before installing any files; reject traversal and zip bombs. */
    fun restore(context: Context, uri: Uri): ProbeConfig {
        val staging = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            var config: ProbeConfig? = null; var total = 0L; var count = 0
            val seen = mutableSetOf<String>()
            context.contentResolver.openInputStream(uri)!!.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        require(++count <= 256 && seen.add(entry.name) && !entry.isDirectory)
                        if (entry.name == "configuration.json") {
                            val out = ByteArrayOutputStream(); val n = copyLimited(zip, out, 1048576); total += n
                            config = ConfigStore(context).parseImport(out.toString("UTF-8"))
                        } else {
                            require(entry.name.matches(Regex("media/[0-9a-f-]{36}\\.bin")))
                            val id = entry.name.removePrefix("media/").removeSuffix(".bin")
                            require(UUID.fromString(id).toString() == id)
                            File(staging, "$id.bin").outputStream().use { total += copyLimited(zip, it, 100 * 1024 * 1024L) }
                        }
                        require(total <= 256 * 1024 * 1024L); zip.closeEntry()
                    }
                }
            }
            val restored = checkNotNull(config)
            val allowed = restored.home.media.map { it.id }.toSet()
            staging.listFiles()?.forEach { file ->
                require(file.nameWithoutExtension in allowed)
                val declared = restored.home.media.first { it.id == file.nameWithoutExtension }
                require(file.length() == declared.bytes)
            }
            staging.listFiles()?.forEach { file ->
                val target = mediaFile(context, file.nameWithoutExtension)
                // Existing file with this ID is retained only if byte-identical.
                if (target.exists()) require(sha(target) == sha(file)) else file.copyTo(target)
            }
            return restored
        } finally { staging.deleteRecursively() }
    }
    private fun sha(file: File): String = file.inputStream().use { stream ->
        val md = java.security.MessageDigest.getInstance("SHA-256"); val buf = ByteArray(16384)
        while (true) { val n = stream.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        md.digest().joinToString("") { "%02x".format(it) }
    }
}

@Serializable data class Dua(val id: String, val title: String, val arabic: String, val french: String, val footnotes: String = "", val source: String, val category: String)
@Serializable private data class DuaAsset(val edition: String, val revision: String, val duas: List<Dua>)
object Duas {
    private var cache: List<Dua>? = null
    private val json = Json { ignoreUnknownKeys = true }
    @Synchronized fun all(context: Context): List<Dua> = cache ?: json.decodeFromString<DuaAsset>(context.assets.open("duas.json").bufferedReader().use { it.readText() }).duas.also { cache = it }
    fun daily(context: Context, date: LocalDate, offset: Int = 0): Dua {
        val all = all(context); return all[Math.floorMod(date.toEpochDay() + offset, all.size.toLong()).toInt()]
    }
}
