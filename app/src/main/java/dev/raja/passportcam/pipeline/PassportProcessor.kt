package dev.raja.passportcam.pipeline

import android.content.Context
import android.net.Uri

/** Orchestrator (driver-owned). Wires ImageLoader -> FaceDetector -> PassportCropper -> Compressor. */
object PassportProcessor {
    /** Full pipeline for one image Uri (camera capture or picked file). Blocking-free: suspends, runs heavy work on Dispatchers.Default. */
    suspend fun process(context: Context, uri: Uri): ProcessOutcome = TODO("driver")
}
