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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
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

@Composable
fun MetadataEditorScreen(
    allTracks: List<TrackEntity>,
    selectedTrackId: Long?,
    currentlyPlayingTrackId: Long?,
    searchState: MetadataSearchUiState,
    pickedCoverOverrideUri: String?,
    onClearPickedCoverOverride: () -> Unit,
    onSelectTrack: (TrackEntity) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchWeb: (String, TrackEntity) -> Unit,
    onLaunchPhotoPickerForCover: (Long) -> Unit,
    onSaveMetadata: (
        trackId: Long,
        title: String,
        artist: String,
        album: String,
        trackNumber: Int?,
        genre: String,
        publisher: String,
        releaseDate: String,
        composer: String,
        notes: String,
        artworkUri: String?,
        replaceArtwork: Boolean
    ) -> Unit,
    onSaveBatchMetadata: (
        trackIds: List<Long>,
        patch: BatchMetadataPatch,
        contextLabel: String
    ) -> Unit,
    initialEditorMode: MetadataEditorMode = MetadataEditorMode.SINGLE,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current

    // Resolve active track (explicit selection -> currently playing -> first library track)
    val activeTrack = remember(allTracks, selectedTrackId, currentlyPlayingTrackId) {
        allTracks.firstOrNull { it.id == selectedTrackId }
            ?: allTracks.firstOrNull { it.id == currentlyPlayingTrackId }
            ?: allTracks.firstOrNull()
    }

    LaunchedEffect(activeTrack?.id) {
        if (activeTrack != null && selectedTrackId != activeTrack.id) {
            onSelectTrack(activeTrack)
        }
    }

    if (activeTrack == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.dp, photonColors.cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.EditNote,
                        contentDescription = null,
                        tint = photonColors.photonGlow,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "No Tracks to Edit Yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Import MP3, FLAC, or WAV audio files in Library first, then open Metadata Editor to inspect and customize every tag and album cover locally on your device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    var editorMode by remember(initialEditorMode) { mutableStateOf(initialEditorMode) }

    if (editorMode != MetadataEditorMode.SINGLE) {
        AlbumAndBatchMetadataContent(
            mode = editorMode,
            onModeChange = { editorMode = it },
            allTracks = allTracks,
            activeTrack = activeTrack,
            searchState = searchState,
            pickedCoverOverrideUri = pickedCoverOverrideUri,
            onClearPickedCoverOverride = onClearPickedCoverOverride,
            onSearchQueryChange = onSearchQueryChange,
            onSearchWeb = onSearchWeb,
            onLaunchPhotoPickerForCover = onLaunchPhotoPickerForCover,
            onSaveBatchMetadata = onSaveBatchMetadata,
            modifier = modifier
        )
        return
    }

    // Editable fields keyed to the currently selected track
    var title by remember(activeTrack.id, activeTrack.title) { mutableStateOf(activeTrack.title) }
    var artist by remember(activeTrack.id, activeTrack.artist) { mutableStateOf(activeTrack.artist) }
    var album by remember(activeTrack.id, activeTrack.album) { mutableStateOf(activeTrack.album) }
    var trackNumberText by remember(activeTrack.id, activeTrack.trackNumber) {
        mutableStateOf(activeTrack.trackNumber?.toString().orEmpty())
    }
    var genre by remember(activeTrack.id, activeTrack.genre) { mutableStateOf(activeTrack.genre) }
    var publisher by remember(activeTrack.id, activeTrack.publisher) { mutableStateOf(activeTrack.publisher) }
    var releaseDate by remember(activeTrack.id, activeTrack.releaseDate, activeTrack.releaseYear) {
        mutableStateOf(activeTrack.releaseDate.ifBlank { activeTrack.releaseYear?.toString().orEmpty() })
    }
    var composer by remember(activeTrack.id, activeTrack.composer) { mutableStateOf(activeTrack.composer) }
    var notes by remember(activeTrack.id, activeTrack.notes) { mutableStateOf(activeTrack.notes) }
    var artworkUri by remember(activeTrack.id, activeTrack.artworkUri) { mutableStateOf(activeTrack.artworkUri.orEmpty()) }
    var showCustomUrlInput by remember(activeTrack.id) { mutableStateOf(false) }
    var trackPickerExpanded by remember { mutableStateOf(false) }

    // Apply newly picked local Photo Picker cover immediately to the preview
    LaunchedEffect(pickedCoverOverrideUri) {
        if (!pickedCoverOverrideUri.isNullOrBlank()) {
            artworkUri = pickedCoverOverrideUri
            onClearPickedCoverOverride()
        }
    }

    // Distinct cover art candidates discovered from web lookup results
    val webCoverCandidates = remember(searchState.results) {
        searchState.results
            .mapNotNull { s -> s.artworkUrl?.takeIf { it.isNotBlank() }?.let { url -> url to "${s.album} · ${s.source}" } }
            .distinctBy { it.first }
    }

    val populateFromSuggestion: (MetadataSuggestion) -> Unit = { suggestion ->
        title = suggestion.title
        artist = suggestion.artist
        album = suggestion.album
        if (suggestion.trackNumber != null) {
            trackNumberText = suggestion.trackNumber.toString()
        }
        genre = suggestion.genre
        publisher = suggestion.publisher
        releaseDate = suggestion.releaseDate.ifBlank { suggestion.releaseYear?.toString().orEmpty() }
        if (!suggestion.artworkUrl.isNullOrBlank()) {
            artworkUri = suggestion.artworkUrl
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 0. Mode Switcher (Single Track / Whole Album / Batch Select)
        item(key = "editor_mode_selector") {
            MetadataEditorModeSelector(
                mode = editorMode,
                onModeChange = { editorMode = it }
            )
        }

        // 1. Track Selector Card
        item(key = "editor_track_selector") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.1.dp, photonColors.photonGlow.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "EDITING TRACK (${allTracks.size} IN LIBRARY)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = photonColors.photonGlow
                        )
                        AudioFormatBadge(format = activeTrack.format)
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            onClick = { trackPickerExpanded = true },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(0.9.dp, photonColors.cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("metadata_editor_track_selector")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PhotonAlbumArt(
                                    artworkUri = artworkUri.ifBlank { null },
                                    contentDescription = "Selected track cover",
                                    size = 44.dp,
                                    cornerRadius = 10.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = activeTrack.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${activeTrack.artist} · ${activeTrack.fileName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    Icons.Rounded.ExpandMore,
                                    contentDescription = "Choose another track",
                                    tint = photonColors.photonGlow
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = trackPickerExpanded,
                            onDismissRequest = { trackPickerExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.86f)
                        ) {
                            allTracks.forEach { candidate ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = candidate.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${candidate.artist} · ${candidate.format}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    },
                                    trailingIcon = if (candidate.id == activeTrack.id) {
                                        {
                                            Icon(
                                                Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = photonColors.photonGlow
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        trackPickerExpanded = false
                                        onSelectTrack(candidate)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Album Cover Art Studio
        item(key = "editor_cover_art_studio") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.dp, photonColors.cardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Image, contentDescription = null, tint = photonColors.photonGlow)
                        Column {
                            Text(
                                text = "Album Cover Art Studio",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Pick from your device photos, choose verified web artwork, or paste a URL",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        PhotonAlbumArt(
                            artworkUri = artworkUri.ifBlank { null },
                            contentDescription = "Album cover art preview",
                            size = 112.dp,
                            cornerRadius = 18.dp,
                            showPhotonRing = artworkUri.isNotBlank()
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onLaunchPhotoPickerForCover(activeTrack.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("editor_pick_device_cover_btn"),
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
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("editor_toggle_cover_url_btn"),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("URL", style = MaterialTheme.typography.labelMedium)
                                }

                                OutlinedButton(
                                    onClick = { artworkUri = "" },
                                    enabled = artworkUri.isNotBlank(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("editor_clear_cover_btn"),
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
                            onValueChange = { artworkUri = it },
                            label = { Text("Cover Image URL or Local File URI") },
                            placeholder = { Text("https://... or file://...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_cover_url_input")
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
                                    val isChosen = artworkUri == url
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(
                                                width = if (isChosen) 2.2.dp else 1.dp,
                                                color = if (isChosen) photonColors.photonGlow else photonColors.cardBorder,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { artworkUri = url }
                                            .testTag("editor_web_cover_$idx")
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
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(photonColors.photonGlow),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Check,
                                                    contentDescription = "Selected cover",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(14.dp)
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

        // 3. High-Precision Web Metadata Lookup
        item(key = "editor_web_lookup") {
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
                                text = "Exact Web Metadata Auto-Complete",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Cross-verifies official releases & catalog tags for exact metadata",
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
                            placeholder = { Text("Artist & song title...") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { onSearchWeb(searchState.query, activeTrack) }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("editor_web_search_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = photonColors.photonGlow,
                                unfocusedBorderColor = photonColors.cardBorder
                            )
                        )
                        Button(
                            onClick = { onSearchWeb(searchState.query, activeTrack) },
                            enabled = !searchState.isLoading && searchState.query.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("editor_web_search_btn")
                        ) {
                            if (searchState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Rounded.Search, contentDescription = "Search exact web metadata")
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

                    searchState.results.take(6).forEachIndexed { idx, suggestion ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                width = if (idx == 0) 1.2.dp else 0.75.dp,
                                color = if (idx == 0) photonColors.photonGlow else photonColors.cardBorder
                            ),
                            modifier = Modifier.testTag("editor_suggestion_card_$idx")
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
                                        contentDescription = "Match artwork for ${suggestion.title}",
                                        size = 56.dp,
                                        cornerRadius = 10.dp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = suggestion.title,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = photonColors.losslessBadge.copy(alpha = 0.18f),
                                                border = BorderStroke(0.7.dp, photonColors.losslessBadge)
                                            ) {
                                                Text(
                                                    text = "${suggestion.confidencePercent}% Match",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = photonColors.losslessBadge,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${suggestion.artist} · ${suggestion.album}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val detailParts = buildList {
                                            if (suggestion.trackNumber != null) add("Track #${suggestion.trackNumber}")
                                            if (suggestion.genre.isNotBlank()) add(suggestion.genre)
                                            if (suggestion.publisher.isNotBlank()) add(suggestion.publisher)
                                            if (suggestion.releaseDate.isNotBlank()) add(suggestion.releaseDate)
                                        }
                                        Text(
                                            text = detailParts.joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = photonColors.photonGlow,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
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
                                                text = suggestion.source,
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
                                        onClick = { populateFromSuggestion(suggestion) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .testTag("editor_fill_suggestion_$idx"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Fill & Review", style = MaterialTheme.typography.labelMedium)
                                    }
                                    Button(
                                        onClick = {
                                            populateFromSuggestion(suggestion)
                                            onSaveMetadata(
                                                activeTrack.id,
                                                suggestion.title,
                                                suggestion.artist,
                                                suggestion.album,
                                                suggestion.trackNumber,
                                                suggestion.genre,
                                                suggestion.publisher,
                                                suggestion.releaseDate,
                                                composer,
                                                notes,
                                                suggestion.artworkUrl ?: artworkUri.ifBlank { null },
                                                true
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .testTag("editor_apply_suggestion_$idx"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Apply & Save", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Complete Editable Tag Form
        item(key = "editor_tag_form") {
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
                        Column {
                            Text(
                                text = "Edit Every Metadata Detail",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Correct any field manually — saved immediately to device database",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = {
                                title = activeTrack.title
                                artist = activeTrack.artist
                                album = activeTrack.album
                                trackNumberText = activeTrack.trackNumber?.toString().orEmpty()
                                genre = activeTrack.genre
                                publisher = activeTrack.publisher
                                releaseDate = activeTrack.releaseDate.ifBlank { activeTrack.releaseYear?.toString().orEmpty() }
                                composer = activeTrack.composer
                                notes = activeTrack.notes
                                artworkUri = activeTrack.artworkUri.orEmpty()
                            },
                            modifier = Modifier.testTag("editor_revert_btn")
                        ) {
                            Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Revert")
                        }
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Song Title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_title_field")
                    )

                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        label = { Text("Artist") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_artist_field")
                    )

                    OutlinedTextField(
                        value = album,
                        onValueChange = { album = it },
                        label = { Text("Album") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_album_field")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = trackNumberText,
                            onValueChange = { input -> trackNumberText = input.filter { it.isDigit() }.take(3) },
                            label = { Text("Track #") },
                            placeholder = { Text("1") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(0.36f)
                                .testTag("editor_track_number_field")
                        )
                        OutlinedTextField(
                            value = releaseDate,
                            onValueChange = { releaseDate = it },
                            label = { Text("Release Date") },
                            placeholder = { Text("YYYY-MM-DD") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.64f)
                                .testTag("editor_release_date_field")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = genre,
                            onValueChange = { genre = it },
                            label = { Text("Genre") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("editor_genre_field")
                        )
                        OutlinedTextField(
                            value = publisher,
                            onValueChange = { publisher = it },
                            label = { Text("Publisher / Label") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("editor_publisher_field")
                        )
                    }

                    OutlinedTextField(
                        value = composer,
                        onValueChange = { composer = it },
                        label = { Text("Composer / Producer") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_composer_field")
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes / Edition Details") },
                        placeholder = { Text("e.g. 24-bit Remaster, Deluxe Edition...") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_notes_field")
                    )

                    Button(
                        onClick = {
                            onSaveMetadata(
                                activeTrack.id,
                                title,
                                artist,
                                album,
                                trackNumberText.toIntOrNull(),
                                genre,
                                publisher,
                                releaseDate,
                                composer,
                                notes,
                                artworkUri.ifBlank { null },
                                true
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("editor_save_metadata_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = photonColors.photonGlow,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save Metadata & Cover to Device",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        // 5. On-Device Privacy Assurance Banner
        item(key = "editor_privacy_footer") {
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
                        text = "100% On-Device Storage · No personal data is collected or uploaded. All edited tags and album covers remain strictly on your device.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
