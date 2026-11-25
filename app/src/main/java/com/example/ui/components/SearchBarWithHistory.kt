package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.LocalPhotonColors

/**
 * Top library search bar with persistent recent search history and live prefix/substring
 * query suggestions as the user types.
 */
@Composable
fun SearchBarWithHistory(
    searchQuery: String,
    searchHistory: List<String>,
    onSearchQueryChange: (String) -> Unit,
    onCommitSearchQuery: (String) -> Unit,
    onRemoveHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val photonColors = LocalPhotonColors.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var isFocused by remember { mutableStateOf(false) }
    var panelExpanded by remember { mutableStateOf(false) }

    val trimmedQuery = searchQuery.trim()

    val suggestions = remember(searchHistory, trimmedQuery) {
        if (trimmedQuery.isEmpty()) {
            searchHistory
        } else {
            val lower = trimmedQuery.lowercase()
            val prefixMatches = mutableListOf<String>()
            val containsMatches = mutableListOf<String>()
            searchHistory.forEach { entry ->
                val entryLower = entry.lowercase()
                if (entryLower == lower) return@forEach
                when {
                    entryLower.startsWith(lower) -> prefixMatches.add(entry)
                    entryLower.contains(lower) -> containsMatches.add(entry)
                }
            }
            prefixMatches + containsMatches
        }
    }

    val showSuggestionsPanel = isFocused && panelExpanded && suggestions.isNotEmpty()

    BackHandler(enabled = showSuggestionsPanel) {
        panelExpanded = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { next ->
                onSearchQueryChange(next)
                panelExpanded = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    isFocused = state.isFocused
                    if (state.isFocused) {
                        panelExpanded = true
                    } else {
                        panelExpanded = false
                    }
                }
                .testTag("library_search_input"),
            placeholder = {
                Text(
                    text = stringResource(R.string.search_library_placeholder),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search library",
                    tint = photonColors.photonGlow
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            onSearchQueryChange("")
                            panelExpanded = true
                        },
                        modifier = Modifier.testTag("clear_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.search_clear_query_cd)
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (trimmedQuery.isNotEmpty()) {
                        onCommitSearchQuery(trimmedQuery)
                    }
                    panelExpanded = false
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            ),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = photonColors.photonGlow,
                unfocusedBorderColor = photonColors.cardBorder,
                focusedContainerColor = photonColors.glassSurface,
                unfocusedContainerColor = photonColors.glassSurface
            )
        )

        AnimatedVisibility(
            visible = showSuggestionsPanel,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .testTag("search_history_panel"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = photonColors.photonGlow.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = photonColors.photonGlow,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = stringResource(
                                    if (trimmedQuery.isEmpty()) R.string.search_recent_header
                                    else R.string.search_suggestions_header
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = photonColors.photonGlow
                            )
                        }

                        if (trimmedQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_clear_history),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onClearHistory()
                                        panelExpanded = false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("clear_search_history_button")
                            )
                        }
                    }

                    HorizontalDivider(
                        color = photonColors.cardBorder.copy(alpha = 0.45f),
                        thickness = 0.75.dp
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 264.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        suggestions.forEachIndexed { index, query ->
                            SearchHistorySuggestionRow(
                                query = query,
                                highlight = trimmedQuery,
                                index = index,
                                onSelect = {
                                    onSearchQueryChange(query)
                                    onCommitSearchQuery(query)
                                    panelExpanded = false
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                },
                                onFillInput = {
                                    onSearchQueryChange(query)
                                    panelExpanded = true
                                },
                                onRemove = {
                                    onRemoveHistoryItem(query)
                                }
                            )
                            if (index < suggestions.lastIndex) {
                                HorizontalDivider(
                                    color = photonColors.cardBorder.copy(alpha = 0.25f),
                                    thickness = 0.6.dp,
                                    modifier = Modifier.padding(horizontal = 14.dp)
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
private fun SearchHistorySuggestionRow(
    query: String,
    highlight: String,
    index: Int,
    onSelect: () -> Unit,
    onFillInput: () -> Unit,
    onRemove: () -> Unit
) {
    val photonColors = LocalPhotonColors.current
    val styledText = remember(query, highlight, photonColors.photonGlow) {
        buildHighlightedQuery(
            text = query,
            highlight = highlight,
            highlightStyle = SpanStyle(
                color = photonColors.photonGlow,
                fontWeight = FontWeight.Bold
            )
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onSelect)
            .padding(start = 14.dp, end = 4.dp)
            .testTag("search_history_item_$index"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = styledText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onFillInput,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("search_history_fill_$index")
        ) {
            Icon(
                imageVector = Icons.Rounded.NorthWest,
                contentDescription = stringResource(R.string.search_fill_query_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("search_history_remove_$index")
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.search_remove_history_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

private fun buildHighlightedQuery(
    text: String,
    highlight: String,
    highlightStyle: SpanStyle
): AnnotatedString {
    if (highlight.isBlank()) return AnnotatedString(text)
    val matchStart = text.indexOf(highlight, ignoreCase = true)
    if (matchStart < 0) return AnnotatedString(text)
    val matchEnd = (matchStart + highlight.length).coerceAtMost(text.length)
    return buildAnnotatedString {
        if (matchStart > 0) {
            append(text.substring(0, matchStart))
        }
        withStyle(highlightStyle) {
            append(text.substring(matchStart, matchEnd))
        }
        if (matchEnd < text.length) {
            append(text.substring(matchEnd))
        }
    }
}
