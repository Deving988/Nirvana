package com.example.data.database

import androidx.room.*
import com.example.data.model.CachedSongEntity
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistSongCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM cached_songs ORDER BY cachedAt DESC")
    fun getCachedSongsFlow(): Flow<List<CachedSongEntity>>

    @Query("SELECT * FROM cached_songs")
    suspend fun getCachedSongs(): List<CachedSongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedSong(song: CachedSongEntity)

    @Query("DELETE FROM cached_songs WHERE id = :songId")
    suspend fun deleteCachedSong(songId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM cached_songs WHERE id = :songId)")
    suspend fun isSongCached(songId: String): Boolean

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getPlaylistsFlow(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    // Playlist song mappings
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSongCrossRef(crossRef: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deletePlaylistSongCrossRef(playlistId: Long, songId: String)

    @Query("""
        SELECT * FROM cached_songs 
        INNER JOIN playlist_songs ON cached_songs.id = playlist_songs.songId 
        WHERE playlist_songs.playlistId = :playlistId
    """)
    fun getSongsForPlaylistFlow(playlistId: Long): Flow<List<CachedSongEntity>>
    
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun deleteSongsForPlaylist(playlistId: Long)

    // YouTube Search Cache
    @Query("SELECT * FROM youtube_cache WHERE `query` = :searchQuery LIMIT 1")
    suspend fun getYoutubeCache(searchQuery: String): com.example.data.model.YoutubeCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertYoutubeCache(cache: com.example.data.model.YoutubeCacheEntity)

    @Query("DELETE FROM youtube_cache WHERE timestamp < :expiryTime")
    suspend fun clearExpiredYoutubeCache(expiryTime: Long)
}

@Database(
    entities = [CachedSongEntity::class, PlaylistEntity::class, PlaylistSongCrossRef::class, com.example.data.model.YoutubeCacheEntity::class],
    version = 2,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
}
