package dev.raja.passportcam.pipeline

import android.graphics.Rect

/** A face found in an upright (EXIF-corrected) bitmap. [bounds] are in that bitmap's pixel coordinates. */
data class DetectedFace(val bounds: Rect)

/** One finished passport photo: JPEG bytes already <= Config.MAX_BYTES. */
class PassportPhoto(
    val jpeg: ByteArray,
    val sidePx: Int,
    val quality: Int,
) {
    val sizeBytes: Int get() = jpeg.size
    val sizeKb: Int get() = (jpeg.size + 999) / 1000
}

sealed interface ProcessOutcome {
    /** [skippedSmallFaces] = faces found but too small to give a good photo. */
    data class Success(val photos: List<PassportPhoto>, val skippedSmallFaces: Int) : ProcessOutcome
    /** No usable face (none found, or all too small). */
    data class NoFace(val skippedSmallFaces: Int) : ProcessOutcome
    data class Failure(val message: String) : ProcessOutcome
}
