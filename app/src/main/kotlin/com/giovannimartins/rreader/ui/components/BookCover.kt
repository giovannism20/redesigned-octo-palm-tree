package com.giovannimartins.rreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.ui.theme.CoverColors
import com.giovannimartins.rreader.ui.theme.OnCover
import java.io.File

private const val COVER_ASPECT_RATIO = 2f / 3f

/** A book cover: the extracted image when there is one, otherwise a generated card showing the title. */
@Composable
fun BookCover(
    title: String,
    coverFile: File?,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.cover_description, title)
    Box(modifier = modifier.aspectRatio(COVER_ASPECT_RATIO).clip(RoundedCornerShape(8.dp))) {
        if (coverFile != null) {
            AsyncImage(
                model = coverFile,
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val color = CoverColors[Math.floorMod(title.hashCode(), CoverColors.size)]
            Box(
                modifier = Modifier.fillMaxSize().background(color).padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = OnCover,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
