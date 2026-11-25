package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.db.TrackEntity
import com.example.data.model.BatchMetadataPatch
import com.example.data.model.MetadataSuggestion
import com.example.ui.MetadataSearchUiState
import com.example.ui.components.AudioFormatBadge
import com.example.ui.components.PhotonAlbumArt
import com.example.ui.theme.LocalPhotonColors

enum class MetadataEditorMode(val label: String) {
    SINGLE("Single Track"),
    ALBUM("Whole Album"),
    BATCH("Batch Select")
}

private data class AlbumMetadataGroup(
    val albumName: String,
    val tracks: List<TrackEntity>,
    val primaryArtist: String,
    val artworkUri: String?
)

@Composable
internal fun MetadataEditorModeSelector(
    mode: MetadataEditorMode,
    onModeChange: (MetadataEditorMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = photonColors.glassSurface,
        border = BorderStroke(1.dp, photonColors.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetadataEditorMode.entries.forEach { option ->
                val selected = mode == option
                val icon = when (option) {
                    MetadataEditorMode.SINGLE -> Icons.Rounded.MusicNote
                    MetadataEditorMode.ALBUM -> Icons.Rounded.Album
                    MetadataEditorMode.BATCH -> Icons.Rounded.Checklist
                }
                FilterChip(
                    selected = selected,
                    onClick = { onModeChange(option) },
                    leadingIcon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("metadata_mode_${option.name.lowercase()}"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = photonColors.photonGlow.copy(alpha = 0.22f),
                        selectedLabelColor = photonColors.photonGlow,
                        selectedLeadingIconColor = photonColors.photonGlow
                    )
                )
            }
        }
    }
}

private fun commonStringOrNull(tracks: List<TrackEntity>, selector: (TrackEntity) -> String): String? {
    if (tracks.isEmpty()) return null
    val first = selector(tracks.first())
    return if (tracks.all { selector(it) == first }) first else null
}

private fun commonReleaseDateOrNull(tracks: List<TrackEntity>): String? {
    if (tracks.isEmpty()) return null
    fun resolve(t: TrackEntity) = t.releaseDate.ifBlank { t.releaseYear?.toString().orEmpty() }
    val first = resolve(tracks.first())
    return if (tracks.all { resolve(it) == first }) first else null
}

private fun commonArtworkOrNull(tracks: List<TrackEntity>): String? {
    if (tracks.isEmpty()) return null
    val first = tracks.first().artworkUri.orEmpty()
    return if (tracks.all { it.artworkUri.orEmpty() == first }) first else null
}

