package dev.raja.passportcam.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dev.raja.passportcam.core.Config
import dev.raja.passportcam.core.PLog
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val AREA = "saver"
private const val MIME_JPEG = "image/jpeg"
private val RELATIVE_PATH = Environment.DIRECTORY_PICTURES + "/" + Config.SAVE_FOLDER
private val STAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)

private val isScoped: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

/**
 * Pure. Returns "passport_yyyyMMdd_HHmmss_N.jpg" for [nowMillis] in the device default zone.
 * [index] is 1-based and is used verbatim as N.
 */
fun passportFileName(index: Int, nowMillis: Long): String {
    require(index >= 1) { "index is 1-based, got $index" }
    val stamp = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).format(STAMP_FORMAT)
    return "passport_${stamp}_$index.jpg"
}

/** MODULE E. Save into MediaStore so photos show up in the system file picker "Recent". */
object PhotoSaver {
    /**
     * Insert [jpeg] into MediaStore.Images under Pictures/Config.SAVE_FOLDER with DISPLAY_NAME [displayName] (".jpg" included),
     * MIME image/jpeg, DATE_ADDED/DATE_TAKEN = now, using IS_PENDING=1 while writing then 0 (API 29+; API 26-28 needs
     * a legacy file write + MediaScannerConnection, no WRITE permission is declared so use app-accessible approach or MediaStore insert).
     * Returns the content Uri. Blocking; call off main thread.
     *
     * Units: DATE_ADDED and DATE_MODIFIED are epoch seconds; DATE_TAKEN is epoch milliseconds (MediaStore convention).
     * If MediaStore renames the file on a name clash, the returned Uri still points at the inserted row; the actual
     * name is logged. Refuses (IllegalArgumentException) any JPEG >= Config.MAX_BYTES as a last-line size guard.
     * If the write fails after the row is inserted, the row is deleted and the exception is rethrown.
     *
     * API 29+ writes to MediaStore Pictures/PassportPhotos and is visible in Recents and Photos.
     *
     * API 26-28 LIMITATION: WRITE_EXTERNAL_STORAGE is deliberately not declared, so MediaStore is expected to refuse
     * the insert. On that refusal the JPEG is written to the app's own external Pictures/PassportPhotos folder
     * (no permission needed) and a file:// Uri is returned. Those files are NOT indexed by MediaStore, so they do
     * NOT appear in Recents or Photos on API 26-28. Logged with PLog.w. The DATA-path insert itself is unverified
     * on device.
     */
    fun save(context: Context, jpeg: ByteArray, displayName: String): Uri {
        require(jpeg.size < Config.MAX_BYTES) { "refusing to save ${jpeg.size} bytes; cap is ${Config.MAX_BYTES}" }
        val resolver = context.contentResolver
        val nowMillis = System.currentTimeMillis()
        val legacyPath = File(legacyPublicDir(), displayName).path
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_JPEG)
            put(MediaStore.Images.Media.DATE_ADDED, nowMillis / 1000)
            put(MediaStore.Images.Media.DATE_MODIFIED, nowMillis / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, nowMillis)
            if (isScoped) {
                put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                put(MediaStore.Images.Media.DATA, legacyPath)
            }
        }

        val uri = try {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        } catch (e: SecurityException) {
            if (isScoped) {
                PLog.e(AREA, "MediaStore insert denied name=$displayName", e)
                throw e
            }
            PLog.w(AREA, "MediaStore insert denied on API ${Build.VERSION.SDK_INT} (WRITE_EXTERNAL_STORAGE not declared, by design); using app-only folder", e)
            return saveAppOnly(context, jpeg, displayName)
        }
        if (uri == null) {
            PLog.e(AREA, "MediaStore insert returned null name=$displayName")
            throw IOException("MediaStore insert returned null for $displayName")
        }

        try {
            resolver.openOutputStream(uri)?.use { it.write(jpeg) }
                ?: throw IOException("openOutputStream returned null for $uri")
            if (isScoped) {
                val cleared = resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                if (cleared < 1) throw IOException("could not clear IS_PENDING for $uri")
            }
        } catch (t: Throwable) {
            val removed = runCatching { resolver.delete(uri, null, null) }.getOrDefault(0)
            PLog.e(AREA, "write failed name=$displayName uri=$uri; removed half-created row rows=$removed", t)
            throw t
        }

        val savedName = queryDisplayName(resolver, uri) ?: displayName
        val location = if (isScoped) RELATIVE_PATH else legacyPath
        PLog.i(AREA, "saved name=$savedName (asked $displayName) bytes=${jpeg.size} uri=$uri location=$location")
        return uri
    }

    /**
     * Delete a photo this app saved. Returns true if removed.
     * Deletes only what this app saved: a MediaStore row on the MediaStore authority, or a file under this app's
     * own folder (API 26-28 fallback). Anything else returns false without touching the file system.
     * On API 30+ the OS lets the app delete rows it inserted without a prompt. A SecurityException (including API 29
     * RecoverableSecurityException, which would need a user prompt that is not shown here) is logged and returns false.
     */
    fun delete(context: Context, uri: Uri): Boolean {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return deleteAppOnly(context, uri)
        if (uri.authority != MediaStore.AUTHORITY) {
            PLog.w(AREA, "refusing delete of non-MediaStore uri=$uri")
            return false
        }
        return try {
            val rows = context.contentResolver.delete(uri, null, null)
            PLog.i(AREA, "delete uri=$uri rows=$rows")
            rows > 0
        } catch (e: SecurityException) {
            PLog.e(AREA, "delete denied uri=$uri (needs user consent or row not owned by app)", e)
            false
        }
    }

    private fun legacyPublicDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), Config.SAVE_FOLDER)

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    /**
     * API 26-28 fallback. Writes to the app's external Pictures/PassportPhotos (no permission needed). Files here are
     * not in MediaStore: not in Recents or Photos. Name clashes get a " (n)" suffix, mirroring MediaStore's rename.
     */
    private fun saveAppOnly(context: Context, jpeg: ByteArray, displayName: String): Uri {
        val dir = appOnlyDir(context) ?: throw IOException("app external Pictures folder unavailable")
        dir.mkdirs()
        val stem = displayName.substringBeforeLast('.')
        val ext = displayName.substringAfterLast('.', "jpg")
        var file = File(dir, displayName)
        var n = 1
        while (!file.createNewFile()) {
            file = File(dir, "$stem ($n).$ext")
            n++
        }
        try {
            file.outputStream().use { it.write(jpeg) }
        } catch (t: Throwable) {
            file.delete()
            PLog.e(AREA, "app-only write failed name=${file.name}", t)
            throw t
        }
        val uri = Uri.fromFile(file)
        PLog.w(AREA, "saved app-only (not in MediaStore, not in Recents) name=${file.name} bytes=${jpeg.size} uri=$uri")
        return uri
    }

    private fun appOnlyDir(context: Context): File? =
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let { File(it, Config.SAVE_FOLDER) }

    private fun deleteAppOnly(context: Context, uri: Uri): Boolean {
        val path = uri.path ?: return false
        val file = File(path)
        val inside = runCatching {
            val dir = appOnlyDir(context) ?: return false
            file.canonicalPath.startsWith(dir.canonicalPath + File.separator)
        }.getOrDefault(false)
        if (!inside) {
            PLog.w(AREA, "refusing delete outside app folder path=$path")
            return false
        }
        val ok = file.delete()
        if (ok) PLog.i(AREA, "delete file path=$path") else PLog.w(AREA, "delete file failed or missing path=$path")
        return ok
    }
}
