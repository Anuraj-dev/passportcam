package dev.raja.passportcam.ui

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.raja.passportcam.R
import dev.raja.passportcam.core.PLog
import dev.raja.passportcam.pipeline.PassportProcessor
import dev.raja.passportcam.pipeline.ProcessOutcome
import dev.raja.passportcam.storage.PhotoSaver
import dev.raja.passportcam.storage.passportFileName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val AREA = "ui"

/** One passport photo already saved to MediaStore. [image] is null only in previews. */
data class SavedPhoto(val uri: Uri, val image: ImageBitmap?, val sizeKb: Int)

enum class Source { Camera, Picker }

enum class FailKind { Processing, Saving }

/** The whole app is one screen at a time; the screen is this state. */
sealed interface UiState {
    data object Home : UiState
    data object Camera : UiState
    data object Processing : UiState
    data class Results(val photos: List<SavedPhoto>, val skippedSmallFaces: Int) : UiState
    data class NoFace(val skippedSmallFaces: Int) : UiState
    data class Failed(val kind: FailKind) : UiState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<UiState>(UiState.Home)
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Where the last image came from, so "Try again" / "Take another" repeat the same path. */
    var lastSource: Source = Source.Camera
        private set

    private var job: Job? = null

    private fun go(next: UiState) {
        val prev = _state.value
        _state.value = next
        if (prev::class != next::class) PLog.i(AREA, "screen ${prev.name()} -> ${next.name()}")
    }

    fun goHome() = go(UiState.Home)

    fun openCamera() {
        lastSource = Source.Camera
        go(UiState.Camera)
    }

    fun onPicked(uri: Uri?) {
        lastSource = Source.Picker
        if (uri == null) {
            PLog.i(AREA, "picker: cancelled, nothing chosen")
            return
        }
        PLog.i(AREA, "picker: got $uri")
        process(uri, Source.Picker, tempFile = null)
    }

    fun onCaptured(file: File) {
        process(Uri.fromFile(file), Source.Camera, tempFile = file)
    }

    fun cancelProcessing() {
        PLog.i(AREA, "processing: cancel tapped")
        job?.cancel()
        job = null
        go(UiState.Home)
    }

    private fun process(uri: Uri, source: Source, tempFile: File?) {
        val app = getApplication<Application>()
        lastSource = source
        job?.cancel()
        go(UiState.Processing)
        job = viewModelScope.launch {
            val started = System.currentTimeMillis()
            try {
                val outcome = PassportProcessor.process(app, uri)
                ensureActive()
                PLog.i(AREA, "outcome ${outcome::class.simpleName} in ${System.currentTimeMillis() - started} ms")
                when (outcome) {
                    is ProcessOutcome.Success -> saveAll(app, outcome)
                    is ProcessOutcome.NoFace -> {
                        PLog.i(AREA, "no face; skippedSmall=${outcome.skippedSmallFaces}")
                        go(UiState.NoFace(outcome.skippedSmallFaces))
                    }
                    is ProcessOutcome.Failure -> {
                        PLog.w(AREA, "pipeline failure: ${outcome.message}")
                        go(UiState.Failed(FailKind.Processing))
                    }
                }
            } catch (e: CancellationException) {
                PLog.i(AREA, "processing cancelled")
                throw e
            } catch (t: Throwable) {
                PLog.e(AREA, "processing crashed", t)
                go(UiState.Failed(FailKind.Processing))
            } finally {
                if (tempFile != null) withContext(NonCancellable + Dispatchers.IO) {
                    val ok = tempFile.delete()
                    PLog.d(AREA, "temp capture ${tempFile.name} deleted=$ok")
                }
            }
        }
    }

    private suspend fun saveAll(app: Application, outcome: ProcessOutcome.Success) {
        val now = System.currentTimeMillis()
        val saved = withContext(Dispatchers.IO) {
            outcome.photos.mapIndexedNotNull { i, photo ->
                try {
                    val name = passportFileName(i + 1, now)
                    val uri = PhotoSaver.save(app, photo.jpeg, name)
                    val image = BitmapFactory.decodeByteArray(photo.jpeg, 0, photo.jpeg.size)?.asImageBitmap()
                    PLog.i(AREA, "saved #${i + 1} $name ${photo.sizeBytes} B (${photo.sidePx}px q${photo.quality}) -> $uri")
                    SavedPhoto(uri, image, photo.sizeKb)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    PLog.e(AREA, "save #${i + 1} failed", t)
                    null
                }
            }
        }
        PLog.i(AREA, "saved ${saved.size}/${outcome.photos.size}; skippedSmall=${outcome.skippedSmallFaces}")
        go(if (saved.isEmpty()) UiState.Failed(FailKind.Saving) else UiState.Results(saved, outcome.skippedSmallFaces))
    }

    fun delete(photo: SavedPhoto) {
        val app = getApplication<Application>()
        PLog.i(AREA, "delete confirmed ${photo.uri}")
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    PhotoSaver.delete(app, photo.uri)
                } catch (t: Throwable) {
                    PLog.e(AREA, "delete threw", t)
                    false
                }
            }
            PLog.i(AREA, "delete ${photo.uri} ok=$ok")
            if (ok) {
                _state.update { s -> if (s is UiState.Results) s.copy(photos = s.photos - photo) else s }
            } else {
                Toast.makeText(app, R.string.delete_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCleared() {
        job?.cancel()
    }
}

private fun UiState.name(): String = this::class.simpleName ?: "?"
