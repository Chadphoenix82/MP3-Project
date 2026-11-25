package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.ArtistPlayStat
import com.example.data.db.GenrePlayStat
import com.example.data.db.TrackEntity
import com.example.ui.components.AudioFormatBadge
import com.example.ui.components.PhotonAlbumArt
import com.example.ui.components.PhotonEqualizerBars
import com.example.ui.theme.LocalPhotonColors

/**
 * Local-only Listening Stats dashboard showcasing total plays, favorites count,
 * ranked Most Played tracks, Top Genres proportional bars, and Top Artists.
 */
@Composable
fun ListeningStatsScreen(
    totalPlayCount: Int,
    totalLibraryTracks: Int,
    favoriteTracks: List<TrackEntity>,
    mostPlayedTracks: List<Pair<TrackEntity, Int>>,
    genreStats: List<GenrePlayStat>,
    artistStats: List<ArtistPlayStat>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    onPlayTracks: (List<TrackEntity>, Int) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val topGenreMax = genreStats.maxOfOrNull { it.playCount }?.coerceAtLeast(1) ?: 1
    val topArtistName = artistStats.firstOrNull()?.artist ?: "—"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("listening_stats_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 156.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "stats_header") {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "LISTENING STATS",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "100% private on-device telemetry · Most Played · Top Genres · Favorites",
                    style = MaterialTheme.typography.labelMedium,
                    color = photonColors.photonGlow
                )
            }
        }

        // Summary Telemetry Grid (2x2)
        item(key = "stats_summary_grid") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatSummaryTile(
                        icon = Icons.Rounded.GraphicEq,
                        label = "Total Plays",
                        value = "$totalPlayCount",
                        accent = photonColors.photonGlow,
                        modifier = Modifier.weight(1f)
                    )
                    StatSummaryTile(
                        icon = Icons.Rounded.Favorite,
                        label = "Favorites",
                        value = "${favoriteTracks.size}",
                        accent = Color(0xFFFF5C8A),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatSummaryTile(
                        icon = Icons.Rounded.LibraryMusic,
                        label = "Library Songs",
                        value = "$totalLibraryTracks",
                        accent = photonColors.losslessBadge,
                        modifier = Modifier.weight(1f)
                    )
                    StatSummaryTile(
                        icon = Icons.Rounded.Person,
                        label = "Top Artist",
                        value = topArtistName,
                        accent = photonColors.nebulaAccent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Favorites Quick Play Banner
        if (favoriteTracks.isNotEmpty()) {
            item(key = "favorites_play_banner") {
                Surface(
                    onClick = { onPlayTracks(favoriteTracks, 0) },
                    shape = RoundedCornerShape(18.dp),
                    color = photonColors.glassSurface,
                    border = BorderStroke(1.1.dp, Color(0x88FF5C8A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stats_play_favorites_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33FF5C8A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Favorite,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5C8A),
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Play All Favorites",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${favoriteTracks.size} hearted track${if (favoriteTracks.size == 1) "" else "s"} ready to play",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFF88AA)
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFF5C8A),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = "Play favorites",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top Genres Bar Breakdown
        if (genreStats.isNotEmpty()) {
            item(key = "top_genres_card") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = photonColors.glassSurface,
                    border = BorderStroke(1.dp, photonColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.BarChart, contentDescription = null, tint = photonColors.losslessBadge)
                            Text(
                                text = "Top Genres",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        genreStats.take(6).forEach { stat ->
                            val frac = (stat.playCount.toFloat() / topGenreMax.toFloat()).coerceIn(0.08f, 1f)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stat.genre,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${stat.playCount} play${if (stat.playCount == 1) "" else "s"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = photonColors.photonGlow
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(photonColors.cardBorder.copy(alpha = 0.4f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(frac)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(photonColors.photonGlow, photonColors.nebulaAccent)
                                                )
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Most Played Tracks Header
        item(key = "most_played_header") {
            Text(
                text = "MOST PLAYED TRACKS",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = photonColors.photonGlow
            )
        }

        if (mostPlayedTracks.isEmpty()) {
            item(key = "stats_empty") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = photonColors.glassSurface,
                    border = BorderStroke(1.dp, photonColors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.BarChart,
                            contentDescription = null,
                            tint = photonColors.photonGlow,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = "No Listening Stats Yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Play songs from your library to unlock Most Played rankings, Top Genres, and Recently Played highlights.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            val rankedList = mostPlayedTracks.map { it.first }
            itemsIndexed(
                items = mostPlayedTracks,
                key = { _, (track, _) -> "most_played_${track.id}" }
            ) { index, (track, plays) ->
                val isCurrent = track.id == currentTrackId
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isCurrent) photonColors.glassSurface else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        width = if (isCurrent) 1.2.dp else 0.8.dp,
                        color = if (isCurrent) photonColors.photonGlow else photonColors.cardBorder.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayTracks(rankedList, index) }
                        .testTag("most_played_row_$index")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${index + 1}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (index < 3) photonColors.goldBadge else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(30.dp)
                        )

                        PhotonAlbumArt(
                            artworkUri = track.artworkUri,
                            contentDescription = track.title,
                            size = 44.dp,
                            cornerRadius = 11.dp,
                            isPlaying = isCurrent && isPlaying
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isCurrent) photonColors.photonGlow else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                AudioFormatBadge(format = track.format)
                            }
                            Text(
                                text = "${track.artist} · $plays play${if (plays == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isCurrent) {
                            PhotonEqualizerBars(
                                isPlaying = isPlaying,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }

                        IconButton(onClick = { onToggleFavorite(track) }) {
                            Icon(
                                imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = if (track.isFavorite) "Remove from favorites" else "Add to favorites",
                                tint = if (track.isFavorite) Color(0xFFFF5C8A) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatSummaryTile(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = photonColors.glassSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1
            )
        }
    }
}
