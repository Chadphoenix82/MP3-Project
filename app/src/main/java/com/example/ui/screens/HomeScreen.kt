package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.db.TrackEntity
import com.example.player.PlaybackUiState
import com.example.ui.components.AudioFormatBadge
import com.example.ui.components.PhotonAlbumArt
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.LocalPhotonColors

@Composable
fun HomeScreen(
    playbackState: PlaybackUiState,
    allTracks: List<TrackEntity>,
    recentlyAddedTracks: List<TrackEntity>,
    mostPlayedTracks: List<Pair<TrackEntity, Int>>,
    forgottenFavorites: List<TrackEntity>,
    onResumeOrExpandDeck: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayTracks: (List<TrackEntity>, Int, String) -> Unit,
    onSmartMix25Shuffle: () -> Unit,
    onSmartMixLossless: () -> Unit,
    onSmartMixRecentlyImported: () -> Unit,
    onImportAudio: () -> Unit,
    onGenerateStarterWavs: () -> Unit,
    onNavigateToAlbum: (String, String?) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val heroTrack = playbackState.currentTrack ?: recentlyAddedTracks.firstOrNull()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_dashboard_screen"),
        contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Continue Listening Hero Card
        item(key = "hero_card") {
            ContinueListeningHeroCard(
                activeTrack = heroTrack,
                isCurrentlyLoaded = playbackState.currentTrack != null,
                isPlaying = playbackState.isPlaying,
                positionMs = playbackState.positionMs,
                durationMs = playbackState.durationMs.takeIf { it > 0L } ?: heroTrack?.durationMs ?: 0L,
                onPrimaryAction = {
                    when {
                        playbackState.currentTrack != null -> onTogglePlayPause()
                        heroTrack != null -> onPlayTracks(recentlyAddedTracks, 0, "Continue Listening")
                        else -> onGenerateStarterWavs()
                    }
                },
                onCardTap = {
                    if (playbackState.currentTrack != null) {
                        onResumeOrExpandDeck()
                    } else if (heroTrack != null) {
                        onPlayTracks(recentlyAddedTracks, 0, "Continue Listening")
                    }
                },
                onArtistClick = { heroTrack?.artist?.let(onNavigateToArtist) },
                onAlbumClick = { heroTrack?.let { onNavigateToAlbum(it.album, it.artist) } },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Empty-library quick onboarding card if no audio tracks yet
        if (allTracks.isEmpty()) {
            item(key = "empty_deck_cta") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = photonColors.glassSurface,
                    border = BorderStroke(1.dp, photonColors.photonGlow.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Load Your Audio Deck",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Import FLAC, WAV, MP3, or Opus files from storage, or synthesize lossless 44.1kHz studio WAV stems immediately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onImportAudio,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("home_import_audio_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = photonColors.photonGlow,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Rounded.LibraryAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import Audio")
                            }
                            OutlinedButton(
                                onClick = onGenerateStarterWavs,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("home_generate_wav_btn")
                            ) {
                                Icon(Icons.Rounded.Waves, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Studio WAVs")
                            }
                        }
                    }
                }
            }
        }

        // 2. Smart Auto-Mixes (1-tap queue generators)
        item(key = "smart_auto_mixes") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeSectionHeader(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "Smart Auto-Mixes",
                    subtitle = "1-tap adaptive deck queues"
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "mix_25_shuffle") {
                        SmartAutoMixCard(
                            title = "25-Track Shuffle",
                            subtitle = "Instant energized mix",
                            icon = Icons.Rounded.Shuffle,
                            accent = photonColors.photonGlow,
                            onClick = onSmartMix25Shuffle,
                            modifier = Modifier.testTag("smart_mix_25_shuffle_btn")
                        )
                    }
                    item(key = "mix_lossless") {
                        SmartAutoMixCard(
                            title = "Lossless / Hi-Res",
                            subtitle = "FLAC, WAV & 256k+",
                            icon = Icons.Rounded.HighQuality,
                            accent = photonColors.losslessBadge,
                            onClick = onSmartMixLossless,
                            modifier = Modifier.testTag("smart_mix_lossless_btn")
                        )
                    }
                    item(key = "mix_recent") {
                        SmartAutoMixCard(
                            title = "Recently Imported",
                            subtitle = "Newest deck arrivals",
                            icon = Icons.Rounded.NewReleases,
                            accent = photonColors.nebulaAccent,
                            onClick = onSmartMixRecentlyImported,
                            modifier = Modifier.testTag("smart_mix_recent_btn")
                        )
                    }
                }
            }
        }

        // 3. Recently Added Carousel
        if (recentlyAddedTracks.isNotEmpty()) {
            item(key = "recently_added_carousel") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeSectionHeader(
                        icon = Icons.Rounded.NewReleases,
                        title = "Recently Added",
                        subtitle = "${recentlyAddedTracks.size} latest imports"
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(recentlyAddedTracks, key = { _, t -> "recent_${t.id}" }) { idx, track ->
                            HomeTrackTile(
                                track = track,
                                badgeText = track.format.uppercase(),
                                onClick = { onPlayTracks(recentlyAddedTracks, idx, "Recently Added") }
                            )
                        }
                    }
                }
            }
        }

        // 4. Most Played Carousel
        if (mostPlayedTracks.isNotEmpty()) {
            item(key = "most_played_carousel") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeSectionHeader(
                        icon = Icons.Rounded.TrendingUp,
                        title = "Most Played",
                        subtitle = "Your top deck rotation"
                    )
                    val trackList = mostPlayedTracks.map { it.first }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(mostPlayedTracks, key = { _, pair -> "top_${pair.first.id}" }) { idx, (track, plays) ->
                            HomeTrackTile(
                                track = track,
                                badgeText = "$plays plays",
                                onClick = { onPlayTracks(trackList, idx, "Most Played") }
                            )
                        }
                    }
                }
            }
        }

        // 5. Forgotten Favorites Carousel (high play count, not played in 30+ days)
        if (forgottenFavorites.isNotEmpty()) {
            item(key = "forgotten_favorites_carousel") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeSectionHeader(
                        icon = Icons.Rounded.History,
                        title = "Forgotten Favorites",
                        subtitle = "Rediscover classics idle 30+ days"
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(forgottenFavorites, key = { _, t -> "forgot_${t.id}" }) { idx, track ->
                            HomeTrackTile(
                                track = track,
                                badgeText = formatDurationMs(track.durationMs),
                                onClick = { onPlayTracks(forgottenFavorites, idx, "Forgotten Favorites") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueListeningHeroCard(
    activeTrack: TrackEntity?,
    isCurrentlyLoaded: Boolean,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPrimaryAction: () -> Unit,
    onCardTap: () -> Unit,
    onArtistClick: () -> Unit,
    onAlbumClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val context = LocalContext.current
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = photonColors.glassSurface,
        border = BorderStroke(1.2.dp, photonColors.photonGlow.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .height(206.dp)
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onCardTap)
            .testTag("home_continue_listening_hero")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val artworkUri = activeTrack?.artworkUri?.takeIf { it.isNotBlank() }
            if (artworkUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(artworkUri).crossfade(true).build(),
                    contentDescription = "Continue listening cover art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.img_photon_deck_hero),
                    contentDescription = "Photon Audio Deck hero art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.28f),
                                Color.Black.copy(alpha = 0.65f),
                                Color.Black.copy(alpha = 0.90f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = photonColors.photonGlow.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, photonColors.photonGlow.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = photonColors.photonGlow,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isCurrentlyLoaded) "CONTINUE LISTENING" else "PHOTON AUDIO DECK",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }

                    activeTrack?.let {
                        AudioFormatBadge(format = it.format)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = activeTrack?.title ?: "Ready for Studio Playback",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = activeTrack?.artist ?: "Tap to generate or play tracks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = photonColors.photonGlow,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable(enabled = activeTrack != null, onClick = onArtistClick)
                                )
                                if (activeTrack != null && activeTrack.album.isNotBlank()) {
                                    Text(text = "•", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = activeTrack.album,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.78f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable(onClick = onAlbumClick)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = onPrimaryAction,
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = photonColors.photonGlow,
                                contentColor = Color(0xFF040818)
                            ),
                            modifier = Modifier.testTag("home_hero_resume_cta")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Resume",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (isPlaying) "Pause" else "Resume",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    if (isCurrentlyLoaded && durationMs > 0L) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = photonColors.photonGlow,
                                trackColor = Color.White.copy(alpha = 0.22f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatDurationMs(positionMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.78f)
                                )
                                Text(
                                    text = formatDurationMs(durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.78f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    val photonColors = LocalPhotonColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = photonColors.photonGlow,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SmartAutoMixCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = photonColors.glassSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
        modifier = modifier
            .width(164.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HomeTrackTile(
    track: TrackEntity,
    badgeText: String,
    onClick: () -> Unit
) {
    val photonColors = LocalPhotonColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = photonColors.glassSurface,
        border = BorderStroke(0.9.dp, photonColors.cardBorder.copy(alpha = 0.7f)),
        modifier = Modifier
            .width(142.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                PhotonAlbumArt(
                    artworkUri = track.artworkUri,
                    contentDescription = track.title,
                    size = 122.dp,
                    cornerRadius = 14.dp
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = photonColors.photonGlow,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
