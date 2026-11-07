package dev.raja.passportcam.pipeline

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import dev.raja.passportcam.core.Config
import dev.raja.passportcam.core.PLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Orchestrator (driver-owned). Wires ImageLoader -> FaceDetector -> PassportCropper -> Compressor. */
object PassportProcessor {
    private const val AREA = "processor"

    /** Full pipeline for one image Uri (camera capture or picked file). Suspends; heavy work runs on Dispatchers.Default. */
    suspend fun process(context: Context, uri: Uri): ProcessOutcome = withContext(Dispatchers.Default) {
        val t0 = SystemClock.elapsedRealtime()
        PLog.i(AREA, "start uri=$uri")
        try {
            val source = ImageLoader.loadUpright(context, uri)
            try {
                val faces = FaceDetector.detect(source)
                val usable = faces.filter { minOf(it.bounds.width(), it.bounds.height()) >= Config.MIN_FACE_PX }
                val skipped = faces.size - usable.size
                PLog.i(AREA, "faces total=${faces.size} usable=${usable.size} skippedSmall=$skipped")
                if (usable.isEmpty()) return@withContext ProcessOutcome.NoFace(skipped)

                val photos = ArrayList<PassportPhoto>(usable.size)
                usable.forEachIndexed { i, face ->
                    val square = PassportCropper.crop(source, face, Config.OUTPUT_SIDES_PX[0])
                    try {
                        val photo = Compressor.compressUnderLimit(square)
                        if (photo == null) {
                            PLog.w(AREA, "face #${i + 1}: could not get under ${Config.MAX_BYTES} bytes, dropped")
                        } else {
                            PLog.i(AREA, "face #${i + 1}: ${photo.sizeBytes} bytes side=${photo.sidePx} q=${photo.quality}")
                            photos += photo
                        }
                    } finally {
                        square.recycle()
                    }
                }
                if (photos.isEmpty()) {
                    ProcessOutcome.Failure("Could not make the photo small enough.")
                } else {
                    ProcessOutcome.Success(photos, skipped)
                }
            } finally {
                source.recycle()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            PLog.e(AREA, "pipeline failed", e)
            ProcessOutcome.Failure("Something went wrong while making the photo.")
        } finally {
            PLog.i(AREA, "done in ${SystemClock.elapsedRealtime() - t0} ms")
        }
    }
}
