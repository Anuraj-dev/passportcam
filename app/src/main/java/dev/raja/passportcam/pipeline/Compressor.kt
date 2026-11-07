package dev.raja.passportcam.pipeline

import android.graphics.Bitmap
import dev.raja.passportcam.core.Config
import dev.raja.passportcam.core.PLog
import java.io.ByteArrayOutputStream

/** MODULE D. Get a square bitmap under Config.MAX_BYTES as JPEG while keeping the most quality possible. */
object Compressor {
    private const val AREA = "compressor"

    /**
     * [squareBitmap] is the high-resolution cropped face (at least Config.OUTPUT_SIDES_PX[0] px per side when the source allows).
     * Algorithm: for each side in Config.OUTPUT_SIDES_PX (largest first): scale to that side, binary-search the highest JPEG quality
     * in [MIN_QUALITY, MAX_QUALITY] with size <= MAX_BYTES; return the first side that fits. Return null if even the smallest side at
     * MIN_QUALITY is too big (caller shows an error). Must log each attempt via PLog.
     * CPU-heavy (scaling plus several JPEG encodes): call off the main thread. [squareBitmap] is never recycled.
     */
    fun compressUnderLimit(squareBitmap: Bitmap): PassportPhoto? {
        val startNs = System.nanoTime()
        val inputSide = minOf(squareBitmap.width, squareBitmap.height)
        val triedSides = HashSet<Int>()
        for (requestedSide in Config.OUTPUT_SIDES_PX) {
            // Never upscale: a source smaller than the requested side is attempted at its own size.
            val side = minOf(requestedSide, inputSide)
            if (side != requestedSide) {
                PLog.i(AREA, "source ${squareBitmap.width}x${squareBitmap.height} smaller than ${requestedSide}px, using ${side}px")
            }
            // Same size as an earlier attempt that failed at every quality: it would fail identically.
            if (!triedSides.add(side)) {
                PLog.d(AREA, "side=$side already tried, skipped")
                continue
            }
            val photo = encodeSide(squareBitmap, side)
            if (photo != null) {
                PLog.i(AREA, "chosen side=${photo.sidePx} quality=${photo.quality} bytes=${photo.sizeBytes} total=${elapsedMs(startNs)}ms")
                return photo
            }
        }
        PLog.w(AREA, "no side fits under ${Config.MAX_BYTES} bytes even at quality ${Config.MIN_QUALITY}; total=${elapsedMs(startNs)}ms")
        return null
    }

    /** Scales [source] to [side] x [side] and finds the highest passing quality. Null if even MIN_QUALITY is too big. */
    private fun encodeSide(source: Bitmap, side: Int): PassportPhoto? {
        val scaled = if (source.width == side && source.height == side) {
            source
        } else {
            Bitmap.createScaledBitmap(source, side, side, true)
        }
        try {
            val encoded = HashMap<Int, ByteArray>()
            // The helper's cap is inclusive, so MAX_BYTES - 1 keeps every photo strictly under MAX_BYTES (AGENTS rule).
            val quality = highestQualityUnder(Config.MAX_BYTES - 1, Config.MIN_QUALITY, Config.MAX_QUALITY) { q ->
                val bytes = encodeJpeg(scaled, q)
                encoded[q] = bytes
                val verdict = if (bytes.size < Config.MAX_BYTES) "fits" else "over"
                PLog.d(AREA, "side=$side quality=$q bytes=${bytes.size} $verdict")
                bytes.size
            } ?: run {
                PLog.d(AREA, "side=$side does not fit at quality ${Config.MIN_QUALITY}")
                return null
            }
            val jpeg = encoded.getValue(quality)
            return PassportPhoto(jpeg, side, quality)
        } finally {
            if (scaled !== source) scaled.recycle()
        }
    }

    private fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "JPEG encode failed at quality $quality" }
        return out.toByteArray()
    }

    private fun elapsedMs(startNs: Long): Long = (System.nanoTime() - startNs) / 1_000_000
}
