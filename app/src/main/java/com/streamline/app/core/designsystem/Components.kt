package com.streamline.app.core.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun StreamlineWordmark(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(if (compact) 7.dp else 9.dp)
                .clip(CircleShape)
                .background(StreamlineColors.Gold),
        )
        Spacer(Modifier.width(if (compact) 7.dp else 10.dp))
        Text(
            text = buildAnnotatedString {
                append("STREAM")
                withStyle(SpanStyle(color = StreamlineColors.Gold)) { append("LINE") }
            },
            color = StreamlineColors.TextPrimary,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = if (compact) 22.sp else 28.sp,
                letterSpacing = 2.sp,
            ),
        )
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = StreamlineColors.TextPrimary,
    size: Dp = 42.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = when {
            pressed -> 0.9f
            focused -> 1.08f
            else -> 1f
        },
        label = "glass-icon-scale",
    )
    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(
                if (focused) StreamlineColors.Gold.copy(alpha = 0.16f)
                else Color.White.copy(alpha = 0.065f),
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) StreamlineColors.Gold else Color.White.copy(alpha = 0.1f),
                CircleShape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 50.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) {
                    Modifier.border(2.dp, StreamlineColors.TextPrimary, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                },
            ),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = StreamlineColors.Gold,
            contentColor = StreamlineColors.Ink,
            disabledContainerColor = StreamlineColors.Gold.copy(alpha = 0.25f),
            disabledContentColor = StreamlineColors.Ink.copy(alpha = 0.7f),
        ),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    var focused by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 50.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) {
                    Modifier.border(2.dp, StreamlineColors.Gold, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = StreamlineColors.TextPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingDot: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(50),
        color = if (selected) StreamlineColors.Gold else Color.White.copy(alpha = 0.055f),
        contentColor = if (selected) StreamlineColors.Ink else StreamlineColors.TextSecondary,
        border = BorderStroke(
            width = if (focused) 2.dp else 1.dp,
            color = when {
                focused -> StreamlineColors.Gold
                selected -> Color.Transparent
                else -> Color.White.copy(alpha = 0.09f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingDot) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (selected) StreamlineColors.Ink else StreamlineColors.Gold),
                )
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun FilterPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    active: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val contentColor = if (active || focused) StreamlineColors.Gold else StreamlineColors.TextSecondary
    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (focused) StreamlineColors.Gold.copy(alpha = 0.12f)
                else Color.White.copy(alpha = 0.035f),
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (active || focused) StreamlineColors.Gold.copy(alpha = 0.45f)
                else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text = label, color = contentColor, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun RatingBadge(
    rating: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.62f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
            .padding(horizontal = if (compact) 7.dp else 9.dp, vertical = if (compact) 4.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Star,
            contentDescription = null,
            tint = StreamlineColors.Gold,
            modifier = Modifier.size(if (compact) 11.dp else 13.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = rating,
            color = StreamlineColors.TextPrimary,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = if (compact) 10.sp else 11.sp,
                letterSpacing = 0.3.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
fun MetaPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: Color = StreamlineColors.TextSecondary,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text.uppercase(Locale.US),
            color = tone,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
        )
    }
}

@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(
                    text = eyebrow.uppercase(Locale.US),
                    color = StreamlineColors.Gold,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = title,
                color = StreamlineColors.TextPrimary,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                color = StreamlineColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun Artwork(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    fallbackSeed: String = contentDescription.orEmpty(),
) {
    val context = LocalContext.current
    Box(
        modifier = modifier.clip(shape),
    ) {
        if (url.isNullOrBlank()) {
            PosterFallback(seed = fallbackSeed, modifier = Modifier.fillMaxSize())
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    ShimmerBlock(modifier = Modifier.fillMaxSize(), shape = shape)
                },
                error = {
                    PosterFallback(seed = fallbackSeed, modifier = Modifier.fillMaxSize())
                },
            )
        }
    }
}

@Composable
fun PosterFallback(
    seed: String,
    modifier: Modifier = Modifier,
) {
    val palettes = listOf(
        listOf(Color(0xFF273246), Color(0xFF141821)),
        listOf(Color(0xFF3D2424), Color(0xFF171113)),
        listOf(Color(0xFF1E3A35), Color(0xFF0D1715)),
        listOf(Color(0xFF3A3150), Color(0xFF13101C)),
        listOf(Color(0xFF4A3A1D), Color(0xFF18130A)),
    )
    val colors = palettes[seed.hashCode().absoluteValue % palettes.size]
    Box(
        modifier = modifier.background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val lineColor = Color.White.copy(alpha = 0.045f)
            val step = size.width / 4f
            for (i in 1..6) {
                drawLine(
                    color = lineColor,
                    start = Offset(step * i - size.height * 0.2f, 0f),
                    end = Offset(step * i + size.height * 0.2f, size.height),
                    strokeWidth = 1.2f,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Movie,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.22f),
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = seed.trim().take(1).uppercase(Locale.US),
                color = Color.White.copy(alpha = 0.22f),
                style = MaterialTheme.typography.displayMedium,
            )
        }
    }
}

