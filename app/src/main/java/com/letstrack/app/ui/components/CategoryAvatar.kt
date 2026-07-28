package com.letstrack.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.letstrack.app.domain.model.Category
import com.letstrack.app.ui.theme.ShapeFull
import com.letstrack.app.ui.theme.categoricalAccent
import java.io.File

/**
 * Circular category avatar: renders a custom uploaded image if the category has one, else the
 * stored emoji, else falls back to a first-letter monogram. Always sits on the category's
 * palette-snapped accent color so it never depends on emoji rendering to communicate the
 * category. `Category.icon` is repurposed to hold a `file://` path when an image was uploaded
 * (see CategoryManagementViewModel), so the emoji fallback explicitly excludes that case --
 * otherwise a raw file path would render as text.
 */
@Composable
fun CategoryAvatar(
    category: Category?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val accent = category?.let { categoricalAccent(it.color) } ?: MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .size(size)
            .clip(ShapeFull)
            .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        val imageUri = category?.iconUri
        if (!imageUri.isNullOrBlank()) {
            Image(
                painter = rememberAsyncImagePainter(File(imageUri.removePrefix("file://"))),
                contentDescription = category?.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            val label = category?.icon?.takeIf { it.isNotBlank() && !it.startsWith("file://") }
                ?: category?.name?.firstOrNull()?.uppercase()
                ?: "?"
            Text(
                text = label,
                fontSize = (size.value * 0.46f).sp,
                color = accent
            )
        }
    }
}

/** A small solid accent dot, used inline next to category names in lists/legends. */
@Composable
fun AccentDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(ShapeFull)
            .background(color)
    )
}
