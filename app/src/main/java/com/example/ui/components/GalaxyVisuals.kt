package com.example.ui.components

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.ui.theme.LocalPhotonColors
import kotlin.math.PI
import kotlin.math.sin

private data class StarSeed(
    val xFrac: Float,
    val yFrac: Float,
    val radiusDp: Float,
    val phaseOffset: Float,
    val driftSpeed: Float
)

/**
 * Deep Blue Galaxy / Yu-Gi-Oh Photon Deck cosmic backdrop with luminous radial nebula glows,
 * drifting starlight particles, and subtle crystalline light arcs.
 */
@Composable
fun GalaxyBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val photonColors = LocalPhotonColors.current
    val stars = remember {
        List(36) { idx ->
            val seed = (idx * 7919 + 104729) % 1000
            StarSeed(
                xFrac = ((idx * 37 + 13) % 100) / 100f,
                yFrac = ((idx * 53 + 29) % 100) / 100f,
                radiusDp = 0.9f + (seed % 18) / 10f,
                phaseOffset = (idx * 0.43f) % (2f * PI.toFloat()),
                driftSpeed = 0.015f + (idx % 5) * 0.004f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "galaxy_drift")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "star_pulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        photonColors.backdropTop,
                        photonColors.backdropMid,
                        photonColors.backdropBottom
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Upper-right Photon Cyan nebula bloom
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        photonColors.photonGlow.copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.86f, h * 0.12f),
                    radius = w * 0.72f
                ),
                center = Offset(w * 0.86f, h * 0.12f),
                radius = w * 0.72f
            )

            // Lower-left Astral Violet cosmic cloud
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        photonColors.nebulaAccent.copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.14f, h * 0.78f),
                    radius = w * 0.68f
                ),
                center = Offset(w * 0.14f, h * 0.78f),
                radius = w * 0.68f
            )

            // Subtle crystalline Photon orbit ring arc
            drawCircle(
                color = photonColors.photonGlow.copy(alpha = 0.06f),
                radius = w * 0.56f,
                center = Offset(w * 0.92f, h * 0.18f),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Twinkling starlight field
            stars.forEach { star ->
                val twinkle = 0.25f + 0.55f * ((sin(pulsePhase + star.phaseOffset) + 1f) / 2f)
                val x = ((star.xFrac + (pulsePhase / (2f * PI.toFloat())) * star.driftSpeed) % 1f) * w
                val y = star.yFrac * h
                drawCircle(
                    color = photonColors.starColor.copy(alpha = twinkle * 0.65f),
                    radius = star.radiusDp.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
        content()
    }
}

@Composable
fun PhotonAlbumArt(
    artworkUri: String?,
    contentDescription: String,
    size: Dp,
    cornerRadius: Dp = 14.dp,
    isPlaying: Boolean = false,
    showPhotonRing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "photon_halo")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "halo_rotation"
    )

    Box(
        modifier = modifier.size(if (showPhotonRing) size + 22.dp else size),
        contentAlignment = Alignment.Center
    ) {
        if (showPhotonRing) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 3.dp.toPx()
                val ringRadius = (this.size.minDimension / 2f) - strokeWidth
                rotate(degrees = if (isPlaying) ringRotation else 25f) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                photonColors.photonGlow.copy(alpha = 0.95f),
                                photonColors.nebulaAccent.copy(alpha = 0.5f),
                                Color.Transparent,
                                photonColors.photonGlow.copy(alpha = 0.85f)
                            )
                        ),
                        radius = ringRadius,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
        }

        val shape = if (showPhotonRing && cornerRadius >= size / 2) CircleShape else RoundedCornerShape(cornerRadius)
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(artworkUri?.takeIf { it.isNotBlank() } ?: R.drawable.img_default_album_art)
                .crossfade(true)
                .build(),
            placeholder = painterResource(id = R.drawable.img_default_album_art),
            error = painterResource(id = R.drawable.img_default_album_art),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(shape)
                .border(
                    width = if (isPlaying) 1.5.dp else 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            photonColors.photonGlow.copy(alpha = if (isPlaying) 0.9f else 0.35f),
                            photonColors.nebulaAccent.copy(alpha = if (isPlaying) 0.7f else 0.2f)
                        )
                    ),
                    shape = shape
                )
        )
    }
}

@Composable
fun PhotonEqualizerBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val transition = rememberInfiniteTransition(label = "eq_bars")
    val p1 by transition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "b1"
    )
    val p2 by transition.animateFloat(
        initialValue = 0.95f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(340), RepeatMode.Reverse), label = "b2"
    )
    val p3 by transition.animateFloat(
        initialValue = 0.35f, targetValue = 0.88f,
        animationSpec = infiniteRepeatable(tween(510), RepeatMode.Reverse), label = "b3"
    )
    val p4 by transition.animateFloat(
        initialValue = 0.75f, targetValue = 0.28f,
        animationSpec = infiniteRepeatable(tween(390), RepeatMode.Reverse), label = "b4"
    )

    val levels = if (isPlaying) listOf(p1, p2, p3, p4) else listOf(0.3f, 0.55f, 0.35f, 0.25f)

    Row(
        modifier = modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        levels.forEach { level ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((18f * level).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(photonColors.photonGlow, photonColors.nebulaAccent)
                        )
                    )
            )
        }
    }
}

@Composable
fun AudioFormatBadge(
    format: String,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val upper = format.uppercase()
    val (tint, bgAlpha) = when (upper) {
        "FLAC" -> photonColors.losslessBadge to 0.16f
        "WAV" -> photonColors.goldBadge to 0.16f
        "OPUS" -> photonColors.photonGlow to 0.16f
        "AAC", "M4A" -> photonColors.nebulaAccent to 0.16f
        "OGG" -> Color(0xFF60A5FA) to 0.16f
        "MP3" -> MaterialTheme.colorScheme.secondary to 0.16f
        else -> MaterialTheme.colorScheme.onSurfaceVariant to 0.12f
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = tint.copy(alpha = bgAlpha),
        border = BorderStroke(0.75.dp, tint.copy(alpha = 0.55f))
    ) {
        Text(
            text = upper,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = tint,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return "%d:%02d".format(mins, secs)
}
