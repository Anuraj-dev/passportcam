package dev.raja.passportcam.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.raja.passportcam.R
import dev.raja.passportcam.ui.theme.PassportCamTheme

@Composable
fun HomeScreen(onTakePhoto: () -> Unit, onChoosePhoto: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(176.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.art_passport), contentDescription = null, modifier = Modifier.size(88.dp, 96.dp))
            }
            Spacer(Modifier.height(32.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.home_helper),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        BigButton(stringResource(R.string.take_photo), painterResource(R.drawable.ic_camera), onTakePhoto, height = 88.dp)
        Spacer(Modifier.height(16.dp))
        BigButton(
            stringResource(R.string.choose_photo), painterResource(R.drawable.ic_gallery), onChoosePhoto,
            primary = false, height = 88.dp,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun HomePreview() {
    PassportCamTheme { HomeScreen({}, {}) }
}
