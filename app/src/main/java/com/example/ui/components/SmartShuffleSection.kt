package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.db.TrackEntity
import com.example.data.library.GenreTrackCount
import com.example.data.library.SmartShufflePreview
import com.example.ui.theme.LocalPhotonColors
import kotlin.math.roundToInt

private val SuggestedStarterGenres = listOf(
    "Electronic",
    "Synthwave",
    "Lo-Fi",
    "Ambient",
    "Rock",
    "Hip-Hop",
    "Jazz",
    "Indie"
)

@Composable
fun SmartShuffleDashboardCard(
    preview: SmartShufflePreview,
    availableGenres: List<GenreTrackCount>,
    preferredGenres: Set<String>,
    recentWeight: Float,
    hasTracks: Boolean,
    onLaunchSmartShuffle: () -> Unit,
    onToggleGenre: (String) -> Unit,
    onOpenPreferences: () -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val view = LocalView.current

    val recentPercent = (recentWeight * 100f).roundToInt()
    val genrePercent = 100 - recentPercent

    // Build ordered inline quick chips: active preferred genres first, then top library genres
    val quickGenreChips = remember(availableGenres, preferredGenres) {
        val libraryCountMap = availableGenres.associateBy { it.genre.lowercase() }
        val ordered = linkedMapOf<String, Pair<String, Int?>>()
        preferredGenres.sortedBy { it.lowercase() }.forEach { pref ->
            val key = pref.lowercase()
            ordered[key] = pref to libraryCountMap[key]?.trackCount
        }
        availableGenres.take(8).forEach { entry ->
            val key = entry.genre.lowercase()
            if (key !in ordered) {
                ordered[key] = entry.genre to entry.trackCount
            }
        }
        if (ordered.isEmpty()) {
            SuggestedStarterGenres.take(5).forEach { starter ->
                ordered[starter.lowercase()] = starter to null
            }
        }
        ordered.values.toList()
    }

    val statusText = remember(preview, preferredGenres, hasTracks) {
        when {
            !hasTracks -> "Import audio to activate dynamic blending"
            preferredGenres.isEmpty() ->
                "${preview.recentlyImportedCount} recent imports · tap genres below to focus mix"
            preview.overlapCount > 0 ->
                "${preview.recentlyImportedCount} recent · ${preview.genreMatchedCount} genre · ${preview.overlapCount} exact overlap"
            else ->
                "${preview.recentlyImportedCount} recent · ${preview.genreMatchedCount} genre matches"
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = photonColors.glassSurface,
        border = BorderStroke(
            width = 1.1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    photonColors.photonGlow.copy(alpha = 0.65f),
                    photonColors.nebulaAccent.copy(alpha = 0.45f)
                )
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_smart_shuffle_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                photonColors.photonGlow.copy(alpha = 0.16f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.12f, 0f),
                            radius = size.width * 0.72f
                        )
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                photonColors.nebulaAccent.copy(alpha = 0.14f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.92f, size.height),
                            radius = size.width * 0.65f
                        )
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header row with badge, title, and Tune button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        photonColors.photonGlow.copy(alpha = 0.28f),
                                        photonColors.nebulaAccent.copy(alpha = 0.24f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = photonColors.photonGlow,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.smart_shuffle_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = photonColors.nebulaAccent.copy(alpha = 0.18f),
                                border = BorderStroke(0.8.dp, photonColors.nebulaAccent.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "$recentPercent/$genrePercent",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = photonColors.nebulaAccent,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onOpenPreferences()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("smart_shuffle_tune_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = stringResource(R.string.smart_shuffle_tune_cd),
                            tint = photonColors.photonGlow
                        )
                    }
                }

                // Inline Quick Genre Preferences Selector
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("smart_shuffle_genre_chips_row")
                ) {
                    items(quickGenreChips, key = { it.first.lowercase() }) { (genre, count) ->
                        val isSelected = preferredGenres.any { it.equals(genre, ignoreCase = true) }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onToggleGenre(genre)
                            },
                            label = {
                                Text(
                                    text = if (count != null) "$genre · $count" else genre,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                null
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = photonColors.photonGlow.copy(alpha = 0.22f),
                                selectedLabelColor = photonColors.photonGlow,
                                selectedLeadingIconColor = photonColors.photonGlow,
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = photonColors.cardBorder.copy(alpha = 0.65f),
                                selectedBorderColor = photonColors.photonGlow.copy(alpha = 0.75f)
                            ),
                            modifier = Modifier.testTag("smart_shuffle_chip_${genre.lowercase().replace(" ", "_")}")
                        )
                    }

                    item(key = "tune_more_genres") {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = photonColors.nebulaAccent.copy(alpha = 0.14f),
                            border = BorderStroke(0.9.dp, photonColors.nebulaAccent.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onOpenPreferences()
                                }
                                .testTag("smart_shuffle_more_genres_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.smart_shuffle_tune_cd),
                                    tint = photonColors.nebulaAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Customize",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = photonColors.nebulaAccent
                                )
                            }
                        }
                    }
                }

                // Bottom Launch Row: Stacked Artwork Preview + Primary CTA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (preview.previewTracks.isNotEmpty()) {
                        SmartShuffleArtStack(
                            tracks = preview.previewTracks.take(3),
                            borderColor = photonColors.cardBorder
                        )
                    }

                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            onLaunchSmartShuffle()
                        },
                        enabled = hasTracks,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = photonColors.photonGlow,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("smart_shuffle_launch_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        val ctaText = if (hasTracks && preview.totalEstimatedTracks > 0) {
                            "${stringResource(R.string.smart_shuffle_launch_cta)} (${preview.totalEstimatedTracks})"
                        } else {
                            stringResource(R.string.smart_shuffle_launch_cta)
                        }
                        Text(
                            text = ctaText,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartShuffleArtStack(
    tracks: List<TrackEntity>,
    borderColor: Color
) {
    val visible = tracks.take(3)
    val totalWidth = (42 + (visible.size - 1).coerceAtLeast(0) * 18).dp
    Box(
        modifier = Modifier
            .width(totalWidth)
            .height(44.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        visible.forEachIndexed { index, track ->
            Surface(
                shape = RoundedCornerShape(11.dp),
                border = BorderStroke(1.2.dp, borderColor),
                modifier = Modifier
                    .offset(x = (index * 18).dp)
                    .size(42.dp)
            ) {
                PhotonAlbumArt(
                    artworkUri = track.artworkUri,
                    contentDescription = track.title,
                    size = 42.dp,
                    cornerRadius = 11.dp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SmartShufflePreferencesSheet(
    preview: SmartShufflePreview,
    availableGenres: List<GenreTrackCount>,
    preferredGenres: Set<String>,
    recentWeight: Float,
    hasTracks: Boolean,
    onToggleGenre: (String) -> Unit,
    onAddCustomGenre: (String) -> Unit,
    onClearGenres: () -> Unit,
    onUpdateRecentWeight: (Float) -> Unit,
    onLaunchSmartShuffle: () -> Unit,
    onDismiss: () -> Unit
) {
    val photonColors = LocalPhotonColors.current
    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customGenreInput by remember { mutableStateOf("") }

    val recentPercent = (recentWeight * 100f).roundToInt()
    val genrePercent = 100 - recentPercent

    // Merge library genres with any custom saved genres and starter suggestions when library has no tags
    val allSelectableGenres = remember(availableGenres, preferredGenres) {
        val byKey = linkedMapOf<String, Pair<String, Int?>>()
        availableGenres.forEach { g ->
            byKey[g.genre.lowercase()] = g.genre to g.trackCount
        }
        preferredGenres.forEach { pref ->
            val key = pref.lowercase()
            if (key !in byKey) {
                byKey[key] = pref to null
            }
        }
        if (availableGenres.isEmpty()) {
            SuggestedStarterGenres.forEach { starter ->
                val key = starter.lowercase()
                if (key !in byKey) {
                    byKey[key] = starter to null
                }
            }
        }
        byKey.values.toList()
    }

    val submitCustomGenre: () -> Unit = {
        val trimmed = customGenreInput.trim()
        if (trimmed.isNotEmpty()) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onAddCustomGenre(trimmed)
            customGenreInput = ""
        }
    }

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
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .testTag("smart_shuffle_preferences_sheet"),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Sheet header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.smart_shuffle_sheet_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.smart_shuffle_sheet_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close preferences",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Live Blend Telemetry Summary Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = photonColors.glassSurface,
                border = BorderStroke(1.dp, photonColors.photonGlow.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MixStatItem(
                        value = "${preview.totalEstimatedTracks}",
                        label = "Queue Size",
                        accent = photonColors.photonGlow
                    )
                    MixStatItem(
                        value = "${preview.recentlyImportedCount}",
                        label = "Recent",
                        accent = photonColors.nebulaAccent
                    )
                    MixStatItem(
                        value = "${preview.genreMatchedCount}",
                        label = "Genre Match",
                        accent = photonColors.losslessBadge
                    )
                    if (preview.overlapCount > 0) {
                        MixStatItem(
                            value = "${preview.overlapCount}",
                            label = "Both",
                            accent = photonColors.photonGlow
                        )
                    }
                }
            }

            // Mix Balance Slider (Genre Focus <-> Recent Imports)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.smart_shuffle_balance_label),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$recentPercent% Recent · $genrePercent% Genre",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = photonColors.photonGlow
                    )
                }
                Slider(
                    value = recentWeight,
                    onValueChange = onUpdateRecentWeight,
                    valueRange = 0.20f..0.85f,
                    colors = SliderDefaults.colors(
                        thumbColor = photonColors.photonGlow,
                        activeTrackColor = photonColors.photonGlow,
                        inactiveTrackColor = photonColors.nebulaAccent.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("smart_shuffle_balance_slider")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.smart_shuffle_balance_genre_end),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.smart_shuffle_balance_recent_end),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Preferred Genres Multi-Select Grid
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = photonColors.photonGlow,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.smart_shuffle_genres_header),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    AnimatedVisibility(
                        visible = preferredGenres.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        TextButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onClearGenres()
                            },
                            modifier = Modifier.testTag("smart_shuffle_clear_genres_btn")
                        ) {
                            Text(
                                text = stringResource(R.string.smart_shuffle_clear_genres),
                                color = photonColors.nebulaAccent
                            )
                        }
                    }
                }

                if (preferredGenres.isEmpty()) {
                    Text(
                        text = stringResource(R.string.smart_shuffle_empty_genres_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    allSelectableGenres.forEach { (genre, count) ->
                        val isSelected = preferredGenres.any { it.equals(genre, ignoreCase = true) }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onToggleGenre(genre)
                            },
                            label = {
                                val badge = when {
                                    count != null -> "$genre ($count)"
                                    isSelected -> "$genre · Custom"
                                    else -> genre
                                }
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                null
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = photonColors.photonGlow.copy(alpha = 0.22f),
                                selectedLabelColor = photonColors.photonGlow,
                                selectedLeadingIconColor = photonColors.photonGlow,
                                containerColor = photonColors.glassSurface,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = photonColors.cardBorder.copy(alpha = 0.65f),
                                selectedBorderColor = photonColors.photonGlow.copy(alpha = 0.8f)
                            )
                        )
                    }
                }
            }

            // Custom Genre Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customGenreInput,
                    onValueChange = { customGenreInput = it.take(36) },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.smart_shuffle_custom_genre_hint),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitCustomGenre() }),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("smart_shuffle_custom_genre_input")
                )
                Button(
                    onClick = submitCustomGenre,
                    enabled = customGenreInput.trim().isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = photonColors.nebulaAccent,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("smart_shuffle_add_custom_genre_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.smart_shuffle_add_genre),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.smart_shuffle_add_genre))
                }
            }

            // Primary Launch Action
            Button(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onLaunchSmartShuffle()
                    onDismiss()
                },
                enabled = hasTracks,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = photonColors.photonGlow,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("smart_shuffle_sheet_launch_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (hasTracks) {
                        "Start Smart Shuffle · ${preview.totalEstimatedTracks} Tracks"
                    } else {
                        "Import Audio to Start Smart Shuffle"
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun MixStatItem(
    value: String,
    label: String,
    accent: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = accent
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
