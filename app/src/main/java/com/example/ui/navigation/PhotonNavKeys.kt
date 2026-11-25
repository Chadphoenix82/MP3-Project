package com.example.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Type-safe Jetpack Navigation 3 destination hierarchy for Photon Audio Deck.
 */
@Serializable
sealed interface PhotonNavKey : NavKey {
    @Serializable
    data object HomeKey : PhotonNavKey

    @Serializable
    data object LibraryKey : PhotonNavKey

    @Serializable
    data object PlaylistsKey : PhotonNavKey

    @Serializable
    data object SearchKey : PhotonNavKey

    @Serializable
    data class AlbumDetailKey(
        val albumId: String,
        val preferredArtist: String? = null
    ) : PhotonNavKey

    @Serializable
    data class ArtistDetailKey(
        val artistId: String
    ) : PhotonNavKey

    @Serializable
    data object QueueKey : PhotonNavKey

    @Serializable
    data class LyricsInspectorKey(
        val trackId: Long
    ) : PhotonNavKey

    @Serializable
    data object SettingsKey : PhotonNavKey
}

object PhotonDeepLinks {
    const val EXTRA_TARGET = "com.example.photon.DEEP_LINK_TARGET"
    const val TARGET_NOW_PLAYING = "now_playing"
    const val TARGET_LIBRARY = "library"
    const val URI_NOW_PLAYING = "photonaudio://deck/now-playing"
    const val URI_LIBRARY = "photonaudio://deck/library"
}
