package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val duration: Long, // in milliseconds
    val source: String, // e.g. "Jamendo", "FMA", "Audius", "Local Library", etc.
    val streamUrl: String,
    val genre: String,
    val artwork: String, // Url or local path
    val lyrics: String? = null,
    val isCached: Boolean = false
) : Serializable

@Entity(tableName = "cached_songs")
data class CachedSongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val duration: Long,
    val source: String,
    val streamUrl: String,
    val genre: String,
    val artwork: String,
    val lyrics: String? = null,
    val localFilePath: String? = null,
    val cachedAt: Long = System.currentTimeMillis()
) {
    fun toSong() = Song(id, title, artist, duration, source, streamUrl, genre, artwork, lyrics, true)
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val coverArtwork: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: String
)

@Entity(tableName = "youtube_cache")
data class YoutubeCacheEntity(
    @PrimaryKey val query: String,
    val resultsJson: String,
    val timestamp: Long = System.currentTimeMillis()
)

