package dev.raja.passportcam.pipeline

import android.graphics.Bitmap
import android.graphics.Rect

/** MODULE C. Passport-style square crop around a face. */
object PassportCropper {
    /**
     * Pure maths (unit-testable, no Android Bitmap needed): the square region of the source to crop for [face].
     * Rules: square; head height ~Config.HEAD_HEIGHT_FRACTION of the square; eyes ~45-55% from top (shift crop so face sits
     * slightly above centre, room for shoulders below); clamp to [imageW]x[imageH] by shifting first, shrinking second.
     * The returned rect lies fully inside the image. Note ML Kit face bounds cover roughly brow-to-chin, not crown: allow for hair/crown above.
     */
    fun computeCropRect(face: Rect, imageW: Int, imageH: Int): Rect = TODO("module C")

    /** Crop [source] with [computeCropRect] and scale to [sidePx] x [sidePx]. */
    fun crop(source: Bitmap, face: DetectedFace, sidePx: Int): Bitmap = TODO("module C")
}
