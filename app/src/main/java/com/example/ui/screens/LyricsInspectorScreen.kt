package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.TrackEntity
import com.example.data.library.ParsedLyrics
import com.example.data.model.MetadataSuggestion
import com.example.player.PlaybackUiState
import com.example.ui.MetadataSearchUiState
import com.example.ui.components.AudioFormatBadge
import com.example.ui.components.PhotonAlbumArt
import com.example.ui.components.formatDurationMs
import com.example.ui.components.formatSampleRate
import com.example.ui.theme.LocalPhotonColors
import kotlinx.coroutines.launch

@Composable
fun LyricsInspectorScreen(
    track: TrackEntity?,
    playbackState: PlaybackUiState,
    editorSearchState: MetadataSearchUiState,
    pickedCoverOverrideUri: String?,
    onClearPickedCoverOverride: () -> Unit,
    onLoadLyrics: suspend (TrackEntity) -> ParsedLyrics,
    onFetchWebLyrics: suspend (TrackEntity) -> Result<ParsedLyrics>,
    onSaveCustomLyrics: suspend (Long, String) -> ParsedLyrics,
    onSeekTo: (Long) -> Unit,
    onUpdateWebSearchQuery: (String) -> Unit,
    onSearchWebMetadata: (String, TrackEntity) -> Unit,
    onPickCoverPhoto: (Long) -> Unit,
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
        artworkUri: String?
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val scope = rememberCoroutineScope()
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Synced Lyrics, 1 = Inspector & Tags

    if (track == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Select or play a track to view synchronized lyrics and technical file details.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }

    var parsedLyrics by remember(track.id) { mutableStateOf(ParsedLyrics()) }
    var isLoadingLyrics by remember(track.id) { mutableStateOf(true) }
    var isFetchingOnline by remember(track.id) { mutableStateOf(false) }
    var showEditLyricsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(track.id, track.lyricsLrc) {
        isLoadingLyrics = true
        parsedLyrics = onLoadLyrics(track)
        isLoadingLyrics = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("lyrics_inspector_screen")
    ) {
        // Track summary header
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = photonColors.glassSurface,
            border = BorderStroke(1.dp, photonColors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PhotonAlbumArt(track.artworkUri, track.title, size = 54.dp, cornerRadius = 12.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${track.artist} · ${track.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AudioFormatBadge(track.format)
                        Text(
                            text = "${formatSampleRate(track.sampleRateHz)} · ${if (track.bitrateKbps > 0) "${track.bitrateKbps} kbps" else "PCM"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = photonColors.photonGlow
                        )
                    }
                }
            }
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            SegmentedButton(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                icon = { Icon(Icons.Rounded.Lyrics, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("Synced Lyrics") },
                modifier = Modifier.testTag("lyrics_tab_btn")
            )
            SegmentedButton(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                icon = { Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("File & Tag Inspector") },
                modifier = Modifier.testTag("inspector_tab_btn")
            )
        }

        if (activeTab == 0) {
            SyncedLyricsPane(
                track = track,
                isActivePlayingTrack = playbackState.currentTrack?.id == track.id,
                positionMs = playbackState.positionMs,
                parsedLyrics = parsedLyrics,
                isLoading = isLoadingLyrics,
                isFetchingOnline = isFetchingOnline,
                onFetchOnline = {
                    scope.launch {
                        isFetchingOnline = true
                        onFetchWebLyrics(track).onSuccess { parsedLyrics = it }
                        isFetchingOnline = false
                    }
                },
                onEditLyrics = { showEditLyricsDialog = true },
                onSeekTo = onSeekTo,
                modifier = Modifier.weight(1f)
            )
        } else {
            TechnicalInspectorAndTagEditorPane(
                track = track,
                playbackState = playbackState,
                editorSearchState = editorSearchState,
                pickedCoverOverrideUri = pickedCoverOverrideUri,
                onClearPickedCoverOverride = onClearPickedCoverOverride,
                onUpdateWebSearchQuery = onUpdateWebSearchQuery,
                onSearchWebMetadata = { q -> onSearchWebMetadata(q, track) },
                onPickCoverPhoto = { onPickCoverPhoto(track.id) },
                onSaveMetadata = onSaveMetadata,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showEditLyricsDialog) {
        var rawInput by remember(parsedLyrics.rawText) { mutableStateOf(parsedLyrics.rawText) }
        AlertDialog(
            onDismissRequest = { showEditLyricsDialog = false },
            title = { Text("Edit / Paste .LRC Lyrics") },
            text = {
                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = it },
                    placeholder = { Text("[00:12.40] Paste time-synced .lrc or static lyrics...") },
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            parsedLyrics = onSaveCustomLyrics(track.id, rawInput)
                            showEditLyricsDialog = false
                        }
                    }
                ) { Text("Save to Room") }
            },
            dismissButton = {
                TextButton(onClick = { showEditLyricsDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SyncedLyricsPane(
    track: TrackEntity,
    isActivePlayingTrack: Boolean,
    positionMs: Long,
    parsedLyrics: ParsedLyrics,
    isLoading: Boolean,
    isFetchingOnline: Boolean,
    onFetchOnline: () -> Unit,
    onEditLyrics: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val listState = rememberLazyListState()

    val activeLineIndex = remember(parsedLyrics.syncedLines, positionMs, isActivePlayingTrack) {
        if (!isActivePlayingTrack || parsedLyrics.syncedLines.isEmpty()) -1
        else parsedLyrics.syncedLines.indexOfLast { it.timestampMs <= positionMs + 180L }
    }

    LaunchedEffect(activeLineIndex) {
        if (activeLineIndex >= 0) {
            listState.animateScrollToItem((activeLineIndex - 2).coerceAtLeast(0))
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = parsedLyrics.sourceLabel.ifBlank { "No cached .lrc lyrics yet" },
                style = MaterialTheme.typography.labelSmall,
                color = photonColors.photonGlow
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onEditLyrics,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = onFetchOnline,
                    enabled = !isFetchingOnline,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = photonColors.photonGlow,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("fetch_web_lyrics_btn")
                ) {
                    if (isFetchingOnline) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                    }
                    Spacer(Modifier.width(5.dp))
                    Text("Fetch via Web Metadata", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = photonColors.photonGlow)
                }
            }
            parsedLyrics.hasSynced -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(parsedLyrics.syncedLines, key = { i, line -> "lrc_${i}_${line.timestampMs}" }) { idx, line ->
                        val isActive = idx == activeLineIndex
                        val textColor by animateColorAsState(
                            targetValue = if (isActive) photonColors.photonGlow else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "lyric_line_color"
                        )
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isActive) photonColors.photonGlow.copy(alpha = 0.14f) else photonColors.glassSurface.copy(alpha = 0.45f),
                            border = if (isActive) BorderStroke(1.dp, photonColors.photonGlow.copy(alpha = 0.65f)) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSeekTo(line.timestampMs) }
                                .testTag("synced_lyric_line_$idx")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatDurationMs(line.timestampMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isActive) photonColors.photonGlow else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                )
                                Text(
                                    text = line.text,
                                    style = if (isActive) {
                                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    } else {
                                        MaterialTheme.typography.bodyMedium
                                    },
                                    color = textColor,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
            parsedLyrics.hasAny -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(parsedLyrics.plainLines) { _, line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = photonColors.glassSurface,
                        border = BorderStroke(1.dp, photonColors.cardBorder),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.Lyrics, contentDescription = null, tint = photonColors.photonGlow, modifier = Modifier.size(36.dp))
                            Text(
                                text = "No Synced Lyrics Found Locally",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Place a sidecar .lrc next to \"${track.fileName}\", paste lyrics manually, or fetch time-synced lyrics via Web Metadata.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onFetchOnline,
                                enabled = !isFetchingOnline,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = photonColors.photonGlow,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Fetch via Web Metadata")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TechnicalInspectorAndTagEditorPane(
    track: TrackEntity,
    playbackState: PlaybackUiState,
    editorSearchState: MetadataSearchUiState,
    pickedCoverOverrideUri: String?,
    onClearPickedCoverOverride: () -> Unit,
    onUpdateWebSearchQuery: (String) -> Unit,
    onSearchWebMetadata: (String) -> Unit,
    onPickCoverPhoto: () -> Unit,
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
        artworkUri: String?
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album) }
    var genre by remember(track.id) { mutableStateOf(track.genre) }
    var publisher by remember(track.id) { mutableStateOf(track.publisher) }
    var releaseDate by remember(track.id) { mutableStateOf(track.releaseDate) }
    var trackNum by remember(track.id) { mutableStateOf(track.trackNumber?.toString().orEmpty()) }
    var composer by remember(track.id) { mutableStateOf(track.composer) }
    var notes by remember(track.id) { mutableStateOf(track.notes) }
    var artworkUri by remember(track.id) { mutableStateOf(track.artworkUri) }

    LaunchedEffect(pickedCoverOverrideUri) {
        if (!pickedCoverOverrideUri.isNullOrBlank()) {
            artworkUri = pickedCoverOverrideUri
            onClearPickedCoverOverride()
        }
    }

    val signal = playbackState.signalInfo
    val isActive = playbackState.currentTrack?.id == track.id

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Technical File Inspector Card
        item(key = "tech_inspector") {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.dp, photonColors.photonGlow.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "TECHNICAL FILE INSPECTOR",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = photonColors.photonGlow
                    )
                    TechSpecLine("File Name", track.fileName)
                    TechSpecLine("Path / URI", track.uri)
                    TechSpecLine("Codec / Format", if (isActive) signal.codecDisplayName else track.format)
                    TechSpecLine("Bitrate", if (track.bitrateKbps > 0) "${track.bitrateKbps} kbps" else "Lossless / Uncompressed PCM")
                    TechSpecLine("Sample Rate", formatSampleRate(if (isActive) signal.sourceSampleRateHz else track.sampleRateHz))
                    TechSpecLine("Channels", if (isActive) signal.channelLayoutLabel else "Stereo 2.0")
                    TechSpecLine(
                        "Loudness (LUFS / RG)",
                        track.integratedLufs?.let { String.format("%.1f LUFS (RG %+.1f dB)", it, track.replayGainTrackDb ?: 0f) }
                            ?: "Not scanned yet"
                    )
                }
            }
        }

        // Web Metadata Auto-Complete Engine
        item(key = "web_autocomplete_card") {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.dp, photonColors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = photonColors.photonGlow, modifier = Modifier.size(18.dp))
                        Text("Web Metadata Auto-Complete", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editorSearchState.query,
                            onValueChange = onUpdateWebSearchQuery,
                            placeholder = { Text("Artist + Track title...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onSearchWebMetadata(editorSearchState.query) },
                            enabled = !editorSearchState.isLoading,
                            modifier = Modifier.testTag("inspector_web_lookup_btn")
                        ) {
                            if (editorSearchState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.Search, contentDescription = "Lookup")
                            }
                        }
                    }

                    editorSearchState.errorMessage?.let { err ->
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    editorSearchState.results.take(4).forEach { suggestion: MetadataSuggestion ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    title = suggestion.title
                                    artist = suggestion.artist
                                    album = suggestion.album
                                    genre = suggestion.genre
                                    publisher = suggestion.publisher
                                    releaseDate = suggestion.releaseDate
                                    suggestion.trackNumber?.let { trackNum = it.toString() }
                                    if (!suggestion.artworkUrl.isNullOrBlank()) {
                                        artworkUri = suggestion.artworkUrl
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PhotonAlbumArt(suggestion.artworkUrl, suggestion.title, size = 40.dp, cornerRadius = 8.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(suggestion.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${suggestion.artist} · ${suggestion.album}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text("${suggestion.confidencePercent}%", style = MaterialTheme.typography.labelSmall, color = photonColors.photonGlow)
                            }
                        }
                    }
                }
            }
        }

        // Inline Tag Editor Form
        item(key = "inline_tag_editor") {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.dp, photonColors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("INLINE METADATA TAG EDITOR", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = photonColors.photonGlow)
                        OutlinedButton(onClick = onPickCoverPhoto) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cover")
                        }
                    }

                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text("Artist") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = album, onValueChange = { album = it }, label = { Text("Album") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = genre, onValueChange = { genre = it }, label = { Text("Genre") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = trackNum, onValueChange = { trackNum = it.filter(Char::isDigit) }, label = { Text("Track #") }, singleLine = true, modifier = Modifier.width(100.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = publisher, onValueChange = { publisher = it }, label = { Text("Publisher") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = releaseDate, onValueChange = { releaseDate = it }, label = { Text("Year / Date") }, singleLine = true, modifier = Modifier.width(120.dp))
                    }
                    OutlinedTextField(value = composer, onValueChange = { composer = it }, label = { Text("Composer") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, maxLines = 3, modifier = Modifier.fillMaxWidth())

                    Button(
                        onClick = {
                            onSaveMetadata(
                                track.id,
                                title,
                                artist,
                                album,
                                trackNum.toIntOrNull(),
                                genre,
                                publisher,
                                releaseDate,
                                composer,
                                notes,
                                artworkUri
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = photonColors.photonGlow,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inspector_save_tags_btn")
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Metadata Tags to Room")
                    }
                }
            }
        }
    }
}

@Composable
private fun TechSpecLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(116.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
