package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY sortOrder ASC, dateAdded DESC")
    fun observeAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY sortOrder ASC, dateAdded DESC")
    suspend fun getAllTracksOnce(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE uri = :uri LIMIT 1")
    suspend fun getTrackByUri(uri: String): TrackEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM tracks")
    suspend fun getMaxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(track: TrackEntity): Long

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Query("UPDATE tracks SET sortOrder = :sortOrder WHERE id = :trackId")
    suspend fun updateSortOrder(trackId: Long, sortOrder: Int)

    @Transaction
    suspend fun updateSortOrders(orderedTrackIds: List<Long>) {
        orderedTrackIds.forEachIndexed { index, trackId ->
            updateSortOrder(trackId, index)
        }
    }

    @Query(
        """
        UPDATE tracks SET
            title = :title,
            artist = :artist,
            album = :album,
            genre = :genre,
            publisher = :publisher,
            releaseDate = :releaseDate,
            releaseYear = :releaseYear,
            trackNumber = :trackNumber,
            composer = :composer,
            notes = :notes,
            artworkUri = CASE WHEN :replaceArtwork = 1 THEN :artworkUri ELSE COALESCE(:artworkUri, artworkUri) END,
            metadataEnriched = 1
        WHERE id = :trackId
        """
    )
    suspend fun updateMetadata(
        trackId: Long,
        title: String,
        artist: String,
        album: String,
        genre: String,
        publisher: String,
        releaseDate: String,
        releaseYear: Int?,
        trackNumber: Int? = null,
        composer: String = "",
        notes: String = "",
        artworkUri: String?,
        replaceArtwork: Boolean = false
    )

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :trackId")
    suspend fun setFavorite(trackId: Long, isFavorite: Boolean)

    @Query("UPDATE tracks SET customGainDb = :customGainDb, preferredEqPreset = :preferredEqPreset WHERE id = :trackId")
    suspend fun updateTrackSoundProfile(trackId: Long, customGainDb: Float, preferredEqPreset: String?)

    @Query(
        """
        UPDATE tracks SET
            replayGainTrackDb = :trackGainDb,
            replayGainAlbumDb = COALESCE(:albumGainDb, replayGainAlbumDb),
            replayGainTrackPeak = :trackPeak,
            replayGainAlbumPeak = COALESCE(:albumPeak, replayGainAlbumPeak),
            integratedLufs = :integratedLufs,
            replayGainSource = :source
        WHERE id = :trackId
        """
    )
    suspend fun updateTrackLoudness(
        trackId: Long,
        trackGainDb: Float?,
        albumGainDb: Float?,
        trackPeak: Float?,
        albumPeak: Float?,
        integratedLufs: Float?,
        source: String
    )

    @Query("UPDATE tracks SET replayGainAlbumDb = :albumGainDb, replayGainAlbumPeak = :albumPeak WHERE album = :albumName")
    suspend fun updateAlbumLoudness(albumName: String, albumGainDb: Float, albumPeak: Float)

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrack(trackId: Long)

    @Query("SELECT COUNT(*) FROM tracks WHERE folderUri = :folderUri")
    suspend fun countTracksInFolder(folderUri: String): Int
}

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.id, p.name, p.description, p.createdAt,
               COUNT(pt.trackId) AS trackCount,
               (SELECT t.artworkUri FROM tracks t
                INNER JOIN playlist_tracks pt2 ON t.id = pt2.trackId
                WHERE pt2.playlistId = p.id AND t.artworkUri IS NOT NULL
                ORDER BY pt2.position ASC LIMIT 1) AS coverUri
        FROM playlists p
        LEFT JOIN playlist_tracks pt ON p.id = pt.playlistId
        GROUP BY p.id
        ORDER BY p.createdAt DESC
        """
    )
    fun observePlaylistsWithSummary(): Flow<List<PlaylistWithSummary>>

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
        """
    )
    fun observePlaylistTracks(playlistId: Long): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, description = :description WHERE id = :playlistId")
    suspend fun updatePlaylist(playlistId: Long, name: String, description: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistTrack(crossRef: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun updateTrackPosition(playlistId: Long, trackId: Long, position: Int)

    @Transaction
    suspend fun reorderPlaylistTracks(playlistId: Long, orderedTrackIds: List<Long>) {
        orderedTrackIds.forEachIndexed { index, trackId ->
            updateTrackPosition(playlistId, trackId, index)
        }
    }
}

@Dao
interface WatchedFolderDao {
    @Query("SELECT * FROM watched_folders ORDER BY isAppInternal DESC, displayName ASC")
    fun observeWatchedFolders(): Flow<List<WatchedFolderEntity>>

    @Query("SELECT * FROM watched_folders")
    suspend fun getWatchedFoldersOnce(): List<WatchedFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: WatchedFolderEntity)

    @Query("UPDATE watched_folders SET lastScannedAt = :timestamp, trackCount = :count WHERE uri = :uri")
    suspend fun updateScanStats(uri: String, timestamp: Long, count: Int)

    @Query("DELETE FROM watched_folders WHERE uri = :uri")
    suspend fun deleteFolder(uri: String)
}

@Dao
interface ListeningHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: ListeningHistoryEntity): Long

    @Query("SELECT * FROM listening_history ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecentHistory(limit: Int = 100): Flow<List<ListeningHistoryEntity>>

    @Query(
        """
        SELECT trackId, COUNT(*) AS playCount, MAX(playedAt) AS lastPlayedAt
        FROM listening_history
        GROUP BY trackId
        """
    )
    fun observeTrackPlayStats(): Flow<List<TrackPlayStat>>

    @Query(
        """
        SELECT genre, COUNT(*) AS playCount
        FROM listening_history
        WHERE genre != '' AND genre != 'Unknown Genre'
        GROUP BY genre
        ORDER BY playCount DESC
        """
    )
    fun observeGenrePlayStats(): Flow<List<GenrePlayStat>>

    @Query(
        """
        SELECT artist, COUNT(*) AS playCount
        FROM listening_history
        WHERE artist != '' AND artist != 'Unknown Artist'
        GROUP BY artist
        ORDER BY playCount DESC
        """
    )
    fun observeArtistPlayStats(): Flow<List<ArtistPlayStat>>

    @Query("SELECT COUNT(*) FROM listening_history")
    fun observeTotalPlayCount(): Flow<Int>

    @Query("DELETE FROM listening_history")
    suspend fun clearHistory()
}

@Dao
interface ExcludedFolderDao {
    @Query("SELECT * FROM excluded_folders ORDER BY createdAt DESC")
    fun observeExcludedFolders(): Flow<List<ExcludedFolderEntity>>

    @Query("SELECT * FROM excluded_folders")
    suspend fun getExcludedFoldersOnce(): List<ExcludedFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExcludedFolder(folder: ExcludedFolderEntity)

    @Query("DELETE FROM excluded_folders WHERE identifier = :identifier")
    suspend fun deleteExcludedFolder(identifier: String)
}


