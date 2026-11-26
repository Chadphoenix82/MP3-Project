package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.db.TrackEntity
import com.example.ui.theme.LocalPhotonColors

/**
 * Universal reusable track row used across Library, Search, Album/Artist Detail,
 * Playlists, Home, and Live Queue.
 *
 * Tap plays the track; press-and-hold triggers haptic feedback and opens
 * [TrackActionBottomSheet]. For reorderable lists (Playlists and Queue), attach
 * drag-to-reorder gestures exclusively to [dragHandleModifier] so long-pressing
 * the row body still opens the Track Action Sheet consistently everywhere.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRowItem(
    track: TrackEntity,
    onClick: () -> Unit,
    onOpenTrackMenu: (TrackEntity) -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    isDragging: Boolean = false,
    subtitleOverride: String? = null,
    playCount: Int = 0,
    trackNumberBadge: Int? = null,
    showFormatBadge: Boolean = true,
    showFavoriteButton: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    showOverflowButton: Boolean = true,
    dragHandleModifier: Modifier? = null,
    extraTrailingContent: (@Composable RowScope.() -> Unit)? = null,
    rowTestTag: String = "track_row_${track.id}"
) {
    val photonColors = LocalPhotonColors.current
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = when {
            isDragging -> photonColors.photonGlow.copy(alpha = 0.22f)
            isCurrent -> photonColors.photonGlow.copy(alpha = 0.14f)
            else -> photonColors.glassSurface
        },
        border = BorderStroke(
            width = if (isCurrent || isDragging) 1.2.dp else 0.8.dp,
            color = if (isCurrent || isDragging) photonColors.photonGlow else photonColors.cardBorder.copy(alpha = 0.65f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenTrackMenu(track)
                }
            )
            .testTag(rowTestTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (trackNumberBadge != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCurrent) photonColors.photonGlow.copy(alpha = 0.22f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCurrent && isPlaying) {
                        PhotonEqualizerBars(isPlaying = true)
                    } else {
                        Text(
                            text = String.format("%02d", trackNumberBadge),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isCurrent) photonColors.photonGlow else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                PhotonAlbumArt(
                    artworkUri = track.artworkUri,
                    contentDescription = track.title,
                    size = 46.dp,
                    cornerRadius = 10.dp,
                    isPlaying = isCurrent && isPlaying
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold
                    ),
                    color = if (isCurrent) photonColors.photonGlow else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (showFormatBadge) {
                        AudioFormatBadge(format = track.format)
                    }
                    Text(
                        text = subtitleOverride ?: buildString {
                            append(track.artist)
                            if (track.durationMs > 0L) {
                                append(" · ")
                                append(formatDurationMs(track.durationMs))
                            }
                            if (playCount > 0) {
                                append(" · ${playCount}x")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isCurrent && trackNumberBadge == null) {
                PhotonEqualizerBars(isPlaying = isPlaying)
            }

            if (showFavoriteButton && onToggleFavorite != null) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("track_row_fav_${track.id}")
                ) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (track.isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (track.isFavorite) Color(0xFFFF5C8A) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            extraTrailingContent?.invoke(this)

            if (showOverflowButton) {
                IconButton(
                    onClick = { onOpenTrackMenu(track) },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("track_row_overflow_${track.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.track_more_actions_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (dragHandleModifier != null) {
                Box(
                    modifier = dragHandleModifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .testTag("track_row_drag_handle_${track.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = stringResource(R.string.track_drag_handle_cd),
                        tint = if (isDragging) photonColors.photonGlow else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Formats a compact technical audio badge such as "FLAC • 96kHz" or "WAV • 44.1kHz".
 */
fun formatTechnicalAudioBadge(track: TrackEntity): String {
    val codec = track.format.trim().ifBlank { "AUDIO" }.uppercase()
    val khz = if (track.sampleRateHz > 0) {
        val k = track.sampleRateHz / 1000.0
        if (k % 1.0 == 0.0) "${k.toInt()}kHz" else String.format("%.1fkHz", k)
    } else {
        null
    }
    val bitrate = if (track.bitrateKbps > 0 && khz == null) "${track.bitrateKbps}kbps" else null
    return listOfNotNull(codec, khz ?: bitrate).joinToString(" • ")
}