@Composable
fun PersonAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val palettes = listOf(
        StreamlineColors.Gold to Color(0xFF4C3B14),
        StreamlineColors.Ice to Color(0xFF15343D),
        StreamlineColors.Coral to Color(0xFF45211B),
        Color(0xFFB7A6E8) to Color(0xFF2C2444),
    )
    val (accent, base) = palettes[name.hashCode().absoluteValue % palettes.size]
    val initials = name.trim().split(' ').filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.6f))))
            .border(1.dp, accent.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = accent,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = if (size > 44.dp) 14.sp else 12.sp,
            ),
        )
    }
}

@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -0.8f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-progress",
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            StreamlineColors.SurfaceRaised,
            Color(0xFF262A33),
            StreamlineColors.SurfaceRaised,
        ),
        start = Offset(progress * 900f, 0f),
        end = Offset(progress * 900f + 500f, 500f),
    )
    Box(modifier = modifier.clip(shape).background(brush))
}

@Composable
fun PressScale(
    interactionSource: MutableInteractionSource,
    content: @Composable (scale: Float) -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = tween(140),
        label = "press-scale",
    )
    content(scale)
}

@Composable
fun EmptyOrErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null,
    tone: Color = StreamlineColors.Gold,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CineOrb(tone = tone, icon = icon)
        Spacer(Modifier.height(22.dp))
        Text(
            text = title,
            color = StreamlineColors.TextPrimary,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            color = StreamlineColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.9f),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryActionButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
private fun CineOrb(
    tone: Color,
    icon: ImageVector?,
) {
    Box(
        modifier = Modifier.size(132.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tone.copy(alpha = 0.16f), Color.Transparent),
                    center = center,
                    radius = size.minDimension / 2f,
                ),
                radius = size.minDimension / 2f,
            )
            drawCircle(
                color = tone.copy(alpha = 0.22f),
                radius = size.minDimension * 0.36f,
                style = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f))),
            )
            drawCircle(
                color = tone.copy(alpha = 0.12f),
                radius = size.minDimension * 0.44f,
                style = Stroke(width = 1f),
            )
            for (index in 0 until 6) {
                val angle = Math.toRadians(index * 60.0)
                val dotCenter = Offset(
                    x = center.x + cos(angle).toFloat() * size.minDimension * 0.36f,
                    y = center.y + sin(angle).toFloat() * size.minDimension * 0.36f,
                )
                drawCircle(tone.copy(alpha = 0.55f), radius = 3.2f, center = dotCenter)
            }
            drawArc(
                color = tone.copy(alpha = 0.8f),
                startAngle = 210f,
                sweepAngle = 66f,
                useCenter = false,
                topLeft = Offset(size.width * 0.18f, size.height * 0.18f),
                size = Size(size.width * 0.64f, size.height * 0.64f),
                style = Stroke(width = 2.2f, cap = StrokeCap.Round),
            )
        }
        Icon(
            imageVector = icon ?: Icons.Rounded.Movie,
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(38.dp),
        )
    }
}

fun formatPlaybackTime(milliseconds: Long): String {
    if (milliseconds <= 0L) return "0:00"
    val totalSeconds = milliseconds / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}
