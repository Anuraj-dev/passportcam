package dev.raja.passportcam.pipeline

import android.graphics.Bitmap

/** MODULE D. Get a square bitmap under Config.MAX_BYTES as JPEG while keeping the most quality possible. */
object Compressor {
    /**
     * [squareBitmap] is the high-resolution cropped face (at least Config.OUTPUT_SIDES_PX[0] px per side when the source allows).
     * Algorithm: for each side in Config.OUTPUT_SIDES_PX (largest first): scale to that side, binary-search the highest JPEG quality
     * in [MIN_QUALITY, MAX_QUALITY] with size <= MAX_BYTES; return the first side that fits. Return null if even the smallest side at
     * MIN_QUALITY is too big (caller shows an error). Must log each attempt via PLog.
     */
    fun compressUnderLimit(squareBitmap: Bitmap): PassportPhoto? = TODO("module D")
}
