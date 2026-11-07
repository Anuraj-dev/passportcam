package dev.raja.passportcam.pipeline

import android.graphics.Bitmap

/** MODULE C. On-device face detection (ML Kit). */
object FaceDetector {
    /** Detect faces in an upright [bitmap]. Order: left-to-right by bounds.left. Empty list if none. Never throws for "no face". */
    suspend fun detect(bitmap: Bitmap): List<DetectedFace> = TODO("module C")
}
