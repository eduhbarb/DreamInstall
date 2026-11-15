package dev.oni.dreaminstall.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import dev.oni.dreaminstall.core.decodeSmdhIcon
import dev.oni.dreaminstall.ui.theme.LocalPalette

/** A tonal Material 3 card: raised surface instead of the outlined card of 1.3. */
@Composable
fun DreamCard(
    modifier: Modifier = Modifier,
    padding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun SectionLabel(text: String, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        trailing?.invoke()
    }
}

enum class ChipTone { Ok, Warn, Bad, Dim, Accent }

@Composable
fun Chip(text: String, tone: ChipTone = ChipTone.Dim, check: Boolean = false) {
    val palette = LocalPalette.current
    val content = when (tone) {
        ChipTone.Ok -> Color(palette.ok)
        ChipTone.Warn -> Color(palette.warn)
        ChipTone.Bad -> Color(palette.bad)
        ChipTone.Dim -> MaterialTheme.colorScheme.onSurfaceVariant
        ChipTone.Accent -> Color(palette.highlight)
    }
    val container = when (tone) {
        ChipTone.Ok -> Color(palette.okBg)
        ChipTone.Warn -> Color(palette.warnBg)
        ChipTone.Bad -> Color(palette.badBg)
        ChipTone.Dim -> MaterialTheme.colorScheme.surfaceContainerHigh
        ChipTone.Accent -> MaterialTheme.colorScheme.primaryContainer
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(8.dp)) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (check) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(12.dp))
            }
            Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
fun Crescent(size: Dp = 28.dp) {
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
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
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
fun Thumb(icon: ByteArray?, size: Dp = 40.dp) {
    val bitmap: ImageBitmap? = remember(icon) { icon?.let { rgb565(it) } }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap,
                contentDescription = null,
                modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp)),
            )
        } else {
            Text(
                "3DS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        ChipTone.Ok -> Color(palette.ok)
        ChipTone.Warn -> Color(palette.warn)
        ChipTone.Bad -> Color(palette.bad)
        ChipTone.Dim -> MaterialTheme.colorScheme.outline
        ChipTone.Accent -> MaterialTheme.colorScheme.primary
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(3.dp, 16.dp).clip(CircleShape).background(edge))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = edge, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
fun SingleLine(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
