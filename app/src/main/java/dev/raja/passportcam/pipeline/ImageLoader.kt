package dev.raja.passportcam.pipeline

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dev.raja.passportcam.core.Config
import dev.raja.passportcam.core.PLog
import java.io.IOException
import java.io.InputStream

/** MODULE C. Decode a Uri to an upright bitmap. */
object ImageLoader {
    private const val AREA = "loader"

    /**
     * Decode [uri] (content:// or file://), downsample so the longest side <= Config.MAX_DECODE_SIDE_PX,
     * and apply the EXIF orientation so the returned bitmap is visually upright. Runs blocking; call off main thread.
     * Throws on unreadable input.
     */
    fun loadUpright(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val t0 = System.nanoTime()
        try {
            // Three short opens: bounds, EXIF, pixels. ContentResolver handles content:// and file:// alike.
            val (srcW, srcH) = readBounds(resolver, uri)
            val orientation = readOrientation(resolver, uri)
            val sample = sampleSizeFor(srcW, srcH, Config.MAX_DECODE_SIDE_PX)
            PLog.i(AREA, "source ${srcW}x$srcH, sample=$sample, exif orientation=$orientation")

            val decoded = openStream(resolver, uri).use { input ->
                BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: throw IOException("Not a decodable image: $uri")
            val tDecode = System.nanoTime()

            val matrix = orientationMatrix(orientation)
            val upright = if (matrix == null) {
                decoded
            } else {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                    decoded.recycle()
                }
            }
            PLog.i(
                AREA,
                "decoded ${upright.width}x${upright.height} upright (rotated=${matrix != null}) " +
                    "decode=${ms(t0, tDecode)}ms orient=${ms(tDecode, System.nanoTime())}ms total=${ms(t0, System.nanoTime())}ms",
            )
            return upright
        } catch (t: Throwable) {
            PLog.e(AREA, "failed to load $uri", t)
            throw t
        }
    }

    private fun readBounds(resolver: ContentResolver, uri: Uri): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(resolver, uri).use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw IOException("Not a decodable image (no bounds): $uri")
        }
        return options.outWidth to options.outHeight
    }

    /**
     * EXIF orientation tag (ExifInterface.ORIENTATION_*). A missing or unreadable tag is not fatal: the
     * pixels are still decodable, so fall back to NORMAL and warn.
     */
    private fun readOrientation(resolver: ContentResolver, uri: Uri): Int {
        return try {
            openStream(resolver, uri).use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        } catch (e: IOException) {
            PLog.w(AREA, "EXIF unreadable, assuming upright", e)
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun openStream(resolver: ContentResolver, uri: Uri): InputStream =
        resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")

    /**
     * Smallest power-of-two sample size so that the longest side of the decoded image is <= [maxSide].
     * Uses ceiling division because BitmapFactory rounds the decoded size up, not down.
     */
    internal fun sampleSizeFor(width: Int, height: Int, maxSide: Int): Int {
        val longest = maxOf(width, height)
        var sample = 1
        while ((longest + sample - 1) / sample > maxSide) sample *= 2
        return sample
    }

    /**
     * Matrix that maps the stored pixels onto the visually upright image (Android's standard EXIF mapping,
     * including the mirrored variants used by some front cameras). Null means no transform is needed.
     */
    internal fun orientationMatrix(orientation: Int): Matrix? {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                m.setRotate(180f)
                m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                m.setRotate(90f)
                m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                m.setRotate(-90f)
                m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.setRotate(-90f)
            else -> return null // NORMAL, UNDEFINED, or an unknown tag
        }
        return m
    }

    private fun ms(fromNanos: Long, toNanos: Long): Long = (toNanos - fromNanos) / 1_000_000
}
