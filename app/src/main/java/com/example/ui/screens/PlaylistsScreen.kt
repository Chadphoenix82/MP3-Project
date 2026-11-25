package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.PlaylistWithSummary
import com.example.data.db.TrackEntity
import com.example.ui.components.PhotonAlbumArt
import com.example.ui.components.ReorderableTrackLazyColumn
import com.example.ui.theme.LocalPhotonColors

@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistWithSummary>,
    allLibraryTracks: List<TrackEntity>,
    selectedPlaylistId: Long?,
    selectedPlaylistTracks: List<TrackEntity>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    onSelectPlaylist: (Long?) -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onRenamePlaylist: (Long, String, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onAddTrackToPlaylist: (Long, TrackEntity) -> Unit,
    onRemoveTrackFromPlaylist: (Long, TrackEntity) -> Unit,
    onReorderPlaylistTracks: (Long, List<TrackEntity>) -> Unit,
    onPlayTracks: (List<TrackEntity>, Int) -> Unit,
    onShuffleTracks: (List<TrackEntity>) -> Unit,
    onAutoCompleteMetadata: (TrackEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedSummary = playlists.firstOrNull { it.id == selectedPlaylistId }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<PlaylistWithSummary?>(null) }
    var showAddSongsDialog by remember { mutableStateOf(false) }

    if (selectedSummary != null) {
        BackHandler { onSelectPlaylist(null) }

        PlaylistDetailView(
            playlist = selectedSummary,
            tracks = selectedPlaylistTracks,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            onBack = { onSelectPlaylist(null) },
            onAddSongsClick = { showAddSongsDialog = true },
            onPlayTracks = onPlayTracks,
            onShuffleTracks = onShuffleTracks,
            onReorderTracks = { reordered -> onReorderPlaylistTracks(selectedSummary.id, reordered) },
            onRemoveTrack = { track -> onRemoveTrackFromPlaylist(selectedSummary.id, track) },
            onAutoCompleteMetadata = onAutoCompleteMetadata,
            modifier = modifier
        )

        if (showAddSongsDialog) {
            AddTracksToPlaylistDialog(
                playlistName = selectedSummary.name,
                allTracks = allLibraryTracks,
                existingTrackIds = selectedPlaylistTracks.map { it.id }.toSet(),
                onAddTrack = { track -> onAddTrackToPlaylist(selectedSummary.id, track) },
                onDismiss = { showAddSongsDialog = false }
            )
        }
    } else {
        PlaylistsOverview(
            playlists = playlists,
            onOpenPlaylist = { onSelectPlaylist(it.id) },
            onCreateClick = { showCreateDialog = true },
            onEditPlaylist = { editingPlaylist = it },
            onDeletePlaylist = { onDeletePlaylist(it.id) },
            modifier = modifier
        )
    }

    if (showCreateDialog) {
        PlaylistEditorDialog(
            title = "Create Photon Playlist",
            initialName = "",
            initialDescription = "",
            confirmLabel = "Create",
            onConfirm = { name, desc ->
                onCreatePlaylist(name, desc)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    editingPlaylist?.let { target ->
        PlaylistEditorDialog(
            title = "Edit Playlist",
            initialName = target.name,
            initialDescription = target.description,
            confirmLabel = "Save",
            onConfirm = { name, desc ->
                onRenamePlaylist(target.id, name, desc)
                editingPlaylist = null
            },
            onDismiss = { editingPlaylist = null }
        )
    }
}

@Composable
private fun PlaylistsOverview(
    playlists: List<PlaylistWithSummary>,
    onOpenPlaylist: (PlaylistWithSummary) -> Unit,
    onCreateClick: () -> Unit,
    onEditPlaylist: (PlaylistWithSummary) -> Unit,
    onDeletePlaylist: (PlaylistWithSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 156.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "playlists_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CONSTELLATIONS",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${playlists.size} custom playlist${if (playlists.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = photonColors.photonGlow
                    )
                }

                Button(
                    onClick = onCreateClick,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("create_playlist_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Playlist")
                }
            }
        }

        if (playlists.isEmpty()) {
            item(key = "empty_playlists") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = photonColors.glassSurface,
                    border = BorderStroke(1.dp, photonColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = photonColors.photonGlow,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "No Custom Playlists Yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Create playlists and long-press tracks inside them to drag-and-drop into your exact cosmic flow.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(playlists, key = { it.id }) { playlist ->
                var menuOpen by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist_card_${playlist.id}")
                        .clickable { onOpenPlaylist(playlist) },
                    shape = RoundedCornerShape(20.dp),
                    color = photonColors.glassSurface,
                    border = BorderStroke(1.dp, photonColors.cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PhotonAlbumArt(
                            artworkUri = playlist.coverUri,
                            contentDescription = "Playlist cover for ${playlist.name}",
                            size = 58.dp,
                            cornerRadius = 14.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (playlist.description.isNotBlank()) {
                                Text(
                                    text = playlist.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${playlist.trackCount} track${if (playlist.trackCount == 1) "" else "s"} • Drag-sort ready",
                                style = MaterialTheme.typography.labelSmall,
                                color = photonColors.photonGlow
                            )
                        }

                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist options")
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        onEditPlaylist(playlist)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onDeletePlaylist(playlist)
                                    }
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
private fun PlaylistDetailView(
    playlist: PlaylistWithSummary,
    tracks: List<TrackEntity>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onAddSongsClick: () -> Unit,
    onPlayTracks: (List<TrackEntity>, Int) -> Unit,
    onShuffleTracks: (List<TrackEntity>) -> Unit,
    onReorderTracks: (List<TrackEntity>) -> Unit,
    onRemoveTrack: (TrackEntity) -> Unit,
    onAutoCompleteMetadata: (TrackEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current

    ReorderableTrackLazyColumn(
        tracks = tracks,
        currentTrackId = currentTrackId,
        isPlaying = isPlaying,
        dragEnabled = true,
        onTrackClick = onPlayTracks,
        onTracksReordered = onReorderTracks,
        onAutoCompleteMetadata = onAutoCompleteMetadata,
        onAddToPlaylist = null,
        onRemoveTrack = onRemoveTrack,
        removeLabel = "Remove from Playlist",
        contentPadding = PaddingValues(top = 8.dp, bottom = 156.dp),
        modifier = modifier.fillMaxSize(),
        headerContent = {
            item(key = "playlist_detail_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("playlist_back_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back to playlists",
                                tint = photonColors.photonGlow
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (playlist.description.isNotBlank()) {
                                Text(
                                    text = playlist.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${tracks.size} track${if (tracks.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = photonColors.photonGlow
                            )
                        }
                        OutlinedButton(
                            onClick = onAddSongsClick,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("add_songs_to_playlist_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add")
                        }
                    }

                    if (tracks.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onPlayTracks(tracks, 0) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("playlist_play_all_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play All")
                            }
                            OutlinedButton(
                                onClick = { onShuffleTracks(tracks) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("playlist_shuffle_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Shuffle")
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = photonColors.photonGlow.copy(alpha = 0.1f),
                            border = BorderStroke(0.8.dp, photonColors.photonGlow.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.DragIndicator,
                                    contentDescription = null,
                                    tint = photonColors.photonGlow,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Long-press any track or drag its grip handle to sort order",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = photonColors.glassSurface,
                            border = BorderStroke(1.dp, photonColors.cardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("This playlist is empty", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Tap Add to include songs from your music library.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun PlaylistEditorDialog(
    title: String,
    initialName: String,
    initialDescription: String,
    confirmLabel: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var desc by remember { mutableStateOf(initialDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    placeholder = { Text("e.g., Late Night Synthwave") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist_name_input")
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Playlist Description") },
                    placeholder = { Text("Describe the mood, genre, or vibe...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist_description_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name, desc) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("playlist_confirm_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddTracksToPlaylistDialog(
    playlistName: String,
    allTracks: List<TrackEntity>,
    existingTrackIds: Set<Long>,
    onAddTrack: (TrackEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val available = allTracks.filter { it.id !in existingTrackIds }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to $playlistName", style = MaterialTheme.typography.titleMedium) },
        text = {
            if (available.isEmpty()) {
                Text("All library tracks are already in this playlist.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(available, key = { it.id }) { track ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAddTrack(track) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        track.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(Icons.Rounded.Add, contentDescription = "Add track")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
