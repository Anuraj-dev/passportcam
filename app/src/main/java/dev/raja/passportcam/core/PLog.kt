package dev.raja.passportcam.core

import android.util.Log

/**
 * Single logging entry point. Filter on device with:  adb logcat -s PassportCam
 * Tag is constant so one filter shows the whole flow; the `area` prefix says which module logged.
 */
object PLog {
    const val TAG = "PassportCam"
    fun d(area: String, msg: String) { Log.d(TAG, "[$area] $msg") }
    fun i(area: String, msg: String) { Log.i(TAG, "[$area] $msg") }
    fun w(area: String, msg: String, t: Throwable? = null) { Log.w(TAG, "[$area] $msg", t) }
    fun e(area: String, msg: String, t: Throwable? = null) { Log.e(TAG, "[$area] $msg", t) }
}