@Composable
internal fun AlbumAndBatchMetadataContent(
    mode: MetadataEditorMode,
    onModeChange: (MetadataEditorMode) -> Unit,
    allTracks: List<TrackEntity>,
    activeTrack: TrackEntity,
    searchState: MetadataSearchUiState,
    pickedCoverOverrideUri: String?,
    onClearPickedCoverOverride: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchWeb: (String, TrackEntity) -> Unit,
    onLaunchPhotoPickerForCover: (Long) -> Unit,
    onSaveBatchMetadata: (
        trackIds: List<Long>,
        patch: BatchMetadataPatch,
        contextLabel: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current

    // Build sorted album groups from library
    val albumGroups = remember(allTracks) {
        allTracks
            .groupBy { it.album.ifBlank { "Unknown Album" } }
            .entries
            .sortedBy { it.key.lowercase() }
            .map { (albumName, tracksInAlbum) ->
                val ordered = tracksInAlbum.sortedWith(
                    compareBy<TrackEntity> { it.trackNumber ?: Int.MAX_VALUE }
                        .thenBy { it.sortOrder }
                        .thenBy { it.title.lowercase() }
                )
                val distinctArtists = ordered.map { it.artist }.distinct()
                val primaryArtist = if (distinctArtists.size == 1) distinctArtists.first() else "Various Artists"
                AlbumMetadataGroup(
                    albumName = albumName,
                    tracks = ordered,
                    primaryArtist = primaryArtist,
                    artworkUri = ordered.firstNotNullOfOrNull { it.artworkUri?.takeIf(String::isNotBlank) }
                )
            }
    }

    var selectedAlbumName by remember(activeTrack.album) {
        mutableStateOf(activeTrack.album.ifBlank { "Unknown Album" })
    }
    val activeAlbumGroup = remember(albumGroups, selectedAlbumName) {
        albumGroups.firstOrNull { it.albumName == selectedAlbumName } ?: albumGroups.firstOrNull()
    }

    var albumPickerExpanded by remember { mutableStateOf(false) }

    // Batch multi-select state
    var batchSelectedIds by remember(activeTrack.id) {
        mutableStateOf(setOf(activeTrack.id))
    }
    var batchFilterQuery by remember { mutableStateOf("") }

    // Keep batchSelectedIds pruned if tracks are deleted
    LaunchedEffect(allTracks) {
        val validIds = allTracks.map { it.id }.toSet()
        batchSelectedIds = batchSelectedIds.filter { it in validIds }.toSet()
    }

    val targetTracks: List<TrackEntity> = remember(mode, activeAlbumGroup, allTracks, batchSelectedIds) {
        if (mode == MetadataEditorMode.ALBUM) {
            activeAlbumGroup?.tracks.orEmpty()
        } else {
            allTracks.filter { it.id in batchSelectedIds }
        }
    }

    val targetIdsSignature = remember(mode, targetTracks) {
        "${mode.name}:${targetTracks.joinToString(",") { "${it.id}:${it.artist}:${it.album}:${it.genre}:${it.publisher}:${it.releaseDate}:${it.composer}:${it.notes}:${it.artworkUri.orEmpty()}" }}"
    }

    // Shared / Mixed initial values derived from current target tracks
    val initialArtist = remember(targetIdsSignature) { commonStringOrNull(targetTracks) { it.artist } }
    val initialAlbum = remember(targetIdsSignature) { commonStringOrNull(targetTracks) { it.album } }
    val initialGenre = remember(targetIdsSignature) { commonStringOrNull(targetTracks) { it.genre } }
    val initialPublisher = remember(targetIdsSignature) { commonStringOrNull(targetTracks) { it.publisher } }
    val initialReleaseDate = remember(targetIdsSignature) { commonReleaseDateOrNull(targetTracks) }
    val initialComposer = remember(targetIdsSignature) { commonStringOrNull(targetTracks) { it.composer } }
    val initialNotes = remember(targetIdsSignature) { commonStringOrNull(targetTracks) { it.notes } }
    val initialArtwork = remember(targetIdsSignature) { commonArtworkOrNull(targetTracks) }

    // Editable fields + explicit dirty flags so untouched mixed fields are never overwritten
    var artist by remember(targetIdsSignature) { mutableStateOf(initialArtist.orEmpty()) }
    var artistModified by remember(targetIdsSignature) { mutableStateOf(false) }

    var album by remember(targetIdsSignature) { mutableStateOf(initialAlbum.orEmpty()) }
    var albumModified by remember(targetIdsSignature) { mutableStateOf(false) }

    var genre by remember(targetIdsSignature) { mutableStateOf(initialGenre.orEmpty()) }
    var genreModified by remember(targetIdsSignature) { mutableStateOf(false) }

    var publisher by remember(targetIdsSignature) { mutableStateOf(initialPublisher.orEmpty()) }
    var publisherModified by remember(targetIdsSignature) { mutableStateOf(false) }

    var releaseDate by remember(targetIdsSignature) { mutableStateOf(initialReleaseDate.orEmpty()) }
    var releaseDateModified by remember(targetIdsSignature) { mutableStateOf(false) }

    var composer by remember(targetIdsSignature) { mutableStateOf(initialComposer.orEmpty()) }
    var composerModified by remember(targetIdsSignature) { mutableStateOf(false) }

    var notes by remember(targetIdsSignature) { mutableStateOf(initialNotes.orEmpty()) }
    var notesModified by remember(targetIdsSignature) { mutableStateOf(false) }

    var artworkUri by remember(targetIdsSignature) { mutableStateOf(initialArtwork ?: targetTracks.firstOrNull()?.artworkUri.orEmpty()) }
    var artworkModified by remember(targetIdsSignature) { mutableStateOf(false) }
    var showCustomUrlInput by remember(targetIdsSignature) { mutableStateOf(false) }

    var autoNumberTracks by remember(targetIdsSignature) { mutableStateOf(false) }

    // Pre-fill web search box nicely when switching to Whole Album mode or changing album
    LaunchedEffect(mode, activeAlbumGroup?.albumName) {
        if (mode == MetadataEditorMode.ALBUM && activeAlbumGroup != null) {
            val q = buildString {
                if (activeAlbumGroup.primaryArtist != "Unknown Artist" && activeAlbumGroup.primaryArtist != "Various Artists") {
                    append(activeAlbumGroup.primaryArtist)
                    append(" ")
                }
                if (activeAlbumGroup.albumName != "Unknown Album") {
                    append(activeAlbumGroup.albumName)
                }
            }.trim()
            if (q.isNotBlank()) {
                onSearchQueryChange(q)
            }
        }
    }

    // Apply newly picked Photo Picker cover to the album/batch preview
    LaunchedEffect(pickedCoverOverrideUri) {
        if (!pickedCoverOverrideUri.isNullOrBlank()) {
            artworkUri = pickedCoverOverrideUri
            artworkModified = true
            onClearPickedCoverOverride()
        }
    }

    val currentPatch = BatchMetadataPatch(
        artist = if (artistModified) artist else null,
        album = if (albumModified) album else null,
        genre = if (genreModified) genre else null,
        publisher = if (publisherModified) publisher else null,
        releaseDate = if (releaseDateModified) releaseDate else null,
        composer = if (composerModified) composer else null,
        notes = if (notesModified) notes else null,
        artworkUri = if (artworkModified) artworkUri.ifBlank { null } else null,
        updateArtwork = artworkModified,
        autoNumberTracks = autoNumberTracks
    )

    val modifiedFieldCount = listOf(
        artistModified,
        albumModified,
        genreModified,
        publisherModified,
        releaseDateModified,
        composerModified,
        notesModified,
        artworkModified,
        autoNumberTracks
    ).count { it }

    val resetAllEdits = {
        artist = initialArtist.orEmpty()
        artistModified = false
        album = initialAlbum.orEmpty()
        albumModified = false
        genre = initialGenre.orEmpty()
        genreModified = false
        publisher = initialPublisher.orEmpty()
        publisherModified = false
        releaseDate = initialReleaseDate.orEmpty()
        releaseDateModified = false
        composer = initialComposer.orEmpty()
        composerModified = false
        notes = initialNotes.orEmpty()
        notesModified = false
        artworkUri = initialArtwork ?: targetTracks.firstOrNull()?.artworkUri.orEmpty()
        artworkModified = false
        autoNumberTracks = false
    }

    val fillSharedFromSuggestion: (MetadataSuggestion) -> Unit = { suggestion ->
        if (suggestion.artist.isNotBlank()) {
            artist = suggestion.artist
            artistModified = true
        }
        if (suggestion.album.isNotBlank()) {
            album = suggestion.album
            albumModified = true
        }
        if (suggestion.genre.isNotBlank()) {
            genre = suggestion.genre
            genreModified = true
        }
        if (suggestion.publisher.isNotBlank()) {
            publisher = suggestion.publisher
            publisherModified = true
        }
        val dateStr = suggestion.releaseDate.ifBlank { suggestion.releaseYear?.toString().orEmpty() }
        if (dateStr.isNotBlank()) {
            releaseDate = dateStr
            releaseDateModified = true
        }
        if (!suggestion.artworkUrl.isNullOrBlank()) {
            artworkUri = suggestion.artworkUrl
            artworkModified = true
        }
    }

    val webCoverCandidates = remember(searchState.results) {
        searchState.results
            .mapNotNull { s -> s.artworkUrl?.takeIf { it.isNotBlank() }?.let { url -> url to "${s.album} · ${s.source}" } }
            .distinctBy { it.first }
    }

    val coverAnchorTrackId = targetTracks.firstOrNull()?.id ?: activeTrack.id
    val contextLabel = if (mode == MetadataEditorMode.ALBUM) {
        "Album \"${(if (albumModified && album.isNotBlank()) album.trim() else activeAlbumGroup?.albumName) ?: "Album"}\""
    } else {
        "Batch Selection"
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 0. Mode Switcher (Single Track / Whole Album / Batch Select)
        item(key = "editor_mode_selector") {
            MetadataEditorModeSelector(
                mode = mode,
                onModeChange = onModeChange
            )
        }

        // 1. Scope Selector: Whole Album Picker OR Batch Multi-Select Track Checklist
        if (mode == MetadataEditorMode.ALBUM && activeAlbumGroup != null) {
            item(key = "editor_album_selector") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = photonColors.glassSurface,
                    border = BorderStroke(1.1.dp, photonColors.photonGlow.copy(alpha = 0.55f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "EDITING ENTIRE ALBUM (${albumGroups.size} IN LIBRARY)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = photonColors.photonGlow
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = photonColors.photonGlow.copy(alpha = 0.16f),
                                border = BorderStroke(0.75.dp, photonColors.photonGlow.copy(alpha = 0.55f))
                            ) {
                                Text(
                                    text = "${activeAlbumGroup.tracks.size} Tracks",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = photonColors.photonGlow,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                onClick = { albumPickerExpanded = true },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(0.9.dp, photonColors.cardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("metadata_editor_album_selector")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    PhotonAlbumArt(
                                        artworkUri = artworkUri.ifBlank { activeAlbumGroup.artworkUri },
                                        contentDescription = "Selected album cover",
                                        size = 48.dp,
                                        cornerRadius = 10.dp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = activeAlbumGroup.albumName,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${activeAlbumGroup.primaryArtist} · ${activeAlbumGroup.tracks.size} track(s)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        Icons.Rounded.ExpandMore,
                                        contentDescription = "Choose another album",
                                        tint = photonColors.photonGlow
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = albumPickerExpanded,
                                onDismissRequest = { albumPickerExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.86f)
                            ) {
                                albumGroups.forEach { group ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = group.albumName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${group.primaryArtist} · ${group.tracks.size} track(s)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        },
                                        trailingIcon = if (group.albumName == activeAlbumGroup.albumName) {
                                            {
                                                Icon(
                                                    Icons.Rounded.Check,
                                                    contentDescription = null,
                                                    tint = photonColors.photonGlow
                                                )
                                            }
                                        } else null,
                                        onClick = {
                                            albumPickerExpanded = false
                                            selectedAlbumName = group.albumName
                                        }
                                    )
                                }
                            }
                        }

                        // Compact preview of all tracks inside this album
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "TRACKS IN THIS ALBUM",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            activeAlbumGroup.tracks.forEachIndexed { idx, track ->
                                val numLabel = if (autoNumberTracks) {
                                    "#${idx + 1}"
                                } else {
                                    track.trackNumber?.let { "#$it" } ?: "•"
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = numLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (autoNumberTracks) photonColors.losslessBadge else photonColors.photonGlow,
                                        modifier = Modifier.width(26.dp)
                                    )
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = track.format,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (mode == MetadataEditorMode.BATCH) {
            item(key = "editor_batch_track_picker") {
                val trimmedFilter = batchFilterQuery.trim().lowercase()
                val visibleTracks = remember(allTracks, trimmedFilter) {
                    if (trimmedFilter.isEmpty()) allTracks
                    else allTracks.filter {
                        it.title.lowercase().contains(trimmedFilter) ||
                            it.artist.lowercase().contains(trimmedFilter) ||
                            it.album.lowercase().contains(trimmedFilter) ||
                            it.genre.lowercase().contains(trimmedFilter)
                    }
                }
                val firstSelectedTrack = remember(allTracks, batchSelectedIds) {
                    allTracks.firstOrNull { it.id in batchSelectedIds } ?: activeTrack
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = photonColors.glassSurface,
                    border = BorderStroke(1.1.dp, photonColors.photonGlow.copy(alpha = 0.55f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "SELECT SONGS FOR BATCH EDIT",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = photonColors.photonGlow
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (batchSelectedIds.isNotEmpty()) {
                                    photonColors.losslessBadge.copy(alpha = 0.18f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                border = BorderStroke(
                                    0.75.dp,
                                    if (batchSelectedIds.isNotEmpty()) photonColors.losslessBadge else photonColors.cardBorder
                                )
                            ) {
                                Text(
                                    text = "${batchSelectedIds.size} of ${allTracks.size} Selected",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (batchSelectedIds.isNotEmpty()) photonColors.losslessBadge else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Filter input
                        OutlinedTextField(
                            value = batchFilterQuery,
                            onValueChange = { batchFilterQuery = it },
                            placeholder = { Text("Filter songs by title, artist, album...") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = if (batchFilterQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = { batchFilterQuery = "" }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear filter", modifier = Modifier.size(16.dp))
                                    }
                                }
                            } else null,
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("batch_filter_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = photonColors.photonGlow,
                                unfocusedBorderColor = photonColors.cardBorder
                            )
                        )

                        // Quick selection chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { batchSelectedIds = visibleTracks.map { it.id }.toSet() },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("batch_select_all_btn")
                            ) {
                                Text("Select All (${visibleTracks.size})", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = {
                                    val albumKey = firstSelectedTrack.album
                                    batchSelectedIds = allTracks.filter { it.album == albumKey }.map { it.id }.toSet()
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("batch_select_same_album_btn")
                            ) {
                                Text("Same Album", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = {
                                    val artistKey = firstSelectedTrack.artist
                                    batchSelectedIds = allTracks.filter { it.artist == artistKey }.map { it.id }.toSet()
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("batch_select_same_artist_btn")
                            ) {
                                Text("Same Artist", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(
                                onClick = { batchSelectedIds = emptySet() },
                                enabled = batchSelectedIds.isNotEmpty(),
                                modifier = Modifier.testTag("batch_clear_selection_btn")
                            ) {
                                Text("Clear", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        // Scrollable track checklist
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 4.dp)
                        ) {
                            if (visibleTracks.isEmpty()) {
                                Text(
                                    text = "No tracks match \"$batchFilterQuery\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(14.dp)
                                )
                            } else {
                                visibleTracks.forEachIndexed { idx, track ->
                                    val checked = track.id in batchSelectedIds
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                batchSelectedIds = if (checked) {
                                                    batchSelectedIds - track.id
                                                } else {
                                                    batchSelectedIds + track.id
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                            .testTag("batch_track_row_${track.id}"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { isChecked ->
                                                batchSelectedIds = if (isChecked) {
                                                    batchSelectedIds + track.id
                                                } else {
                                                    batchSelectedIds - track.id
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = photonColors.photonGlow,
                                                checkmarkColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        )
                                        PhotonAlbumArt(
                                            artworkUri = track.artworkUri,
                                            contentDescription = "Cover for ${track.title}",
                                            size = 38.dp,
                                            cornerRadius = 8.dp
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = track.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${track.artist} · ${track.album}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        AudioFormatBadge(format = track.format)
                                    }
                                    if (idx < visibleTracks.lastIndex) {
                                        HorizontalDivider(
                                            color = photonColors.cardBorder.copy(alpha = 0.35f),
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Shared Cover Art Studio
        item(key = "editor_batch_cover_art_studio") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (artworkModified) photonColors.photonGlow.copy(alpha = 0.65f) else photonColors.cardBorder
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = null, tint = photonColors.photonGlow)
                            Column {
                                Text(
                                    text = if (mode == MetadataEditorMode.ALBUM) "Album Cover Art" else "Batch Cover Art",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = when {
                                        !artworkModified -> "Keeping existing artwork on each track"
                                        artworkUri.isBlank() -> "Will clear cover art on all ${targetTracks.size} track(s)"
                                        else -> "Will apply this cover to all ${targetTracks.size} track(s)"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (artworkModified) photonColors.photonGlow else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (artworkModified) {
                            TextButton(
                                onClick = {
                                    artworkUri = initialArtwork ?: targetTracks.firstOrNull()?.artworkUri.orEmpty()
                                    artworkModified = false
                                },
                                modifier = Modifier.testTag("batch_keep_cover_btn")
                            ) {
                                Text("Keep Existing", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        PhotonAlbumArt(
                            artworkUri = artworkUri.ifBlank { null },
                            contentDescription = "Shared cover art preview",
                            size = 104.dp,
                            cornerRadius = 18.dp,
                            showPhotonRing = artworkModified && artworkUri.isNotBlank()
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onLaunchPhotoPickerForCover(coverAnchorTrackId) },
                                enabled = targetTracks.isNotEmpty(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("batch_pick_device_cover_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Choose Photo", maxLines = 1)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showCustomUrlInput = !showCustomUrlInput },
                                    enabled = targetTracks.isNotEmpty(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("batch_toggle_cover_url_btn"),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("URL", style = MaterialTheme.typography.labelMedium)
                                }

                                OutlinedButton(
                                    onClick = {
                                        artworkUri = ""
                                        artworkModified = true
                                    },
                                    enabled = targetTracks.isNotEmpty(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("batch_clear_cover_btn"),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = showCustomUrlInput) {
                        OutlinedTextField(
                            value = artworkUri,
                            onValueChange = {
                                artworkUri = it
                                artworkModified = true
                            },
                            label = { Text("Cover Image URL or Local File URI") },
                            placeholder = { Text("https://... or file://...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("batch_cover_url_input")
                        )
                    }

                    if (webCoverCandidates.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "SELECT FROM VERIFIED WEB COVERS",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = photonColors.photonGlow
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                webCoverCandidates.forEachIndexed { idx, (url, caption) ->
                                    val isChosen = artworkModified && artworkUri == url
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(
                                                width = if (isChosen) 2.2.dp else 1.dp,
                                                color = if (isChosen) photonColors.photonGlow else photonColors.cardBorder,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                artworkUri = url
                                                artworkModified = true
                                            }
                                            .testTag("batch_web_cover_$idx")
                                    ) {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = caption,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (isChosen) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(photonColors.photonGlow),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Check,
                                                    contentDescription = "Selected cover",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Exact Web Lookup for Shared Album / Batch Tags
        item(key = "editor_batch_web_lookup") {
            val anchorTrack = targetTracks.firstOrNull() ?: activeTrack
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.dp, photonColors.photonGlow.copy(alpha = 0.45f))
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
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = photonColors.photonGlow)
                        Column {
                            Text(
                                text = if (mode == MetadataEditorMode.ALBUM) "Web Album Auto-Complete" else "Web Batch Auto-Complete",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Applies shared artist, album, genre, label, date & artwork (preserves individual song titles)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchState.query,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text("Artist & album name...") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { onSearchWeb(searchState.query, anchorTrack) }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("batch_web_search_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = photonColors.photonGlow,
                                unfocusedBorderColor = photonColors.cardBorder
                            )
                        )
                        Button(
                            onClick = { onSearchWeb(searchState.query, anchorTrack) },
                            enabled = !searchState.isLoading && searchState.query.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("batch_web_search_btn")
                        ) {
                            if (searchState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Rounded.Search, contentDescription = "Search web album metadata")
                            }
                        }
                    }

                    searchState.errorMessage?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    searchState.results.take(4).forEachIndexed { idx, suggestion ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                width = if (idx == 0) 1.2.dp else 0.75.dp,
                                color = if (idx == 0) photonColors.photonGlow else photonColors.cardBorder
                            ),
                            modifier = Modifier.testTag("batch_suggestion_card_$idx")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    PhotonAlbumArt(
                                        artworkUri = suggestion.artworkUrl,
                                        contentDescription = "Match artwork for ${suggestion.album}",
                                        size = 52.dp,
                                        cornerRadius = 10.dp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = suggestion.album.ifBlank { suggestion.title },
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = suggestion.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val details = listOfNotNull(
                                            suggestion.genre.takeIf { it.isNotBlank() },
                                            suggestion.publisher.takeIf { it.isNotBlank() },
                                            suggestion.releaseDate.takeIf { it.isNotBlank() }
                                        ).joinToString(" · ")
                                        if (details.isNotBlank()) {
                                            Text(
                                                text = details,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = photonColors.photonGlow,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Verified,
                                                contentDescription = null,
                                                tint = photonColors.goldBadge,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "${suggestion.source} · ${suggestion.confidencePercent}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = photonColors.goldBadge
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { fillSharedFromSuggestion(suggestion) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .testTag("batch_fill_suggestion_$idx"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Fill Shared", style = MaterialTheme.typography.labelMedium)
                                    }
                                    Button(
                                        onClick = {
                                            if (targetTracks.isEmpty()) return@Button
                                            val dateStr = suggestion.releaseDate.ifBlank { suggestion.releaseYear?.toString().orEmpty() }
                                            val patch = BatchMetadataPatch(
                                                artist = suggestion.artist.takeIf { it.isNotBlank() },
                                                album = suggestion.album.takeIf { it.isNotBlank() },
                                                genre = suggestion.genre.takeIf { it.isNotBlank() },
                                                publisher = suggestion.publisher.takeIf { it.isNotBlank() },
                                                releaseDate = dateStr.takeIf { it.isNotBlank() },
                                                artworkUri = suggestion.artworkUrl?.takeIf { it.isNotBlank() },
                                                updateArtwork = !suggestion.artworkUrl.isNullOrBlank()
                                            )
                                            if (mode == MetadataEditorMode.ALBUM && !patch.album.isNullOrBlank()) {
                                                selectedAlbumName = patch.album
                                            }
                                            onSaveBatchMetadata(
                                                targetTracks.map { it.id },
                                                patch,
                                                contextLabel
                                            )
                                        },
                                        enabled = targetTracks.isNotEmpty(),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .testTag("batch_apply_suggestion_$idx"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Apply (${targetTracks.size})", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Shared Metadata Tag Form (Album / Batch)
        item(key = "editor_batch_tag_form") {
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (mode == MetadataEditorMode.ALBUM) {
                                    "Shared Album Metadata"
                                } else {
                                    "Shared Batch Metadata"
                                },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Only modified fields are written — untouched tags stay intact per track",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = resetAllEdits,
                            enabled = modifiedFieldCount > 0,
                            modifier = Modifier.testTag("batch_revert_btn")
                        ) {
                            Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Revert")
                        }
                    }

                    BatchTagField(
                        value = artist,
                        onValueChange = {
                            artist = it
                            artistModified = true
                        },
                        label = "Artist",
                        isMixed = initialArtist == null && targetTracks.size > 1,
                        isModified = artistModified,
                        testTag = "batch_artist_field"
                    )

                    BatchTagField(
                        value = album,
                        onValueChange = {
                            album = it
                            albumModified = true
                        },
                        label = "Album",
                        isMixed = initialAlbum == null && targetTracks.size > 1,
                        isModified = albumModified,
                        testTag = "batch_album_field"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BatchTagField(
                            value = genre,
                            onValueChange = {
                                genre = it
                                genreModified = true
                            },
                            label = "Genre",
                            isMixed = initialGenre == null && targetTracks.size > 1,
                            isModified = genreModified,
                            testTag = "batch_genre_field",
                            modifier = Modifier.weight(1f)
                        )
                        BatchTagField(
                            value = releaseDate,
                            onValueChange = {
                                releaseDate = it
                                releaseDateModified = true
                            },
                            label = "Release Date",
                            placeholderOverride = "YYYY-MM-DD",
                            isMixed = initialReleaseDate == null && targetTracks.size > 1,
                            isModified = releaseDateModified,
                            testTag = "batch_release_date_field",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    BatchTagField(
                        value = publisher,
                        onValueChange = {
                            publisher = it
                            publisherModified = true
                        },
                        label = "Publisher / Label",
                        isMixed = initialPublisher == null && targetTracks.size > 1,
                        isModified = publisherModified,
                        testTag = "batch_publisher_field"
                    )

                    BatchTagField(
                        value = composer,
                        onValueChange = {
                            composer = it
                            composerModified = true
                        },
                        label = "Composer / Producer",
                        isMixed = initialComposer == null && targetTracks.size > 1,
                        isModified = composerModified,
                        testTag = "batch_composer_field"
                    )

                    BatchTagField(
                        value = notes,
                        onValueChange = {
                            notes = it
                            notesModified = true
                        },
                        label = "Notes / Edition Details",
                        placeholderOverride = "e.g. 24-bit Remaster, Deluxe Edition...",
                        isMixed = initialNotes == null && targetTracks.size > 1,
                        isModified = notesModified,
                        singleLine = false,
                        testTag = "batch_notes_field"
                    )

                    // Auto-number tracks sequentially toggle
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                        border = BorderStroke(
                            0.8.dp,
                            if (autoNumberTracks) photonColors.losslessBadge.copy(alpha = 0.65f) else photonColors.cardBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FormatListNumbered,
                                contentDescription = null,
                                tint = if (autoNumberTracks) photonColors.losslessBadge else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Number Tracks (1..${targetTracks.size.coerceAtLeast(1)})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Assigns sequential track numbers in current list order",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoNumberTracks,
                                onCheckedChange = { autoNumberTracks = it },
                                modifier = Modifier.testTag("batch_auto_number_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = photonColors.losslessBadge
                                )
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (targetTracks.isEmpty() || !currentPatch.hasAnyChanges) return@Button
                            if (mode == MetadataEditorMode.ALBUM && albumModified && album.isNotBlank()) {
                                selectedAlbumName = album.trim()
                            }
                            onSaveBatchMetadata(
                                targetTracks.map { it.id },
                                currentPatch,
                                contextLabel
                            )
                        },
                        enabled = targetTracks.isNotEmpty() && currentPatch.hasAnyChanges,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("editor_batch_save_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = photonColors.photonGlow,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                targetTracks.isEmpty() -> "Select at Least 1 Track"
                                !currentPatch.hasAnyChanges -> "Edit Any Field to Apply"
                                mode == MetadataEditorMode.ALBUM -> "Apply to All ${targetTracks.size} Album Tracks"
                                else -> "Apply to ${targetTracks.size} Selected Tracks"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        // 5. On-Device Privacy Assurance Banner
        item(key = "editor_batch_privacy_footer") {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = photonColors.losslessBadge.copy(alpha = 0.1f),
                border = BorderStroke(0.8.dp, photonColors.losslessBadge.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = photonColors.losslessBadge,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "100% On-Device Storage · Batch & album edits update your local Room database atomically without uploading personal files.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchTagField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isMixed: Boolean,
    isModified: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    placeholderOverride: String? = null,
    singleLine: Boolean = true
) {
    val photonColors = LocalPhotonColors.current
    val resolvedLabel = if (isModified) "$label · Edited" else label
    val placeholderText = when {
        isMixed && !isModified -> "Mixed values (leave untouched to keep)"
        placeholderOverride != null -> placeholderOverride
        else -> null
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(resolvedLabel) },
        placeholder = placeholderText?.let { text -> { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        maxLines = if (singleLine) 1 else 4,
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = photonColors.photonGlow,
            unfocusedBorderColor = if (isModified) photonColors.photonGlow.copy(alpha = 0.7f) else photonColors.cardBorder
        )
    )
}
