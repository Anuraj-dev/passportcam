package dev.raja.passportcam.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.raja.passportcam.R

/** Shrinks slightly while pressed so every tap feels acknowledged. */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(stiffness = 600f), label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** The app's main button: >= 72dp tall, 24sp label, icon so meaning survives without reading. */
@Composable
fun BigButton(
    text: String,
    icon: Painter?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    height: Dp = 80.dp,
    contentColor: Color? = null,
) {
    val source = remember { MutableInteractionSource() }
    val m = modifier.fillMaxWidth().heightIn(min = height).pressScale(source)
    val pad = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    val content: @Composable () -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
    if (primary) {
        Button(
            onClick = onClick, modifier = m, shape = MaterialTheme.shapes.large,
            interactionSource = source, contentPadding = pad,
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick, modifier = m, shape = MaterialTheme.shapes.large,
            interactionSource = source, contentPadding = pad,
            border = BorderStroke(2.dp, contentColor ?: MaterialTheme.colorScheme.primary),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = contentColor ?: MaterialTheme.colorScheme.primary,
            ),
        ) { content() }
    }
}

@Composable
fun BigButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true) =
    BigButton(text, rememberVectorPainter(icon), onClick, modifier, primary)

/** Round back arrow, 64dp touch target. [onDark] for the camera screen. */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier, onDark: Boolean = false) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(64.dp)
            .background(if (onDark) Color.Black.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.back),
            tint = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(32.dp),
        )
    }
}

/**
 * Shared layout for simple one-message screens (errors, permission): back button, big icon,
 * title, a short line of help, then the primary action at the bottom within thumb reach.
 */
@Composable
fun MessageScreen(
    icon: Painter,
    iconTint: Color,
    title: String,
    body: String,
    primaryText: String,
    primaryIcon: Painter?,
    onPrimary: () -> Unit,
    onBack: () -> Unit,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(24.dp),
    ) {
        BackButton(onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(128.dp).background(iconTint.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(64.dp)) }
            Spacer(Modifier.size(32.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.size(16.dp))
            Text(
                body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BigButton(primaryText, primaryIcon, onPrimary)
        if (secondaryText != null && onSecondary != null) {
            Spacer(Modifier.size(16.dp))
            BigButton(secondaryText, null, onSecondary, primary = false, height = 72.dp)
        }
    }
}
