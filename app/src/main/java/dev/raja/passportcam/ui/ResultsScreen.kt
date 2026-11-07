package dev.raja.passportcam.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.raja.passportcam.R
import dev.raja.passportcam.core.PLog
import dev.raja.passportcam.ui.theme.Danger
import dev.raja.passportcam.ui.theme.PassportCamTheme

@Composable
fun ResultsScreen(
    photos: List<SavedPhoto>,
    skippedSmallFaces: Int,
    onShare: (SavedPhoto) -> Unit,
    onDelete: (SavedPhoto) -> Unit,
    onDone: () -> Unit,
    onTakeAnother: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<SavedPhoto?>(null) }
    val columns = if (photos.size <= 1) 1 else 2

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                ResultsHeader(skippedSmallFaces, empty = photos.isEmpty())
            }
            itemsIndexed(photos, key = { _, p -> p.uri.toString() }) { i, p ->
                PhotoCard(
                    photo = p, number = i + 1, compact = columns > 1,
                    onShare = { onShare(p) },
                    onDelete = { PLog.i("ui", "delete tapped ${p.uri}"); pendingDelete = p },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            BigButton(stringResource(R.string.done), rememberVectorPainter(Icons.Filled.Check), onDone)
            Spacer(Modifier.height(12.dp))
            BigButton(
                stringResource(R.string.take_another), painterResource(R.drawable.ic_camera), onTakeAnother,
                primary = false, height = 72.dp,
            )
        }
    }

    pendingDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Danger, modifier = Modifier.size(40.dp)) },
            title = { Text(stringResource(R.string.delete_title), style = MaterialTheme.typography.headlineSmall) },
            text = { Text(stringResource(R.string.delete_body), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; onDelete(p) }, modifier = Modifier.height(64.dp)) {
                    Text(stringResource(R.string.delete), style = MaterialTheme.typography.labelLarge, color = Danger)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { PLog.i("ui", "delete kept"); pendingDelete = null },
                    modifier = Modifier.height(64.dp),
                ) { Text(stringResource(R.string.keep), style = MaterialTheme.typography.labelLarge) }
            },
        )
    }
}

@Composable
private fun ResultsHeader(skippedSmallFaces: Int, empty: Boolean) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.results_title), style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(if (empty) R.string.results_empty else R.string.results_saved),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (skippedSmallFaces > 0) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    pluralStringResource(R.plurals.results_skipped, skippedSmallFaces, skippedSmallFaces),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun PhotoCard(
    photo: SavedPhoto,
    number: Int,
    compact: Boolean,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            val imgMod = Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium)
            val desc = stringResource(R.string.photo_description, number)
            if (photo.image != null) {
                Image(photo.image, contentDescription = desc, modifier = imgMod, contentScale = ContentScale.Crop)
            } else {
                Box(imgMod.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.art_passport), contentDescription = desc, modifier = Modifier.size(66.dp, 72.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.size_kb, photo.sizeKb),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            val share = @Composable { m: Modifier ->
                BigButton(
                    stringResource(R.string.share), rememberVectorPainter(Icons.Filled.Share), onShare,
                    modifier = m, height = 64.dp, compact = compact,
                )
            }
            val delete = @Composable { m: Modifier ->
                BigButton(
                    stringResource(R.string.delete), rememberVectorPainter(Icons.Filled.Delete), onDelete,
                    modifier = m, primary = false, height = 64.dp, contentColor = Danger, compact = compact,
                )
            }
            if (compact) {
                share(Modifier)
                Spacer(Modifier.height(8.dp))
                delete(Modifier)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    share(Modifier.weight(1f))
                    delete(Modifier.weight(1f))
                }
            }
        }
    }
}

/** System share sheet for one saved MediaStore photo. */
fun sharePhoto(context: Context, photo: SavedPhoto) {
    PLog.i("ui", "share tapped ${photo.uri}")
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, photo.uri)
        clipData = ClipData.newRawUri(null, photo.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, context.getString(R.string.share_chooser))
    try {
        context.startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        PLog.e("ui", "no share target", e)
        Toast.makeText(context, R.string.share_failed, Toast.LENGTH_LONG).show()
    }
}

private fun fakePhotos(n: Int) = List(n) { SavedPhoto(Uri.parse("content://preview/$it"), null, 82 - it * 7) }

@Preview(showBackground = true, widthDp = 380, heightDp = 860, name = "Results - one")
@Composable
private fun ResultsOnePreview() {
    PassportCamTheme { ResultsScreen(fakePhotos(1), 0, {}, {}, {}, {}) }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 860, name = "Results - group")
@Composable
private fun ResultsGroupPreview() {
    PassportCamTheme { ResultsScreen(fakePhotos(3), 2, {}, {}, {}, {}) }
}
