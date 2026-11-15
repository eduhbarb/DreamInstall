package dev.oni.dreaminstaller.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.oni.dreaminstaller.core.decodeSmdhIcon
import dev.oni.dreaminstaller.ui.theme.LocalPalette

@Composable
fun DreamCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalPalette.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(palette.card),
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(palette.border)),
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun SectionTitle(text: String, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color(LocalPalette.current.dim),
        )
        trailing?.invoke()
    }
}

enum class ChipTone { Ok, Warn, Bad, Dim, Accent }

@Composable
fun Chip(text: String, tone: ChipTone = ChipTone.Dim) {
    val palette = LocalPalette.current
    val color = when (tone) {
        ChipTone.Ok -> Color(0xFF54D08A)
        ChipTone.Warn -> Color(0xFFE7B45C)
        ChipTone.Bad -> Color(0xFFE44C3E)
        ChipTone.Dim -> Color(palette.dim)
        ChipTone.Accent -> Color(palette.highlight)
    }
    val background = when (tone) {
        ChipTone.Ok -> Color(0x1F54D08A)
        ChipTone.Warn -> Color(0x1FE7B45C)
        ChipTone.Bad -> Color(0x1FE44C3E)
        ChipTone.Dim -> Color(0x141E1530)
        ChipTone.Accent -> Color(0x1FCBB0F5)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .border(1.dp, color.copy(alpha = 0.45f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun Crescent(size: Dp = 34.dp) {
    val palette = LocalPalette.current
    Canvas(Modifier.size(size)) {
        drawCircle(Color(palette.accent), radius = this.size.minDimension * 0.46f)
        drawCircle(
            Color(palette.bg),
            radius = this.size.minDimension * 0.38f,
            center = Offset(this.size.width * 0.66f, this.size.height * 0.34f),
        )
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, color: Color? = null) {
    val palette = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(Color(palette.track)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(CircleShape)
                .background(color ?: Color(palette.accent)),
        )
    }
}

@Composable
fun Thumb(icon: ByteArray?, size: Dp = 46.dp) {
    val palette = LocalPalette.current
    val bitmap: ImageBitmap? = remember(icon) { icon?.let { rgb565(it) } }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(palette.track))
            .border(1.dp, Color(palette.border), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(12.dp)))
        } else {
            Text("3DS", style = MaterialTheme.typography.labelSmall, color = Color(palette.dim))
        }
    }
}

fun rgb565(data: ByteArray): ImageBitmap? {
    val pixels = decodeSmdhIcon(data, 48, 48) ?: return null
    return Bitmap.createBitmap(pixels, 48, 48, Bitmap.Config.ARGB_8888).asImageBitmap()
}

@Composable
fun LineNote(text: String, tone: ChipTone = ChipTone.Dim, icon: ImageVector? = null) {
    val palette = LocalPalette.current
    val edge = when (tone) {
        ChipTone.Ok -> Color(0xFF54D08A)
        ChipTone.Warn -> Color(0xFFE7B45C)
        ChipTone.Bad -> Color(0xFFE44C3E)
        ChipTone.Dim -> Color(palette.border)
        ChipTone.Accent -> Color(palette.accent)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(palette.blob))
            .border(1.dp, Color(palette.border), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 2.dp).size(3.dp, 16.dp).clip(CircleShape).background(edge))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = Color(palette.soft))
        }
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = edge, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun SingleLine(text: String, color: Color = Color(LocalPalette.current.dim)) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
