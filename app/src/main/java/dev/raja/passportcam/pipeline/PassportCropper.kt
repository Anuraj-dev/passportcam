package dev.raja.passportcam.pipeline

import android.graphics.Bitmap
import android.graphics.Rect
import dev.raja.passportcam.core.Config
import dev.raja.passportcam.core.PLog
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A square region in source pixel coordinates. Plain Ints so the maths runs on the JVM without android.graphics. */
internal data class SquareCrop(val left: Int, val top: Int, val side: Int)

/** MODULE C. Passport-style square crop around a face. */
object PassportCropper {
    private const val AREA = "cropper"

    /**
     * ML Kit's box is roughly brow-to-chin. The crown sits about this fraction of the box height above the box top.
     * Midpoint of the 0.25-0.35 range for an adult head (crown-to-chin is about 1.3x brow-to-chin).
     */
    private const val CROWN_EXTENSION = 0.30f

    /**
     * Pure maths (unit-testable, no Android Bitmap needed): the square region of the source to crop for [face].
     * Rules: square; head height ~Config.HEAD_HEIGHT_FRACTION of the square; eyes ~45-55% from top (shift crop so face sits
     * slightly above centre, room for shoulders below); clamp to [imageW]x[imageH] by shifting first, shrinking second.
     * The returned rect lies fully inside the image. Note ML Kit face bounds cover roughly brow-to-chin, not crown: allow for hair/crown above.
     */
    fun computeCropRect(face: Rect, imageW: Int, imageH: Int): Rect {
        val c = squareCropFor(face.left, face.top, face.right, face.bottom, imageW, imageH)
        PLog.i(
            AREA,
            "face ${face.width()}x${face.height()} in ${imageW}x${imageH} -> crop left=${c.left} top=${c.top} side=${c.side}",
        )
        return Rect(c.left, c.top, c.left + c.side, c.top + c.side)
    }

    /** Crop [source] with [computeCropRect] and scale to [sidePx] x [sidePx]. */
    fun crop(source: Bitmap, face: DetectedFace, sidePx: Int): Bitmap {
        require(sidePx > 0) { "sidePx must be positive, was $sidePx" }
        val t0 = System.nanoTime()
        val rect = computeCropRect(face.bounds, source.width, source.height)
        // createBitmap returns [source] itself when the region is the whole bitmap, so guard before recycling.
        val region = Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height())
        val scaled = Bitmap.createScaledBitmap(region, sidePx, sidePx, true)
        if (region !== source && region !== scaled) region.recycle()
        PLog.i(
            AREA,
            "cropped ${rect.width()}px -> ${sidePx}px in ${(System.nanoTime() - t0) / 1_000_000}ms",
        )
        return scaled
    }

    /**
     * Derivation. Head (crown to chin) fills HEAD_HEIGHT_FRACTION of the square, so side = headH / fraction.
     * Crown sits at (1 - fraction) / 2 of the square from the top, which puts eyes near the middle (crown to chin,
     * eyes at about half the head height) and leaves the rest as shoulder room below the chin.
     * Clamping: if the ideal side exceeds the image's shorter side it can never fit, so shrink to that side first;
     * then shift the square inside the image. Shifting never needs a further shrink.
     */
    internal fun squareCropFor(
        faceLeft: Int,
        faceTop: Int,
        faceRight: Int,
        faceBottom: Int,
        imageW: Int,
        imageH: Int,
    ): SquareCrop {
        require(imageW > 0 && imageH > 0) { "image must be non-empty, was ${imageW}x$imageH" }

        val faceHeight = max(faceBottom - faceTop, 1).toFloat()
        val crownY = faceTop - CROWN_EXTENSION * faceHeight
        val headHeight = faceHeight * (1f + CROWN_EXTENSION)
        val idealSide = (headHeight / Config.HEAD_HEIGHT_FRACTION).roundToInt()
        val side = idealSide.coerceIn(1, min(imageW, imageH))

        val idealTop = crownY - (1f - Config.HEAD_HEIGHT_FRACTION) / 2f * side
        val centreX = (faceLeft + faceRight) / 2f
        val left = (centreX - side / 2f).roundToInt().coerceIn(0, imageW - side)
        val top = idealTop.roundToInt().coerceIn(0, imageH - side)
        return SquareCrop(left, top, side)
    }
}
