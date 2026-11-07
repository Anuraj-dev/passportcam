package dev.raja.passportcam.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.raja.passportcam.R
import dev.raja.passportcam.core.PLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private const val CAM = "camera"

/** Camera route: permission gate, then the viewfinder. [onCaptured] receives a JPEG in cacheDir the caller must delete. */
@Composable
fun CameraScreen(onBack: () -> Unit, onCaptured: (File) -> Unit, onChoosePhoto: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(hasPermission()) }
    var blocked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        // After a denial, no rationale means Android will not show the dialog again: send them to Settings.
        blocked = !ok && activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false
        PLog.i(CAM, "permission result granted=$ok blocked=$blocked")
    }
    // Re-check when returning from Settings.
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                val now = hasPermission()
                if (now != granted) PLog.i(CAM, "permission changed on resume granted=$now")
                granted = now
                if (now) blocked = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) { PLog.i(CAM, "camera screen; permission granted=$granted") }

    when {
        granted -> Viewfinder(onBack, onCaptured, onChoosePhoto)
        blocked -> MessageScreen(
            icon = rememberVectorPainter(Icons.Filled.Settings),
            iconTint = MaterialTheme.colorScheme.primary,
            title = stringResource(R.string.camera_blocked_title),
            body = stringResource(R.string.camera_blocked_body),
            primaryText = stringResource(R.string.open_settings),
            primaryIcon = rememberVectorPainter(Icons.Filled.Settings),
            onPrimary = {
                PLog.i(CAM, "open settings tapped")
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            onBack = onBack,
            secondaryText = stringResource(R.string.choose_photo),
            onSecondary = onChoosePhoto,
        )
        else -> MessageScreen(
            icon = painterResource(R.drawable.ic_camera),
            iconTint = MaterialTheme.colorScheme.primary,
            title = stringResource(R.string.camera_ask_title),
            body = stringResource(R.string.camera_ask_body),
            primaryText = stringResource(R.string.camera_allow),
            primaryIcon = painterResource(R.drawable.ic_camera),
            onPrimary = {
                PLog.i(CAM, "requesting permission")
                launcher.launch(Manifest.permission.CAMERA)
            },
            onBack = onBack,
        )
    }
}

@Composable
private fun Viewfinder(onBack: () -> Unit, onCaptured: (File) -> Unit, onChoosePhoto: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            // Latency over quality: output is a 600px crop, and these users may move right after tapping.
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }
    var useFront by rememberSaveable { mutableStateOf(false) }
    var canFlip by remember { mutableStateOf(false) }
    var noCamera by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    var focusTick by remember { mutableIntStateOf(0) }
    val flash = remember { Animatable(0f) }

    // Bind to the screen's lifecycle; unbind when leaving so the camera is released immediately.
    DisposableEffect(controller, lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        controller.initializationFuture.addListener({
            try {
                val back = controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                val front = controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                PLog.i(CAM, "cameras back=$back front=$front")
                canFlip = back && front
                if (!back && front) useFront = true
                noCamera = !back && !front
                ready = true
            } catch (t: Throwable) {
                PLog.e(CAM, "camera init failed", t)
                noCamera = true
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            PLog.i(CAM, "unbinding camera")
            controller.unbind()
        }
    }
    LaunchedEffect(useFront, ready) {
        if (!ready) return@LaunchedEffect
        val sel = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        if (controller.cameraSelector != sel) {
            controller.cameraSelector = sel
            PLog.i(CAM, "using ${if (useFront) "front" else "back"} camera")
        }
    }
    LaunchedEffect(error) {
        if (error) { delay(3500); error = false }
    }

    if (noCamera) {
        MessageScreen(
            icon = rememberVectorPainter(Icons.Filled.Warning),
            iconTint = MaterialTheme.colorScheme.primary,
            title = stringResource(R.string.no_camera_title),
            body = stringResource(R.string.no_camera_body),
            primaryText = stringResource(R.string.choose_photo),
            primaryIcon = painterResource(R.drawable.ic_gallery),
            onPrimary = onChoosePhoto,
            onBack = onBack,
        )
        return
    }

    fun capture() {
        if (busy) return
        busy = true
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        scope.launch { flash.snapTo(0.85f); flash.animateTo(0f, tween(350)) }
        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val started = System.currentTimeMillis()
        PLog.i(CAM, "capture start front=$useFront -> ${file.name}")
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    PLog.i(CAM, "capture done ${file.length()} B in ${System.currentTimeMillis() - started} ms")
                    busy = false
                    onCaptured(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    PLog.e(CAM, "capture failed code=${exception.imageCaptureError}", exception)
                    file.delete()
                    busy = false
                    error = true
                }
            },
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    // TextureView so the preview fades with the screen transitions.
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                    // Observe taps for the focus ring; returning false lets PreviewView do the actual tap-to-focus.
                    setOnTouchListener { v, e ->
                        if (e.action == MotionEvent.ACTION_UP) {
                            focusAt = Offset(e.x, e.y)
                            focusTick++
                            v.performClick()
                        }
                        false
                    }
                }
            },
        )

        focusAt?.let { FocusRing(it, focusTick) }

        // Shutter flash.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = flash.value }.background(Color.White))

        // Top: back + short hint.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack, onDark = true)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.camera_hint),
                color = Color.White, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(64.dp))
        }

        AnimatedVisibility(
            visible = error, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center).padding(24.dp),
        ) {
            Text(
                stringResource(R.string.capture_failed),
                color = Color.White, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.7f), MaterialTheme.shapes.medium).padding(20.dp),
            )
        }

        // Bottom: [spacer] [shutter] [flip], like the phone's own camera.
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(72.dp))
            Shutter(enabled = ready && !busy, busy = busy, onClick = ::capture)
            if (canFlip) {
                IconButton(
                    onClick = {
                        useFront = !useFront
                        PLog.i(CAM, "flip tapped -> front=$useFront")
                    },
                    enabled = !busy,
                    modifier = Modifier.size(72.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape),
                ) {
                    Icon(
                        Icons.Filled.Refresh, contentDescription = stringResource(R.string.flip_camera),
                        tint = Color.White, modifier = Modifier.size(36.dp),
                    )
                }
            } else {
                Spacer(Modifier.size(72.dp))
            }
        }
    }
}

@Composable
private fun Shutter(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val desc = stringResource(R.string.shutter)
    Box(
        Modifier.size(92.dp).pressScale(source).border(5.dp, Color.White, CircleShape).padding(9.dp)
            .background(if (enabled || busy) Color.White else Color.White.copy(alpha = 0.5f), CircleShape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(36.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 4.dp)
    }
}

/** A white ring that pops in where the user tapped, then fades. [tick] restarts it on every tap. */
@Composable
private fun FocusRing(at: Offset, tick: Int) {
    val anim = remember(tick) { Animatable(0f) }
    LaunchedEffect(tick) {
        anim.animateTo(1f, tween(250))
        delay(700)
        anim.animateTo(2f, tween(300))
    }
    val v = anim.value
    val scale by animateFloatAsState(if (v < 1f) 1.3f - 0.3f * v else 1f, label = "ringScale")
    val alpha = if (v <= 1f) v else 2f - v
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            color = Color.White.copy(alpha = alpha.coerceIn(0f, 1f)),
            radius = 40.dp.toPx() * scale,
            center = at,
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}
