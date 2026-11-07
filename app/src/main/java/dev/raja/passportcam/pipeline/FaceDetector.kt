package dev.raja.passportcam.pipeline

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import dev.raja.passportcam.core.PLog
import kotlinx.coroutines.tasks.await

/** MODULE C. On-device face detection (ML Kit). */
object FaceDetector {
    private const val AREA = "detector"

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .build()

    /** Detect faces in an upright [bitmap]. Order: left-to-right by bounds.left. Empty list if none. Never throws for "no face". */
    suspend fun detect(bitmap: Bitmap): List<DetectedFace> {
        val t0 = System.nanoTime()
        // One client per call: each image is processed once, and the client holds native model resources.
        val detector = FaceDetection.getClient(options)
        try {
            val faces = detector.process(InputImage.fromBitmap(bitmap, 0)).await()
            val result = faces
                .map { DetectedFace(it.boundingBox) }
                .sortedBy { it.bounds.left }
            PLog.i(AREA, "faces=${result.size} from ${bitmap.width}x${bitmap.height} in ${(System.nanoTime() - t0) / 1_000_000}ms")
            result.forEachIndexed { i, face ->
                val b = face.bounds
                PLog.d(AREA, "face[$i] bounds=(${b.left},${b.top})-(${b.right},${b.bottom}) ${b.width()}x${b.height()}")
            }
            return result
        } catch (t: Throwable) {
            PLog.e(AREA, "face detection failed", t)
            throw t
        } finally {
            detector.close()
        }
    }
}