/**
 * Universal Press-and-Hold Track Action Sheet (ModalBottomSheet) providing
 * header metadata + favorite toggle, Queue Controls, Direct Navigation,
 * and Device Utility Actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionBottomSheet(
    track: TrackEntity,
    onDismiss: () -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onAddToQueue: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
    onGoToAlbum: (albumName: String, artistName: String?) -> Unit,
    onGoToArtist: (artistName: String) -> Unit,
    onOpenLyricsAndTagEditor: (TrackEntity) -> Unit,
    onShareAudioFile: (TrackEntity) -> Unit,
    onSetAsRingtone: (TrackEntity) -> Unit,
    onRequestDeleteFromDevice: (TrackEntity) -> Unit
) {
    val photonColors = LocalPhotonColors.current
    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val techBadge = formatTechnicalAudioBadge(track)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = photonColors.backdropTop,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 22.dp)
                .testTag("track_action_bottom_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: 56dp rounded album thumbnail, title, artist/album subtitle, format badge, inline Heart
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.dp, photonColors.photonGlow.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PhotonAlbumArt(
                        artworkUri = track.artworkUri,
                        contentDescription = track.title,
                        size = 56.dp,
                        cornerRadius = 14.dp
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = listOf(track.artist, track.album)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = photonColors.photonGlow.copy(alpha = 0.16f),
                            border = BorderStroke(0.8.dp, photonColors.photonGlow.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = techBadge,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = photonColors.photonGlow,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            onToggleFavorite(track)
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("track_action_favorite_btn")
                    ) {
                        Icon(
                            imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (track.isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (track.isFavorite) Color(0xFFFF5C8A) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // Section 1: Queue Controls
            ActionSheetSectionLabel("QUEUE CONTROLS")
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(0.8.dp, photonColors.cardBorder.copy(alpha = 0.65f))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TrackActionSheetRow(
                        icon = Icons.Rounded.QueuePlayNext,
                        title = stringResource(R.string.track_action_play_next),
                        subtitle = "Insert directly after the active track",
                        accent = photonColors.photonGlow,
                        testTag = "track_action_play_next",
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onPlayNext(track)
                            onDismiss()
                        }
                    )
                    HorizontalDivider(color = photonColors.cardBorder.copy(alpha = 0.35f))
                    TrackActionSheetRow(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        title = stringResource(R.string.track_action_add_to_queue),
                        subtitle = "Append to the end of the live queue",
                        accent = photonColors.photonGlow,
                        testTag = "track_action_add_to_queue",
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onAddToQueue(track)
                            onDismiss()
                        }
                    )
                    HorizontalDivider(color = photonColors.cardBorder.copy(alpha = 0.35f))
                    TrackActionSheetRow(
                        icon = Icons.Rounded.PlaylistAdd,
                        title = stringResource(R.string.track_action_add_to_playlist),
                        subtitle = "Save into a custom playlist",
                        accent = photonColors.nebulaAccent,
                        testTag = "track_action_add_to_playlist",
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onDismiss()
                            onAddToPlaylist(track)
                        }
                    )
                }
            }

            // Section 2: Direct Navigation Actions
            ActionSheetSectionLabel("NAVIGATION & INSPECTOR")
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(0.8.dp, photonColors.cardBorder.copy(alpha = 0.65f))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TrackActionSheetRow(
                        icon = Icons.Rounded.Album,
                        title = stringResource(R.string.track_action_go_to_album),
                        subtitle = track.album.ifBlank { "Unknown Album" },
                        accent = photonColors.photonGlow,
                        testTag = "track_action_go_to_album",
                        onClick = {
                            onDismiss()
                            onGoToAlbum(track.album.ifBlank { "Unknown Album" }, track.artist)
                        }
                    )
                    HorizontalDivider(color = photonColors.cardBorder.copy(alpha = 0.35f))
                    TrackActionSheetRow(
                        icon = Icons.Rounded.Person,
                        title = stringResource(R.string.track_action_go_to_artist),
                        subtitle = track.artist.ifBlank { "Unknown Artist" },
                        accent = photonColors.photonGlow,
                        testTag = "track_action_go_to_artist",
                        onClick = {
                            onDismiss()
                            onGoToArtist(track.artist.ifBlank { "Unknown Artist" })
                        }
                    )
                    HorizontalDivider(color = photonColors.cardBorder.copy(alpha = 0.35f))
                    TrackActionSheetRow(
                        icon = Icons.Rounded.EditNote,
                        title = stringResource(R.string.track_action_lyrics_tag_editor),
                        subtitle = "Synced .lrc lyrics, audio specs & web tag editor",
                        accent = photonColors.losslessBadge,
                        testTag = "track_action_lyrics_inspector",
                        onClick = {
                            onDismiss()
                            onOpenLyricsAndTagEditor(track)
                        }
                    )
                }
            }

            // Section 3: Utility Actions
            ActionSheetSectionLabel("FILE & DEVICE")
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(0.8.dp, photonColors.cardBorder.copy(alpha = 0.65f))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TrackActionSheetRow(
                        icon = Icons.Rounded.Share,
                        title = stringResource(R.string.track_action_share),
                        subtitle = "Send audio file via system share sheet",
                        accent = photonColors.photonGlow,
                        testTag = "track_action_share",
                        onClick = {
                            onDismiss()
                            onShareAudioFile(track)
                        }
                    )
                    HorizontalDivider(color = photonColors.cardBorder.copy(alpha = 0.35f))
                    TrackActionSheetRow(
                        icon = Icons.Rounded.NotificationsActive,
                        title = stringResource(R.string.track_action_set_ringtone),
                        subtitle = "Assign as default phone ringtone",
                        accent = photonColors.nebulaAccent,
                        testTag = "track_action_set_ringtone",
                        onClick = {
                            onDismiss()
                            onSetAsRingtone(track)
                        }
                    )
                    HorizontalDivider(color = photonColors.cardBorder.copy(alpha = 0.35f))
                    TrackActionSheetRow(
                        icon = Icons.Rounded.DeleteForever,
                        title = stringResource(R.string.track_action_delete_device),
                        subtitle = "Permanently remove audio file & library entry",
                        accent = MaterialTheme.colorScheme.error,
                        isDestructive = true,
                        testTag = "track_action_delete_device",
                        onClick = {
                            onDismiss()
                            onRequestDeleteFromDevice(track)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionSheetSectionLabel(text: String) {
    val photonColors = LocalPhotonColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = photonColors.photonGlow,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )
}

@Composable
private fun TrackActionSheetRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    testTag: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
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

@Composable
fun DeleteTrackConfirmationDialog(
    track: TrackEntity,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.track_delete_dialog_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Are you sure you want to delete \"${track.title}\" by ${track.artist}?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "This removes the track from Photon Audio and attempts to delete ${track.fileName.ifBlank { "the underlying audio file" }} from device storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("confirm_delete_track_btn")
            ) {
                Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.track_delete_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_track_btn")
            ) {
                Text(stringResource(R.string.track_delete_dialog_cancel))
            }
        }
    )
}
