package dev.raja.passportcam.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

/** MODULE C. Decode a Uri to an upright bitmap. */
object ImageLoader {
    /**
     * Decode [uri] (content:// or file://), downsample so the longest side <= Config.MAX_DECODE_SIDE_PX,
     * and apply the EXIF orientation so the returned bitmap is visually upright. Runs blocking; call off main thread.
     * Throws on unreadable input.
     */
    fun loadUpright(context: Context, uri: Uri): Bitmap = TODO("module C")
}
