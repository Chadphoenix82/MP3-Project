package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.data.model.MetadataSuggestion
import com.example.data.model.SleepTimerState
import com.example.ui.MetadataSearchUiState
import com.example.ui.components.PhotonAlbumArt
import com.example.ui.theme.LocalPhotonColors
import kotlin.math.roundToInt

/**
 * Interactive Web Metadata Auto-Complete Dialog powered by real MusicBrainz,
 * Cover Art Archive, and iTunes Search APIs — plus manual tag editor fallback.
 */
@Composable
fun MetadataAutoCompleteDialog(
    state: MetadataSearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onApplySuggestion: (MetadataSuggestion) -> Unit,
    onSaveManual: (
        trackId: Long,
        title: String,
        artist: String,
        album: String,
        genre: String,
        publisher: String,
        releaseDate: String,
        artworkUri: String?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val track = state.targetTrack ?: return
    val photonColors = LocalPhotonColors.current
    var manualEditMode by remember(track.id) { mutableStateOf(false) }

    var editTitle by remember(track.id) { mutableStateOf(track.title) }
    var editArtist by remember(track.id) { mutableStateOf(track.artist) }
    var editAlbum by remember(track.id) { mutableStateOf(track.album) }
    var editGenre by remember(track.id) { mutableStateOf(track.genre) }
    var editPublisher by remember(track.id) { mutableStateOf(track.publisher) }
    var editReleaseDate by remember(track.id) { mutableStateOf(track.releaseDate) }
    var editArtworkUrl by remember(track.id) { mutableStateOf(track.artworkUri.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = photonColors.photonGlow)
                Text(
                    text = if (manualEditMode) "Edit Song Metadata" else "Web Metadata Auto-Complete",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            if (manualEditMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Song Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editArtist,
                        onValueChange = { editArtist = it },
                        label = { Text("Artist Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editAlbum,
                        onValueChange = { editAlbum = it },
                        label = { Text("Album") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editGenre,
                        onValueChange = { editGenre = it },
                        label = { Text("Genre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editPublisher,
                        onValueChange = { editPublisher = it },
                        label = { Text("Publisher / Record Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editReleaseDate,
                        onValueChange = { editReleaseDate = it },
                        label = { Text("Release Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editArtworkUrl,
                        onValueChange = { editArtworkUrl = it },
                        label = { Text("Album Art URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = onQueryChange,
                            label = { Text("Search song / artist") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("metadata_search_input")
                        )
                        Button(
                            onClick = onSearch,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("metadata_search_button")
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search web metadata")
                        }
                    }

                    when {
                        state.isLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(color = photonColors.photonGlow)
                                    Text(
                                        "Querying MusicBrainz & iTunes catalogs...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        state.results.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = state.errorMessage ?: "Search for a song or artist to fetch exact album art, release date, publisher, and tags.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.results, key = { it.id }) { suggestion ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("metadata_result_${suggestion.id}")
                                            .clickable { onApplySuggestion(suggestion) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = BorderStroke(0.9.dp, photonColors.cardBorder)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            PhotonAlbumArt(
                                                artworkUri = suggestion.artworkUrl,
                                                contentDescription = "Suggested cover art for ${suggestion.title}",
                                                size = 54.dp,
                                                cornerRadius = 10.dp
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = suggestion.title,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${suggestion.artist} · ${suggestion.album}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = buildString {
                                                        append(suggestion.genre)
                                                        if (suggestion.releaseDate.isNotBlank()) {
                                                            append(" · ")
                                                            append(suggestion.releaseDate)
                                                        }
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = photonColors.photonGlow,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Publisher: ${suggestion.publisher} (${suggestion.source})",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = photonColors.nebulaAccent,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Icon(
                                                Icons.Rounded.CheckCircle,
                                                contentDescription = "Apply metadata",
                                                tint = photonColors.photonGlow,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (manualEditMode) {
                Button(
                    onClick = {
                        onSaveManual(
                            track.id,
                            editTitle,
                            editArtist,
                            editAlbum,
                            editGenre,
                            editPublisher,
                            editReleaseDate,
                            editArtworkUrl.takeIf { it.isNotBlank() }
                        )
                    }
                ) {
                    Text("Save Tags")
                }
            } else {
                OutlinedButton(onClick = { manualEditMode = true }) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Manual Edit")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (manualEditMode) manualEditMode = false else onDismiss()
                }
            ) {
                Text(if (manualEditMode) "Back to Search" else "Close")
            }
        }
    )
}

@Composable
fun SleepTimerModalDialog(
    sleepTimer: SleepTimerState,
    onSelectMinutes: (Int) -> Unit,
    onSelectEndOfTrack: () -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val photonColors = LocalPhotonColors.current
    var customMinutes by remember { mutableFloatStateOf(20f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = photonColors.goldBadge)
                Text("Sleep Timer", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (sleepTimer.isActive) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = photonColors.goldBadge.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, photonColors.goldBadge)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Active: ${sleepTimer.label}",
                                style = MaterialTheme.typography.titleSmall,
                                color = photonColors.goldBadge
                            )
                            TextButton(onClick = {
                                onCancelTimer()
                                onDismiss()
                            }) {
                                Text("Stop Timer")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(10, 15, 30, 60).forEach { mins ->
                        OutlinedButton(
                            onClick = {
                                onSelectMinutes(mins)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("${mins}m", maxLines = 1)
                        }
                    }
                }

                Column {
                    Text(
                        text = "Custom duration: ${customMinutes.roundToInt()} minutes",
                        style = MaterialTheme.typography.labelMedium,
                        color = photonColors.photonGlow
                    )
                    Slider(
                        value = customMinutes,
                        onValueChange = { customMinutes = it },
                        valueRange = 1f..120f
                    )
                }

                OutlinedButton(
                    onClick = {
                        onSelectEndOfTrack()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Pause at End of Current Track")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSelectMinutes(customMinutes.roundToInt())
                    onDismiss()
                }
            ) {
                Text("Start ${customMinutes.roundToInt()}m Timer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun PickPlaylistForTrackDialog(
    track: TrackEntity,
    playlists: List<PlaylistWithSummary>,
    onSelectPlaylist: (Long) -> Unit,
    onCreateAndAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                Text("Add \"${track.title}\"", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (playlists.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(playlists, key = { it.id }) { pl ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectPlaylist(pl.id)
                                        onDismiss()
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(pl.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${pl.trackCount} tracks",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Or create new playlist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (newName.isNotBlank()) {
                Button(onClick = {
                    onCreateAndAdd(newName)
                    onDismiss()
                }) {
                    Text("Create & Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
