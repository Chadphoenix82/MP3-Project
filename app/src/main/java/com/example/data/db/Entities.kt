package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [Index(value = ["uri"], unique = true)]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uri: String,
    val fileName: String,
    val title: String,
    val artist: String = "Unknown Artist",
    val album: String = "Unknown Album",
    val genre: String = "Unknown Genre",
    val publisher: String = "Independent",
    val releaseDate: String = "",
    val releaseYear: Int? = null,
    val trackNumber: Int? = null,
    val composer: String = "",
    val notes: String = "",
    val format: String = "MP3",
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 44100,
    val durationMs: Long = 0L,
    val artworkUri: String? = null,
    val sortOrder: Int = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val folderUri: String? = null,
    val metadataEnriched: Boolean = false,
    val isFavorite: Boolean = false,
    val customGainDb: Float = 0f,
    val preferredEqPreset: String? = null
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index("trackId")]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: Long,
    val position: Int
)

@Entity(tableName = "watched_folders")
data class WatchedFolderEntity(
    @PrimaryKey val uri: String,
    val displayName: String,
    val lastScannedAt: Long = 0L,
    val trackCount: Int = 0,
    val isAppInternal: Boolean = false
)

data class PlaylistWithSummary(
    val id: Long,
    val name: String,
    val description: String,
    val createdAt: Long,
    val trackCount: Int,
    val coverUri: String?
)

@Entity(
    tableName = "listening_history",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trackId"), Index("playedAt")]
)
data class ListeningHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val trackId: Long,
    val genre: String,
    val artist: String,
    val playedAt: Long = System.currentTimeMillis()
)

data class TrackPlayStat(
    val trackId: Long,
    val playCount: Int,
    val lastPlayedAt: Long
)

data class GenrePlayStat(
    val genre: String,
    val playCount: Int
)

data class ArtistPlayStat(
    val artist: String,
    val playCount: Int
)

@Entity(tableName = "excluded_folders")
data class ExcludedFolderEntity(
    @PrimaryKey val identifier: String,
    val displayName: String,
    val isUriTree: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)


