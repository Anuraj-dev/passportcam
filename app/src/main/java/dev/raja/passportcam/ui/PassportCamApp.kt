package dev.raja.passportcam.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.raja.passportcam.R
import dev.raja.passportcam.core.PLog
import dev.raja.passportcam.ui.theme.PassportCamTheme

/** Rough left-to-right order of the flow; decides whether a transition slides forward or back. */
private fun UiState.depth(): Int = when (this) {
    UiState.Home -> 0
    UiState.Camera -> 1
    UiState.Processing -> 2
    is UiState.Results, is UiState.NoFace, is UiState.Failed -> 3
}

@Composable
fun PassportCamApp(vm: AppViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> vm.onPicked(uri) }
    val choosePhoto = {
        PLog.i("ui", "choose photo tapped")
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val retry = {
        PLog.i("ui", "try again tapped (source=${vm.lastSource})")
        if (vm.lastSource == Source.Camera) vm.openCamera() else { vm.goHome(); choosePhoto() }
    }

    // Light status-bar icons over the black camera preview, dark icons elsewhere.
    val view = LocalView.current
    if (!view.isInEditMode) {
        val dark = state is UiState.Camera
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    // System back: Home exits the app (default); every other screen steps back toward Home.
    BackHandler(enabled = state !is UiState.Home) {
        PLog.i("ui", "system back on ${state::class.simpleName}")
        if (state is UiState.Processing) vm.cancelProcessing() else vm.goHome()
    }

    PassportCamTheme {
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            transitionSpec = {
                val forward = targetState.depth() >= initialState.depth()
                val dir = if (forward) 1 else -1
                (fadeIn(tween(320, delayMillis = 60)) + slideInHorizontally(tween(360)) { dir * it / 6 }) togetherWith
                    (fadeOut(tween(200)) + slideOutHorizontally(tween(360)) { -dir * it / 8 })
            },
            label = "screen",
        ) { s ->
            when (s) {
                UiState.Home -> HomeScreen(onTakePhoto = { PLog.i("ui", "take photo tapped"); vm.openCamera() }, onChoosePhoto = choosePhoto)
                UiState.Camera -> CameraScreen(
                    onBack = vm::goHome,
                    onCaptured = vm::onCaptured,
                    onChoosePhoto = { vm.goHome(); choosePhoto() },
                )
                UiState.Processing -> ProcessingScreen(onCancel = vm::cancelProcessing)
                is UiState.Results -> ResultsScreen(
                    photos = s.photos,
                    skippedSmallFaces = s.skippedSmallFaces,
                    onShare = { sharePhoto(context, it) },
                    onDelete = vm::delete,
                    onDone = { PLog.i("ui", "done tapped"); vm.goHome() },
                    onTakeAnother = { PLog.i("ui", "take another tapped"); vm.openCamera() },
                )
                is UiState.NoFace -> MessageScreen(
                    icon = rememberVectorPainter(Icons.Filled.Face),
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.noface_title),
                    body = stringResource(if (s.skippedSmallFaces > 0) R.string.noface_small_body else R.string.noface_body),
                    primaryText = stringResource(R.string.try_again),
                    primaryIcon = rememberVectorPainter(Icons.Filled.Refresh),
                    onPrimary = retry,
                    onBack = vm::goHome,
                    secondaryText = stringResource(R.string.back_to_start),
                    onSecondary = vm::goHome,
                )
                is UiState.Failed -> MessageScreen(
                    icon = rememberVectorPainter(Icons.Filled.Warning),
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.failed_title),
                    body = stringResource(if (s.kind == FailKind.Saving) R.string.save_failed_body else R.string.failed_body),
                    primaryText = stringResource(R.string.try_again),
                    primaryIcon = rememberVectorPainter(Icons.Filled.Refresh),
                    onPrimary = retry,
                    onBack = vm::goHome,
                    secondaryText = stringResource(R.string.back_to_start),
                    onSecondary = vm::goHome,
                )
            }
        }
    }
}
