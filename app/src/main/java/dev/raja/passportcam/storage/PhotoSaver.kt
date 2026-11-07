package dev.raja.passportcam.storage

import android.content.Context
import android.net.Uri

/** MODULE E. Save into MediaStore so photos show up in the system file picker "Recent". */
object PhotoSaver {
    /**
     * Insert [jpeg] into MediaStore.Images under Pictures/Config.SAVE_FOLDER with DISPLAY_NAME [displayName] (".jpg" included),
     * MIME image/jpeg, DATE_ADDED/DATE_TAKEN = now, using IS_PENDING=1 while writing then 0 (API 29+; API 26-28 needs
     * a legacy file write + MediaScannerConnection, no WRITE permission is declared so use app-accessible approach or MediaStore insert).
     * Returns the content Uri. Blocking; call off main thread.
     */
    fun save(context: Context, jpeg: ByteArray, displayName: String): Uri = TODO("module E")

    /** Delete a photo this app saved. Returns true if removed. */
    fun delete(context: Context, uri: Uri): Boolean = TODO("module E")
}
