package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.db.TrackEntity
import com.example.data.library.FolderMonitorStatus
import com.example.data.model.AudioSignalInfo
import com.example.ui.theme.LocalPhotonColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Signature Photon Deck console hero dynamically synced to the active Cosmic/Custom theme,
 * featuring animated holographic rim shimmer, live Now Playing capsule with album art & codec badge,
 * frosted telemetry pills, and tactile orbital controls.
 */
@Composable
fun PhotonDeckHero(
    totalTracks: Int,
    genreCount: Int,
    playlistCount: Int,
    isPlaying: Boolean,
    monitorStatus: FolderMonitorStatus,
    currentTrack: TrackEntity? = null,
    signalInfo: AudioSignalInfo = AudioSignalInfo(),
    onOpenNowPlaying: () -> Unit = {},
    onTogglePlayPause: () -> Unit = {},
    onImportFilesClick: () -> Unit,
    onWatchFolderClick: () -> Unit,
    onScanFoldersNow: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val haptics = LocalHapticFeedback.current
    val deckShape = RoundedCornerShape(26.dp)

    val glow = photonColors.photonGlow
    val accent = photonColors.nebulaAccent
    val deepBase = photonColors.backdropTop
    val cardGlass = lerp(deepBase, photonColors.glassSurface, 0.72f).copy(alpha = 0.78f)
    val luminousHighlight = lerp(Color.White, glow, 0.35f)
    val onGlowInk = Color(0xFF040818)

    val infiniteTransition = rememberInfiniteTransition(label = "photon_deck_energy")
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 4200 else 7600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_phase"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    val rimBrush = Brush.linearGradient(
        colors = listOf(
            luminousHighlight.copy(alpha = 0.88f * glowPulse),
            glow.copy(alpha = 0.72f),
            accent.copy(alpha = 0.80f * glowPulse),
            glow.copy(alpha = 0.92f)
        ),
        start = Offset(0f, 0f),
        end = Offset(900f, 450f)
    )

    val playInteraction = remember { MutableInteractionSource() }
    val playPressed by playInteraction.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (playPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "deck_play_scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(
                elevation = 18.dp,
                shape = deckShape,
                ambientColor = glow,
                spotColor = glow
            )
            .clip(deckShape)
            .border(width = 1.4.dp, brush = rimBrush, shape = deckShape)
            .background(deepBase)
            .testTag("photon_deck_hero_card")
    ) {
        // Layer 1: Bespoke 3D Crystalline Prism Artwork tinted subtly to match active theme
        Image(
            painter = painterResource(id = R.drawable.img_photon_deck_hero),
            contentDescription = "Photon Deck crystalline cosmic prism artwork",
            contentScale = ContentScale.Crop,
            alpha = 0.75f,
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.55f) }),
            modifier = Modifier.matchParentSize()
        )

        // Layer 1b: Active theme chromatic wash over prism art
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            deepBase.copy(alpha = 0.86f),
                            glow.copy(alpha = 0.22f),
                            accent.copy(alpha = 0.28f)
                        )
                    )
                )
        )

        // Layer 2: Directional readability scrim
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to deepBase.copy(alpha = 0.90f),
                        0.50f to deepBase.copy(alpha = 0.68f),
                        0.82f to deepBase.copy(alpha = 0.34f),
                        1.0f to deepBase.copy(alpha = 0.22f)
                    )
                )
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to deepBase.copy(alpha = 0.22f),
                        0.55f to deepBase.copy(alpha = 0.14f),
                        1.0f to deepBase.copy(alpha = 0.88f)
                    )
                )
        )

        // Layer 3: Live Theme-Synced Photon Energy Canvas
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            val orbX = w * (0.82f + 0.03f * cos(shimmerPhase))
            val orbY = h * (0.24f + 0.03f * sin(shimmerPhase))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glow.copy(alpha = 0.26f * glowPulse),
                        accent.copy(alpha = 0.12f * glowPulse),
                        Color.Transparent
                    ),
                    center = Offset(orbX, orbY),
                    radius = w * 0.44f
                ),
                center = Offset(orbX, orbY),
                radius = w * 0.44f
            )

            val wavePath = Path()
            val waveMidY = h * 0.54f
            val amplitude = if (isPlaying) 9.dp.toPx() else 4.dp.toPx()
            val steps = 42
            for (i in 0..steps) {
                val frac = i.toFloat() / steps.toFloat()
                val x = frac * w
                val env = sin(frac * PI).toFloat()
                val y = waveMidY +
                    sin(frac * 10f + shimmerPhase * 2f) * amplitude * env +
                    cos(frac * 5f - shimmerPhase) * (amplitude * 0.45f) * env
                if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
            }
            drawPath(
                path = wavePath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        glow.copy(alpha = if (isPlaying) 0.62f else 0.30f),
                        accent.copy(alpha = if (isPlaying) 0.52f else 0.24f),
                        Color.Transparent
                    )
                ),
                style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
            )

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = h * 0.28f
                ),
                cornerRadius = CornerRadius(26.dp.toPx(), 26.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Layer 4: Foreground Console Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Eyebrow Badge + Orbital Play/Shuffle Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = cardGlass,
                        border = BorderStroke(
                            0.85.dp,
                            if (isPlaying) photonColors.losslessBadge.copy(alpha = 0.72f) else glow.copy(alpha = 0.52f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isPlaying) photonColors.losslessBadge else glow)
                            )
                            Text(
                                text = if (isPlaying) "ACTIVE PHOTON STREAM" else "HI-RES AUDIO ENGINE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.1.sp,
                                    fontSize = 10.sp
                                ),
                                color = if (isPlaying) photonColors.losslessBadge else luminousHighlight,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "PHOTON DECK",
                        style = MaterialTheme.typography.headlineSmall.merge(
                            TextStyle(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.4.sp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White,
                                        luminousHighlight,
                                        glow
                                    )
                                )
                            )
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "Precision Offline Deck · 10-Band DSP Ready",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFFD0E2F8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (totalTracks > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    ) {
                        Surface(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onShuffleAll()
                            },
                            shape = CircleShape,
                            color = cardGlass,
                            border = BorderStroke(1.1.dp, glow.copy(alpha = 0.65f)),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("hero_shuffle_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = "Shuffle library",
                                    tint = Color.White,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .scale(playScale)
                                .shadow(
                                    elevation = 12.dp,
                                    shape = CircleShape,
                                    ambientColor = glow,
                                    spotColor = glow
                                )
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            luminousHighlight,
                                            glow,
                                            lerp(glow, accent, 0.45f)
                                        )
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.65f), CircleShape)
                                .clickable(
                                    interactionSource = playInteraction,
                                    indication = null,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onPlayAll()
                                    }
                                )
                                .testTag("hero_play_all_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play all tracks",
                                tint = onGlowInk,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }

            // Live Now Playing Capsule inside Photon Deck (when a track is loaded)
            if (currentTrack != null) {
                val sampleRateLabel = if (signalInfo.sourceSampleRateHz >= 1000) {
                    val khz = signalInfo.sourceSampleRateHz / 1000.0
                    if (khz % 1.0 == 0.0) "${khz.toInt()}kHz" else "%.1fkHz".format(khz)
                } else {
                    "${currentTrack.sampleRateHz / 1000}kHz"
                }
                val codecBadge = "${currentTrack.format.uppercase().ifBlank { "AUDIO" }} · ${signalInfo.sourceBitDepthBits}bit · $sampleRateLabel"

                Surface(
                    onClick = onOpenNowPlaying,
                    shape = RoundedCornerShape(16.dp),
                    color = cardGlass.copy(alpha = 0.90f),
                    border = BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(listOf(glow.copy(alpha = 0.7f), accent.copy(alpha = 0.55f)))
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("deck_now_playing_capsule")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PhotonAlbumArt(
                            artworkUri = currentTrack.artworkUri,
                            contentDescription = "Now playing artwork",
                            size = 46.dp,
                            cornerRadius = 11.dp,
                            isPlaying = isPlaying
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(99.dp),
                                    color = glow.copy(alpha = 0.18f),
                                    border = BorderStroke(0.6.dp, glow.copy(alpha = 0.55f))
                                ) {
                                    Text(
                                        text = codecBadge,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = luminousHighlight,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                                if (currentTrack.customGainDb != 0f || !currentTrack.preferredEqPreset.isNullOrBlank()) {
                                    Text(
                                        text = "DSP",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        ),
                                        color = photonColors.goldBadge
                                    )
                                }
                            }
                            Text(
                                text = currentTrack.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentTrack.artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFBFD7F2),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTogglePlayPause()
                            },
                            shape = CircleShape,
                            color = glow,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("deck_capsule_play_pause")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = onGlowInk,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Frosted Telemetry Pill Row (Songs · Genres · Playlists)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeckStatPill(
                    icon = Icons.Rounded.MusicNote,
                    value = "$totalTracks",
                    label = if (totalTracks == 1) "Song" else "Songs",
                    accent = glow,
                    pillBg = cardGlass,
                    modifier = Modifier.weight(1f)
                )
                DeckStatPill(
                    icon = Icons.Rounded.Folder,
                    value = "$genreCount",
                    label = if (genreCount == 1) "Genre" else "Genres",
                    accent = photonColors.losslessBadge,
                    pillBg = cardGlass,
                    modifier = Modifier.weight(1f)
                )
                DeckStatPill(
                    icon = Icons.Rounded.QueueMusic,
                    value = "$playlistCount",
                    label = if (playlistCount == 1) "Playlist" else "Playlists",
                    accent = accent,
                    pillBg = cardGlass,
                    modifier = Modifier.weight(1f)
                )
            }

            // Frosted Glass Live Folder Monitor Console Strip
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = cardGlass,
                border = BorderStroke(0.9.dp, glow.copy(alpha = 0.42f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 11.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (monitorStatus.isScanning || monitorStatus.isEnrichingMetadata) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp,
                            color = glow
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Radar,
                            contentDescription = "Folder monitor active",
                            tint = photonColors.losslessBadge,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = monitorStatus.lastEventMessage,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFFDCEBFF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        onClick = onScanFoldersNow,
                        shape = RoundedCornerShape(10.dp),
                        color = glow.copy(alpha = 0.22f),
                        border = BorderStroke(0.8.dp, glow.copy(alpha = 0.55f)),
                        modifier = Modifier.testTag("scan_folders_now_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Scan watched folders",
                                tint = luminousHighlight,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Scan",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Dual Action Console Dock (Import Files & Watch Folder)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    luminousHighlight,
                                    glow,
                                    lerp(glow, accent, 0.30f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(15.dp)
                        )
                        .clickable(onClick = onImportFilesClick)
                        .testTag("import_audio_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AudioFile,
                            contentDescription = null,
                            tint = onGlowInk,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "Import Files",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = onGlowInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    cardGlass.copy(alpha = 0.90f),
                                    lerp(cardGlass, accent, 0.16f).copy(alpha = 0.90f)
                                )
                            )
                        )
                        .border(
                            width = 1.1.dp,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    glow.copy(alpha = 0.80f),
                                    accent.copy(alpha = 0.72f)
                                )
                            ),
                            shape = RoundedCornerShape(15.dp)
                        )
                        .clickable(onClick = onWatchFolderClick)
                        .testTag("watch_folder_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CreateNewFolder,
                            contentDescription = null,
                            tint = luminousHighlight,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "Watch Folder",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckStatPill(
    icon: ImageVector,
    value: String,
    label: String,
    accent: Color,
    pillBg: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = pillBg,
        border = BorderStroke(0.9.dp, accent.copy(alpha = 0.48f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 16.sp
                    ),
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        lineHeight = 12.sp
                    ),
                    color = accent.copy(alpha = 0.95f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
