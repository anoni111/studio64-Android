package com.studio64.android

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * - unpackApp: descomprime el servidor de Studio 64 (asset studio64-app.zip) en filesDir/studio64.
 * - importEngine: copia el motor compilado (web/engine, web/data, web/thumbs, server/data, server/samples)
 *   desde un .zip que el usuario genera en un PC con el instalador oficial de Studio 64.
 */
object Installer {
    /** Cambialo cuando cambie el contenido de studio64-app.zip para que se vuelva a descomprimir. */
    const val APP_VERSION = "0.2.4-beta.7"

    fun appDir(ctx: Context) = File(ctx.filesDir, "studio64")

    fun engineReady(ctx: Context): Boolean {
        val d = appDir(ctx)
        return File(d, "web/engine/sm64.us.f3dex2e.wasm").isFile && File(d, "web/engine/sm64.us.f3dex2e.js").isFile
    }

    private fun safeFile(root: File, rel: String): File? {
        val f = File(root, rel).canonicalFile
        return if (f.path.startsWith(root.canonicalPath + File.separator)) f else null
    }

    fun unpackApp(ctx: Context) {
        val dir = appDir(ctx)
        val marker = File(dir, ".app-version")
        if (marker.isFile && marker.readText() == APP_VERSION && File(dir, "android-main.mjs").isFile) return
        dir.mkdirs()
        val keepCatalog = engineReady(ctx) // el catalogo importado va con el motor: no pisarlo
        ctx.assets.open("studio64-app.zip").use { raw ->
            ZipInputStream(raw.buffered(1 shl 16)).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    if (e.isDirectory) continue
                    val name = e.name.replace('\\', '/')
                    if (keepCatalog && name == "server/data/catalog.json") continue
                    val out = safeFile(dir, name) ?: continue
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zin.copyTo(it, 1 shl 16) }
                }
            }
        }
        marker.writeText(APP_VERSION)
    }

    private val WANTED = Regex("(?:^|/)((?:web/(?:engine|thumbs|data)|server/(?:data|samples))/.+|engine/baserom\\.us\\.z64)$")

    /** Devuelve el numero de archivos copiados; lanza IllegalStateException si el zip no trae el motor. */
    fun importEngine(ctx: Context, uri: Uri, onProgress: (Int) -> Unit): Int {
        val dir = appDir(ctx)
        var count = 0
        var wasm = false
        var js = false
        val input = ctx.contentResolver.openInputStream(uri) ?: throw IllegalStateException("No se pudo abrir el archivo")
        input.use { raw ->
            ZipInputStream(raw.buffered(1 shl 16)).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    if (e.isDirectory) continue
                    val name = e.name.replace('\\', '/')
                    var rel = WANTED.find(name)?.groupValues?.get(1)
                    if (rel == null) {
                        val base = name.substringAfterLast('/')
                        if (base == "sm64.us.f3dex2e.wasm" || base == "sm64.us.f3dex2e.js") rel = "web/engine/$base"
                    }
                    if (rel == null) continue
                    val out = safeFile(dir, rel) ?: continue
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zin.copyTo(it, 1 shl 16) }
                    if (rel == "web/engine/sm64.us.f3dex2e.wasm") wasm = true
                    if (rel == "web/engine/sm64.us.f3dex2e.js") js = true
                    count++
                    onProgress(count)
                }
            }
        }
        if (!wasm || !js) {
            throw IllegalStateException(
                "El zip no contiene web/engine/sm64.us.f3dex2e.wasm y .js. " +
                    "Comprime las carpetas «web» y «server» de tu Studio 64 ya configurado en el PC."
            )
        }
        return count
    }

    fun romReady(ctx: Context) = File(appDir(ctx), "engine/baserom.us.z64").let { it.isFile && it.length() == ROM_SIZE }

    private const val ROM_SIZE = 8L * 1024 * 1024

    /** Importa tu ROM de SM64 (US) como engine/baserom.us.z64: acepta .z64, .v64, .n64 o un .zip con la ROM. */
    fun importRom(ctx: Context, uri: Uri) {
        val input = ctx.contentResolver.openInputStream(uri) ?: throw IllegalStateException("No se pudo abrir el archivo")
        var data = input.use { it.readBytes() }
        if (data.size > 4 && data[0] == 'P'.code.toByte() && data[1] == 'K'.code.toByte()) {
            var found: ByteArray? = null
            ZipInputStream(data.inputStream()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (!e.isDirectory && Regex("(?i)\\.(z64|v64|n64|rom)$").containsMatchIn(e.name)) { found = z.readBytes(); break }
                }
            }
            data = found ?: throw IllegalStateException("El zip no contiene ninguna ROM (.z64/.v64/.n64)")
        }
        if (data.size.toLong() != ROM_SIZE) throw IllegalStateException("La ROM debe medir 8 MB (mide ${data.size / 1024 / 1024} MB)")
        val h = data.copyOfRange(0, 4).map { it.toInt() and 0xFF }
        when {
            h == listOf(0x80, 0x37, 0x12, 0x40) -> {}                                   // z64 (big-endian)
            h == listOf(0x37, 0x80, 0x40, 0x12) -> for (i in 0 until data.size - 1 step 2) { // v64 (pares invertidos)
                val t = data[i]; data[i] = data[i + 1]; data[i + 1] = t
            }
            h == listOf(0x40, 0x12, 0x37, 0x80) -> for (i in 0 until data.size - 3 step 4) { // n64 (palabras invertidas)
                var t = data[i]; data[i] = data[i + 3]; data[i + 3] = t
                t = data[i + 1]; data[i + 1] = data[i + 2]; data[i + 2] = t
            }
            else -> throw IllegalStateException("Eso no parece una ROM de Nintendo 64")
        }
        val title = String(data, 0x20, 20, Charsets.ISO_8859_1)
        if (!title.startsWith("SUPER MARIO 64") || data[0x3E] != 'E'.code.toByte()) {
            throw IllegalStateException("Hace falta la ROM de Super Mario 64 versión US (NTSC)")
        }
        val out = File(appDir(ctx), "engine/baserom.us.z64")
        out.parentFile?.mkdirs()
        out.writeBytes(data)
        // si ya habia un kit construido con otra ROM, se rehace
        File(ctx.filesDir, "home/.cache/studio64/romkit").deleteRecursively()
    }
}
