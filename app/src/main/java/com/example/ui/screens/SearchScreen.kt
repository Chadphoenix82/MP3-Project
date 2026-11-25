package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.PlaylistWithSummary
import com.example.data.db.TrackEntity
import com.example.ui.components.AudioFormatBadge
import com.example.ui.components.PhotonAlbumArt
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.LocalPhotonColors

enum class SearchScope(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists"),
    GENRES("Genres")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    query: String,
    searchHistory: List<String>,
    allTracks: List<TrackEntity>,
    recentlyPlayedTracks: List<TrackEntity>,
    playlists: List<PlaylistWithSummary>,
    onQueryChange: (String) -> Unit,
    onCommitQuery: (String) -> Unit,
    onRemoveHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    onPlayTracks: (List<TrackEntity>, Int, String) -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onAddToQueue: (TrackEntity) -> Unit,
    onInspectTrack: (TrackEntity) -> Unit,
    onNavigateToAlbum: (String, String?) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val focusManager = LocalFocusManager.current
    var activeScope by remember { mutableStateOf(SearchScope.ALL) }

    val q = query.trim().lowercase()

    val matchedSongs = remember(q, allTracks) {
        if (q.isEmpty()) emptyList()
        else allTracks.filter {
            it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q) ||
                it.genre.lowercase().contains(q) ||
                it.composer.lowercase().contains(q)
        }.take(40)
    }

    val matchedAlbums = remember(q, allTracks) {
        if (q.isEmpty()) emptyList()
        else allTracks
            .filter { it.album.lowercase().contains(q) || it.artist.lowercase().contains(q) }
            .groupBy { it.album.ifBlank { "Unknown Album" } }
            .entries
            .take(15)
            .map { (album, list) -> Triple(album, list.first().artist, list) }
    }

    val matchedArtists = remember(q, allTracks) {
        if (q.isEmpty()) emptyList()
        else allTracks
            .filter { it.artist.lowercase().contains(q) }
            .groupBy { it.artist.ifBlank { "Unknown Artist" } }
            .entries
            .take(15)
            .map { it.key to it.value }
    }

    val matchedPlaylists = remember(q, playlists) {
        if (q.isEmpty()) emptyList()
        else playlists.filter {
            it.name.lowercase().contains(q) || it.description.lowercase().contains(q)
        }
    }

    val matchedGenres = remember(q, allTracks) {
        if (q.isEmpty()) emptyList()
        else allTracks
            .filter { it.genre.lowercase().contains(q) }
            .groupBy { it.genre.ifBlank { "Unknown Genre" } }
            .entries
            .take(12)
            .map { it.key to it.value }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("dedicated_search_screen")
    ) {
        // Search Input Bar
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search songs, albums, artists, playlists, genres...") },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = photonColors.photonGlow)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.testTag("search_clear_input_btn")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onCommitQuery(query)
                    focusManager.clearFocus()
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = photonColors.photonGlow,
                unfocusedBorderColor = photonColors.cardBorder,
                focusedContainerColor = photonColors.glassSurface,
                unfocusedContainerColor = photonColors.glassSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("search_query_input")
        )

        // Scope Filter Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            items(SearchScope.entries, key = { it.name }) { scope ->
                val selected = scope == activeScope
                FilterChip(
                    selected = selected,
                    onClick = { activeScope = scope },
                    label = { Text(scope.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = photonColors.photonGlow.copy(alpha = 0.22f),
                        selectedLabelColor = photonColors.photonGlow
                    ),
                    modifier = Modifier.testTag("search_scope_${scope.name.lowercase()}")
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (q.isEmpty()) {
                // Empty query: RECENT SEARCHES + RECENT MATCHES
                if (searchHistory.isNotEmpty()) {
                    item(key = "recent_searches_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RECENT SEARCHES",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = photonColors.photonGlow
                            )
                            TextButton(onClick = onClearHistory) {
                                Text("Clear", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    item(key = "recent_searches_chips") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            searchHistory.forEach { term ->
                                InputChip(
                                    selected = false,
                                    onClick = {
                                        onQueryChange(term)
                                        onCommitQuery(term)
                                    },
                                    label = { Text(term) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Remove $term",
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { onRemoveHistoryItem(term) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                item(key = "recent_matches_header") {
                    Text(
                        text = "RECENT MATCHES",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = photonColors.photonGlow,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                val recentSource = recentlyPlayedTracks.ifEmpty { allTracks.take(10) }
                if (recentSource.isEmpty()) {
                    item(key = "empty_recent_matches") {
                        Text(
                            text = "Start playing or searching to populate recent matches.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    itemsIndexed(recentSource, key = { _, t -> "recent_match_${t.id}" }) { idx, track ->
                        SearchSongResultRow(
                            track = track,
                            onClick = { onPlayTracks(recentSource, idx, "Recent Matches") },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onInspect = { onInspectTrack(track) }
                        )
                    }
                }
            } else {
                // Live Entity-Grouped Results
                val showSongs = (activeScope == SearchScope.ALL || activeScope == SearchScope.SONGS) && matchedSongs.isNotEmpty()
                val showAlbums = (activeScope == SearchScope.ALL || activeScope == SearchScope.ALBUMS) && matchedAlbums.isNotEmpty()
                val showArtists = (activeScope == SearchScope.ALL || activeScope == SearchScope.ARTISTS) && matchedArtists.isNotEmpty()
                val showPlaylists = (activeScope == SearchScope.ALL || activeScope == SearchScope.PLAYLISTS) && matchedPlaylists.isNotEmpty()
                val showGenres = (activeScope == SearchScope.ALL || activeScope == SearchScope.GENRES) && matchedGenres.isNotEmpty()

                if (!showSongs && !showAlbums && !showArtists && !showPlaylists && !showGenres) {
                    item(key = "no_search_results") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = photonColors.glassSurface,
                            border = BorderStroke(1.dp, photonColors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = null, tint = photonColors.photonGlow, modifier = Modifier.size(32.dp))
                                Text("No matches for \"$query\"", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Try another scope chip or search by artist, album, or genre.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (showSongs) {
                    item(key = "group_songs") {
                        SearchGroupHeader("SONGS", matchedSongs.size)
                    }
                    itemsIndexed(matchedSongs, key = { _, t -> "song_${t.id}" }) { idx, track ->
                        SearchSongResultRow(
                            track = track,
                            onClick = {
                                onCommitQuery(query)
                                onPlayTracks(matchedSongs, idx, "Search: $query")
                            },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onInspect = { onInspectTrack(track) }
                        )
                    }
                }

                if (showAlbums) {
                    item(key = "group_albums") {
                        SearchGroupHeader("ALBUMS", matchedAlbums.size)
                    }
                    items(matchedAlbums, key = { "album_${it.first}" }) { (albumName, artistName, list) ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = photonColors.glassSurface,
                            border = BorderStroke(0.8.dp, photonColors.cardBorder.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    onCommitQuery(query)
                                    onNavigateToAlbum(albumName, artistName)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                PhotonAlbumArt(
                                    artworkUri = list.firstNotNullOfOrNull { it.artworkUri },
                                    contentDescription = albumName,
                                    size = 48.dp,
                                    cornerRadius = 10.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(albumName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("$artistName · ${list.size} tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Rounded.Album, contentDescription = null, tint = photonColors.photonGlow)
                            }
                        }
                    }
                }

                if (showArtists) {
                    item(key = "group_artists") {
                        SearchGroupHeader("ARTISTS", matchedArtists.size)
                    }
                    items(matchedArtists, key = { "artist_${it.first}" }) { (artistName, list) ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = photonColors.glassSurface,
                            border = BorderStroke(0.8.dp, photonColors.cardBorder.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    onCommitQuery(query)
                                    onNavigateToArtist(artistName)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.Person, contentDescription = null, tint = photonColors.photonGlow, modifier = Modifier.size(32.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(artistName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${list.size} tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                if (showPlaylists) {
                    item(key = "group_playlists") {
                        SearchGroupHeader("PLAYLISTS", matchedPlaylists.size)
                    }
                    items(matchedPlaylists, key = { "pl_${it.id}" }) { pl ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = photonColors.glassSurface,
                            border = BorderStroke(0.8.dp, photonColors.cardBorder.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onOpenPlaylist(pl.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = photonColors.nebulaAccent, modifier = Modifier.size(28.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pl.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${pl.trackCount} tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                if (showGenres) {
                    item(key = "group_genres") {
                        SearchGroupHeader("GENRES", matchedGenres.size)
                    }
                    items(matchedGenres, key = { "genre_${it.first}" }) { (genre, list) ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = photonColors.glassSurface,
                            border = BorderStroke(0.8.dp, photonColors.cardBorder.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onPlayTracks(list, 0, "Genre: $genre") }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.Category, contentDescription = null, tint = photonColors.losslessBadge, modifier = Modifier.size(26.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(genre, style = MaterialTheme.typography.titleSmall)
                                    Text("${list.size} tracks · Tap to play", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchGroupHeader(title: String, count: Int) {
    val photonColors = LocalPhotonColors.current
    Text(
        text = "$title ($count)",
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = photonColors.photonGlow,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun SearchSongResultRow(
    track: TrackEntity,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onInspect: () -> Unit
) {
    val photonColors = LocalPhotonColors.current
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = photonColors.glassSurface,
        border = BorderStroke(0.8.dp, photonColors.cardBorder.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PhotonAlbumArt(
                artworkUri = track.artworkUri,
                contentDescription = track.title,
                size = 46.dp,
                cornerRadius = 10.dp
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AudioFormatBadge(format = track.format)
                    Text(
                        text = "${track.artist} · ${formatDurationMs(track.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("search_row_overflow_${track.id}")
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Track options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Play Next") },
                        leadingIcon = { Icon(Icons.Rounded.QueuePlayNext, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onPlayNext()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Queue") },
                        leadingIcon = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onAddToQueue()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Inspect / Edit Tags") },
                        leadingIcon = { Icon(Icons.Rounded.EditNote, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onInspect()
                        }
                    )
                }
            }
        }
    }
}
