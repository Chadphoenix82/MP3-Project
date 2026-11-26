package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.db.TrackEntity
import com.example.ui.components.PhotonAlbumArt
import com.example.ui.components.TrackRowItem
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.LocalPhotonColors

sealed class CollectionDetailTarget {
    data class Artist(val artistName: String) : CollectionDetailTarget()
    data class Album(val albumName: String, val preferredArtist: String? = null) : CollectionDetailTarget()
}

private data class ArtistAlbumCardModel(
    val albumName: String,
    val tracks: List<TrackEntity>,
    val artworkUri: String?,
    val releaseYear: Int?
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArtistAlbumDetailScreen(
    target: CollectionDetailTarget,
    allTracks: List<TrackEntity>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (albumName: String, preferredArtist: String?) -> Unit,
    onPlayTracks: (List<TrackEntity>, Int) -> Unit,
    onShuffleTracks: (List<TrackEntity>) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onOpenTrackMenu: (TrackEntity) -> Unit,
    onEditAlbumMetadata: (albumName: String, anchorTrack: TrackEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val photonColors = LocalPhotonColors.current
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val parallaxOffsetPx by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                420f
            }
        }
    }

    val matchedTracks = remember(target, allTracks) {
        when (target) {
            is CollectionDetailTarget.Artist -> {
                allTracks
                    .filter { it.artist.equals(target.artistName, ignoreCase = true) }
                    .sortedWith(
                        compareBy<TrackEntity> { it.album.lowercase() }
                            .thenBy { it.trackNumber ?: Int.MAX_VALUE }
                            .thenBy { it.title.lowercase() }
                    )
            }
            is CollectionDetailTarget.Album -> {
                allTracks
                    .filter { it.album.equals(target.albumName, ignoreCase = true) }
                    .sortedWith(
                        compareBy<TrackEntity> { it.trackNumber ?: Int.MAX_VALUE }
                            .thenBy { it.sortOrder }
                            .thenBy { it.title.lowercase() }
                    )
            }
        }
    }

    val artistAlbums = remember(target, matchedTracks) {
        if (target !is CollectionDetailTarget.Artist) emptyList()
        else matchedTracks
            .groupBy { it.album.ifBlank { "Unknown Album" } }
            .entries
            .sortedBy { it.key.lowercase() }
            .map { (albumName, tracksInAlbum) ->
                ArtistAlbumCardModel(
                    albumName = albumName,
                    tracks = tracksInAlbum.sortedBy { it.trackNumber ?: Int.MAX_VALUE },
                    artworkUri = tracksInAlbum.firstNotNullOfOrNull { it.artworkUri?.takeIf(String::isNotBlank) },
                    releaseYear = tracksInAlbum.mapNotNull { it.releaseYear }.maxOrNull()
                )
            }
    }

    val heroArtwork = remember(matchedTracks) {
        matchedTracks.firstNotNullOfOrNull { it.artworkUri?.takeIf(String::isNotBlank) }
    }
    val totalDurationMs = remember(matchedTracks) { matchedTracks.sumOf { it.durationMs.coerceAtLeast(0L) } }
    val distinctGenres = remember(matchedTracks) {
        matchedTracks.map { it.genre }.filter { it.isNotBlank() }.distinct().take(3)
    }
    val albumPrimaryArtist = remember(target, matchedTracks) {
        if (target !is CollectionDetailTarget.Album) null
        else {
            val artists = matchedTracks.map { it.artist }.filter { it.isNotBlank() }.distinct()
            when {
                artists.size == 1 -> artists.first()
                !target.preferredArtist.isNullOrBlank() -> target.preferredArtist
                artists.isNotEmpty() -> artists.first()
                else -> "Unknown Artist"
            }
        }
    }
    val releaseYear = remember(matchedTracks) { matchedTracks.mapNotNull { it.releaseYear }.maxOrNull() }
    val publisher = remember(matchedTracks) {
        matchedTracks.map { it.publisher }
            .firstOrNull { it.isNotBlank() && !it.equals("Independent", ignoreCase = true) }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("artist_album_detail_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 156.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Parallax Hero Cover Header Card
        item(key = "collection_hero") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.1.dp, photonColors.photonGlow.copy(alpha = 0.55f))
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Parallax ambient backdrop banner
                    if (!heroArtwork.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(heroArtwork)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .graphicsLayer {
                                    translationY = parallaxOffsetPx * 0.42f
                                    val s = 1f + (parallaxOffsetPx / 1800f).coerceIn(0f, 0.14f)
                                    scaleX = s
                                    scaleY = s
                                    alpha = (0.34f - (parallaxOffsetPx / 1600f)).coerceIn(0.08f, 0.34f)
                                }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        photonColors.photonGlow.copy(alpha = 0.14f),
                                        photonColors.glassSurface.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        PhotonAlbumArt(
                            artworkUri = heroArtwork,
                            contentDescription = when (target) {
                                is CollectionDetailTarget.Artist -> "Artwork for ${target.artistName}"
                                is CollectionDetailTarget.Album -> "Cover art for ${target.albumName}"
                            },
                            size = 112.dp,
                            cornerRadius = if (target is CollectionDetailTarget.Artist) 56.dp else 20.dp,
                            showPhotonRing = true,
                            modifier = Modifier.graphicsLayer {
                                translationY = parallaxOffsetPx * 0.14f
                            }
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = if (target is CollectionDetailTarget.Artist) Icons.Rounded.Person else Icons.Rounded.Album,
                                    contentDescription = null,
                                    tint = photonColors.photonGlow,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (target is CollectionDetailTarget.Artist) "ARTIST" else "ALBUM",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = photonColors.photonGlow
                                )
                            }

                            Text(
                                text = when (target) {
                                    is CollectionDetailTarget.Artist -> target.artistName
                                    is CollectionDetailTarget.Album -> target.albumName
                                },
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (target is CollectionDetailTarget.Album && !albumPrimaryArtist.isNullOrBlank()) {
                                Surface(
                                    onClick = { onNavigateToArtist(albumPrimaryArtist) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = photonColors.photonGlow.copy(alpha = 0.14f),
                                    border = BorderStroke(0.75.dp, photonColors.photonGlow.copy(alpha = 0.45f)),
                                    modifier = Modifier.testTag("album_detail_artist_link")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Person,
                                            contentDescription = "View artist $albumPrimaryArtist",
                                            tint = photonColors.photonGlow,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = albumPrimaryArtist,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = photonColors.photonGlow,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = null,
                                            tint = photonColors.photonGlow,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            val statsSummary = buildList {
                                if (target is CollectionDetailTarget.Artist && artistAlbums.isNotEmpty()) {
                                    add("${artistAlbums.size} Album${if (artistAlbums.size == 1) "" else "s"}")
                                }
                                add("${matchedTracks.size} Track${if (matchedTracks.size == 1) "" else "s"}")
                                if (totalDurationMs > 0L) add(formatDurationMs(totalDurationMs))
                                if (releaseYear != null && target is CollectionDetailTarget.Album) add("$releaseYear")
                            }.joinToString(" · ")

                            Text(
                                text = statsSummary,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (distinctGenres.isNotEmpty() || publisher != null) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            distinctGenres.forEach { genre ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = photonColors.nebulaAccent.copy(alpha = 0.16f),
                                    border = BorderStroke(0.7.dp, photonColors.nebulaAccent.copy(alpha = 0.45f))
                                ) {
                                    Text(
                                        text = genre,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = photonColors.nebulaAccent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            if (publisher != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(0.7.dp, photonColors.cardBorder)
                                ) {
                                    Text(
                                        text = publisher,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Primary Playback Actions + Edit Album shortcut
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onPlayTracks(matchedTracks, 0) },
                            enabled = matchedTracks.isNotEmpty(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = photonColors.photonGlow,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("collection_play_all_btn")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play All", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        }

                        OutlinedButton(
                            onClick = { onShuffleTracks(matchedTracks) },
                            enabled = matchedTracks.isNotEmpty(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("collection_shuffle_btn")
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }

                    if (target is CollectionDetailTarget.Album && matchedTracks.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { onEditAlbumMetadata(target.albumName, matchedTracks.first()) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("album_detail_edit_metadata_btn")
                        ) {
                            Icon(Icons.Rounded.EditNote, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Album Metadata & Cover", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
                }
            }
        }

        // 2. Artist Mode: Horizontal Albums Carousel
        if (target is CollectionDetailTarget.Artist && artistAlbums.isNotEmpty()) {
            item(key = "artist_albums_section") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ALBUMS (${artistAlbums.size})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = photonColors.photonGlow,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(artistAlbums, key = { it.albumName }) { albumCard ->
                            Surface(
                                onClick = { onNavigateToAlbum(albumCard.albumName, target.artistName) },
                                shape = RoundedCornerShape(18.dp),
                                color = photonColors.glassSurface,
                                border = BorderStroke(0.9.dp, photonColors.cardBorder),
                                modifier = Modifier
                                    .width(142.dp)
                                    .testTag("artist_album_card_${albumCard.albumName}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    PhotonAlbumArt(
                                        artworkUri = albumCard.artworkUri,
                                        contentDescription = "Open album ${albumCard.albumName}",
                                        size = 122.dp,
                                        cornerRadius = 12.dp
                                    )
                                    Text(
                                        text = albumCard.albumName,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = buildString {
                                            append("${albumCard.tracks.size} track${if (albumCard.tracks.size == 1) "" else "s"}")
                                            if (albumCard.releaseYear != null) append(" · ${albumCard.releaseYear}")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Tracklist Header
        item(key = "collection_tracks_header") {
            Text(
                text = if (target is CollectionDetailTarget.Album) "ALBUM TRACKLIST" else "ALL SONGS",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = photonColors.photonGlow,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        if (matchedTracks.isEmpty()) {
            item(key = "collection_empty") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = photonColors.glassSurface,
                    border = BorderStroke(0.9.dp, photonColors.cardBorder)
                ) {
                    Text(
                        text = "No tracks found in your local library for this selection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            itemsIndexed(matchedTracks, key = { _, track -> track.id }) { index, track ->
                val isActive = track.id == currentTrackId
                TrackRowItem(
                    track = track,
                    isCurrent = isActive,
                    isPlaying = isPlaying,
                    trackNumberBadge = if (target is CollectionDetailTarget.Album) {
                        track.trackNumber ?: (index + 1)
                    } else null,
                    subtitleOverride = buildString {
                        if (target is CollectionDetailTarget.Artist) {
                            append(track.album)
                        } else {
                            append(track.artist)
                        }
                        if (track.durationMs > 0L) {
                            append(" · ")
                            append(formatDurationMs(track.durationMs))
                        }
                    },
                    showFavoriteButton = true,
                    onToggleFavorite = { onToggleFavorite(track) },
                    onClick = { onPlayTracks(matchedTracks, index) },
                    onOpenTrackMenu = onOpenTrackMenu,
                    rowTestTag = "collection_track_item_${track.id}"
                )
            }
        }
    }
}
