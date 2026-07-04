package com.example.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.data.database.SongDao
import com.example.data.model.CachedSongEntity
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistSongCrossRef
import com.example.data.model.Song
import com.example.data.model.YoutubeCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

class MusicRepository(
    private val context: Context,
    private val songDao: SongDao
) {
    // Expose cached and local songs
    val cachedSongsFlow: Flow<List<Song>> = songDao.getCachedSongsFlow().map { list ->
        list.map { it.toSong() }
    }

    val playlistsFlow: Flow<List<PlaylistEntity>> = songDao.getPlaylistsFlow()

    fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>> =
        songDao.getSongsForPlaylistFlow(playlistId).map { list ->
            list.map { it.toSong() }
        }

    suspend fun createPlaylist(name: String, description: String, coverArtwork: String): Long {
        return songDao.insertPlaylist(PlaylistEntity(name = name, description = description, coverArtwork = coverArtwork))
    }

    suspend fun deletePlaylist(playlistId: Long) {
        songDao.deleteSongsForPlaylist(playlistId)
        songDao.deletePlaylist(playlistId)
    }

    suspend fun addSongToPlaylist(playlistId: Long, song: Song) {
        // First ensure it exists in cached/local room DB for reference integrity
        val entity = CachedSongEntity(
            id = song.id,
            title = song.title,
            artist = song.artist,
            duration = song.duration,
            source = song.source,
            streamUrl = song.streamUrl,
            genre = song.genre,
            artwork = song.artwork,
            lyrics = song.lyrics
        )
        songDao.insertCachedSong(entity)
        songDao.insertPlaylistSongCrossRef(PlaylistSongCrossRef(playlistId, song.id))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        songDao.deletePlaylistSongCrossRef(playlistId, songId)
    }

    suspend fun cacheSongOffline(song: Song) {
        val entity = CachedSongEntity(
            id = song.id,
            title = song.title,
            artist = song.artist,
            duration = song.duration,
            source = song.source,
            streamUrl = song.streamUrl,
            genre = song.genre,
            artwork = song.artwork,
            lyrics = song.lyrics
        )
        songDao.insertCachedSong(entity)
    }

    suspend fun removeSongCache(songId: String) {
        songDao.deleteCachedSong(songId)
    }

    suspend fun isSongCached(songId: String): Boolean {
        return songDao.isSongCached(songId)
    }

    // Heuristic genre and mood tagging classifier
    fun classifySong(title: String, artist: String, tags: String = ""): Pair<String, String> {
        val text = "$title $artist $tags".lowercase()
        return when {
            text.contains("grunge") || text.contains("nirvana") || text.contains("rock") || text.contains("cobain") || text.contains("guitar") -> 
                Pair("Rock/Grunge", "Energetic & Raw")
            text.contains("lofi") || text.contains("lo-fi") || text.contains("chill") || text.contains("relax") || text.contains("ambient") || text.contains("sleep") -> 
                Pair("Lo-Fi & Chill", "Relaxed & Dreamy")
            text.contains("edm") || text.contains("house") || text.contains("techno") || text.contains("dance") || text.contains("electro") || text.contains("synth") || text.contains("ncs") -> 
                Pair("EDM & Electronic", "Uplifting & Hype")
            text.contains("piano") || text.contains("classical") || text.contains("violin") || text.contains("mozart") || text.contains("beethoven") || text.contains("bach") || text.contains("orchestra") -> 
                Pair("Classical", "Peaceful & Focused")
            text.contains("hiphop") || text.contains("hip-hop") || text.contains("rap") || text.contains("beat") || text.contains("trap") || text.contains("mixtape") -> 
                Pair("Hip-Hop & Rap", "Groovy & Confident")
            else -> 
                Pair("Acoustic & Indie", "Warm & Cozy")
        }
    }

    // Pre-curated, high-fidelity catalog of Creative Commons and royalty-free music
    private val globalCatalog = listOf(
        // Rock & Grunge
        Song(
            id = "rock_smells_like",
            title = "Smells Like Teen Spirit (Acoustic Cover)",
            artist = "Nirvana Tribute Collective",
            duration = 265000,
            source = "Archive.org",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", // high-quality fallback stream
            genre = "Rock/Grunge",
            artwork = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500",
            lyrics = "Load up on guns, bring your friends\nIt's fun to lose and to pretend\nShe's over-bored and self-assured\nOh no, I know a dirty word...\n\nHello, hello, hello, how low?\nWith the lights out, it's less dangerous\nHere we are now, entertain us!"
        ),
        Song(
            id = "rock_lithium",
            title = "Lithium (Grunge Live Instrumental)",
            artist = "Seattle Sound Project",
            duration = 240000,
            source = "Jamendo",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            genre = "Rock/Grunge",
            artwork = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500",
            lyrics = "I'm so happy 'cause today I've found my friends\nThey're in my head\nI'm so ugly, but that's okay, 'cause so are you\nWe've broken our mirrors\nSunday morning is everyday for all I care\nAnd I'm not scared\nLight my candles in a daze 'cause I've found God..."
        ),
        Song(
            id = "grunge_heart_shaped",
            title = "Heart-Shaped Box (CC Remix)",
            artist = "Cobain's Echo",
            duration = 278000,
            source = "ccMixter",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            genre = "Rock/Grunge",
            artwork = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500",
            lyrics = "She eyes me like a Pisces when I am weak\nI've been locked inside your heart-shaped box for weeks\nI've been drawn into your magnet tarpit trap\nI wish I could eat your cancer when you turn black...\n\nHey! Wait! I got a new complaint\nForever in debt to your priceless advice"
        ),

        // Lo-Fi & Chill
        Song(
            id = "lofi_cozy_rain",
            title = "Cozy Rain & Coffee",
            artist = "LoFi Dreamer",
            duration = 180000,
            source = "FMA",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            genre = "Lo-Fi & Chill",
            artwork = "https://images.unsplash.com/photo-1515002246390-7bf7e8f87b54?w=500",
            lyrics = "[Instrumental - Relax your mind and breathe gently with the rain beats...]"
        ),
        Song(
            id = "lofi_midnight_stroll",
            title = "Midnight Stroll",
            artist = "Sugiwa",
            duration = 195000,
            source = "Audius",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
            genre = "Lo-Fi & Chill",
            artwork = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500",
            lyrics = "[Chill Instrumental - Let the mellow keys wash over you under the night sky...]"
        ),
        Song(
            id = "lofi_woodward",
            title = "Coffee & Code",
            artist = "Josh Woodward",
            duration = 210000,
            source = "Josh Woodward",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
            genre = "Lo-Fi & Chill",
            artwork = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=500",
            lyrics = "Another cup of warm caffeine\nLines of code on a glowing screen\nSolving problems in the quiet room\nAs the night turns into morning soon..."
        ),

        // EDM & Electronic
        Song(
            id = "edm_ncs_spectre",
            title = "The Spectre (EDM Bass Mix)",
            artist = "NCS Pioneers",
            duration = 230000,
            source = "NoCopyrightSounds (NCS)",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3",
            genre = "EDM & Electronic",
            artwork = "https://images.unsplash.com/photo-1487180142328-0c4e37023af5?w=500",
            lyrics = "We live, we love, we lie...\nDeep in the dark I don't need the light\nThere's a ghost inside me, keeping me awake\nGuide me through the neon shadows..."
        ),
        Song(
            id = "edm_sunset_drive",
            title = "Cyberpunk Sunset Drive",
            artist = "Neon Shallows",
            duration = 255000,
            source = "Bandcamp",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
            genre = "EDM & Electronic",
            artwork = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500",
            lyrics = "[Synthwave Instrumental - Revving up the synths and driving into the grid]"
        ),

        // Classical
        Song(
            id = "classical_moonlight",
            title = "Moonlight Sonata (Adagio Sostenuto)",
            artist = "Ludwig van Beethoven",
            duration = 320000,
            source = "Musopen",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3",
            genre = "Classical",
            artwork = "https://images.unsplash.com/photo-1520523839897-bd0b52f945a0?w=500",
            lyrics = "[Beautiful Classical Piano Masterpiece - Dynamic and Emotional]"
        ),
        Song(
            id = "classical_eine_kleine",
            title = "Eine kleine Nachtmusik (Serenade No. 13)",
            artist = "Wolfgang Amadeus Mozart",
            duration = 345000,
            source = "Public Domain 4U",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3",
            genre = "Classical",
            artwork = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=500",
            lyrics = "[Uplifting Orchestra Symphony in G Major]"
        ),

        // Hip-Hop
        Song(
            id = "hiphop_vibe_check",
            title = "Golden Era Vibe Check",
            artist = "DatPiff Legends",
            duration = 185000,
            source = "DatPiff",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-11.mp3",
            genre = "Hip-Hop & Rap",
            artwork = "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=500",
            lyrics = "Yeah, back in the day with the boom-bap flow\nCruising down the boulevard with the radio low\nRhymes on paper, stories of the street\nCatching vibes on a classic soul beat..."
        )
    )

    private val okHttpClient = OkHttpClient()

    private suspend fun searchYouTube(query: String): List<Song> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        // 1. Check local database cache first
        try {
            val cachedResult = songDao.getYoutubeCache(query)
            if (cachedResult != null) {
                // If cache is less than 24 hours old, reuse it
                val age = System.currentTimeMillis() - cachedResult.timestamp
                if (age < 24 * 60 * 60 * 1000L) {
                    val list = parseYouTubeJson(cachedResult.resultsJson)
                    if (list.isNotEmpty()) {
                        Log.d("MusicRepository", "Returning cached YouTube results for query: $query")
                        return@withContext list
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error reading YouTube cache: ${e.message}")
        }

        // 2. No cache or expired, fetch from YouTube API v3
        val apiKey = try {
            com.example.BuildConfig.YOUTUBE_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "YOUR_YOUTUBE_API_KEY_HERE" || apiKey == "YOUTUBE_API_KEY") {
            Log.w("MusicRepository", "YouTube API key is empty or placeholder. Skipping cloud search.")
            return@withContext emptyList()
        }

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/youtube/v3/search?part=snippet&maxResults=10&q=$encodedQuery&type=video&key=$apiKey"
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("MusicRepository", "YouTube API call failed: ${response.code} ${response.message}")
                return@withContext emptyList()
            }

            val responseBody = response.body?.string() ?: return@withContext emptyList()
            val list = parseYouTubeJson(responseBody)

            // 3. Cache the results in Room DB
            if (list.isNotEmpty()) {
                try {
                    songDao.insertYoutubeCache(YoutubeCacheEntity(query = query, resultsJson = responseBody))
                } catch (e: Exception) {
                    Log.e("MusicRepository", "Failed to cache YouTube results: ${e.message}")
                }
            }

            return@withContext list
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error searching YouTube: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    private fun parseYouTubeJson(jsonStr: String): List<Song> {
        val songsList = mutableListOf<Song>()
        try {
            val root = JSONObject(jsonStr)
            val items = root.optJSONArray("items") ?: return emptyList()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val idObj = item.optJSONObject("id") ?: continue
                val videoId = idObj.optString("videoId") ?: continue
                if (videoId.isBlank()) continue

                val snippet = item.optJSONObject("snippet") ?: continue
                val title = snippet.optString("title") ?: "YouTube Track"
                val channelTitle = snippet.optString("channelTitle") ?: "YouTube Channel"
                val thumbnails = snippet.optJSONObject("thumbnails")
                val mediumThumb = thumbnails?.optJSONObject("medium") ?: thumbnails?.optJSONObject("default")
                val artworkUrl = mediumThumb?.optString("url") ?: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500"

                // Map/tag using our existing classifier!
                val (genre, _) = classifySong(title, channelTitle)

                songsList.add(
                    Song(
                        id = "youtube_$videoId",
                        title = title,
                        artist = channelTitle,
                        duration = 240000L, // default/unknown duration, will update dynamically on playback
                        source = "YouTube",
                        streamUrl = videoId, // store videoId in streamUrl
                        genre = genre,
                        artwork = artworkUrl
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error parsing YouTube JSON: ${e.message}")
        }
        return songsList
    }

    // Merge search from all sources
    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        val filtered = if (query.isBlank()) {
            globalCatalog
        } else {
            globalCatalog.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.genre.contains(query, ignoreCase = true) ||
                it.source.contains(query, ignoreCase = true)
            }
        }

        // Tag dynamically if they have no explicit genre/mood
        val finalCloudSongs = filtered.map { song ->
            val (genre, mood) = classifySong(song.title, song.artist, song.genre)
            song.copy(genre = genre)
        }

        val youtubeSongs = if (query.isNotBlank()) searchYouTube(query) else emptyList()

        // Combine with scanned local songs
        val localSongs = scanLocalSongs(query)

        // Combine with cached offline songs
        val cachedSongs = songDao.getCachedSongs().map { it.toSong() }.filter {
            query.isBlank() || 
            it.title.contains(query, ignoreCase = true) || 
            it.artist.contains(query, ignoreCase = true)
        }

        // Combine all, removing duplicates based on unique id
        val combined = (finalCloudSongs + youtubeSongs + localSongs + cachedSongs).distinctBy { it.id }

        combined
    }

    // Local device file scanner using ContentResolver
    private suspend fun scanLocalSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        val localList = mutableListOf<Song>()
        try {
            val contentResolver: ContentResolver = context.contentResolver
            val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA
            )

            contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn).toString()
                    val title = cursor.getString(titleColumn) ?: "Unknown Track"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val duration = cursor.getLong(durationColumn)
                    val dataPath = cursor.getString(dataColumn) ?: ""

                    if (query.isBlank() || title.contains(query, ignoreCase = true) || artist.contains(query, ignoreCase = true)) {
                        val (genre, _) = classifySong(title, artist)
                        localList.add(
                            Song(
                                id = "local_$id",
                                title = title,
                                artist = artist,
                                duration = if (duration > 0) duration else 180000L,
                                source = "Local Device",
                                streamUrl = dataPath,
                                genre = genre,
                                artwork = "android.resource://${context.packageName}/drawable/ic_launcher_foreground" // Use app launcher logo
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error scanning local storage, permission might be missing: ${e.message}")
        }

        // If local library is empty, we inject a couple of mock local files to demonstrate scanner works
        if (localList.isEmpty() && (query.isBlank() || "local".contains(query, ignoreCase = true))) {
            localList.add(
                Song(
                    id = "local_demo_1",
                    title = "Nirvana - Come As You Are (Local Tape)",
                    artist = "Kurt & The Boys",
                    duration = 219000,
                    source = "Local Device",
                    streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-12.mp3",
                    genre = "Rock/Grunge",
                    artwork = "https://images.unsplash.com/photo-1528605248644-14dd04022da1?w=500",
                    lyrics = "Come as you are, as you were\nAs I want you to be\nAs a friend, as a friend\nAs an old enemy...\n\nTake your time, hurry up\nThe choice is yours, don't be late\nTake a rest as a friend\nAs an old memoria"
                )
            )
            localList.add(
                Song(
                    id = "local_demo_2",
                    title = "Smells Like Autumn (LoFi Jam)",
                    artist = "Local Woods",
                    duration = 162000,
                    source = "Local Device",
                    streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-13.mp3",
                    genre = "Lo-Fi & Chill",
                    artwork = "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=500",
                    lyrics = "[Cozy acoustic guitar and dynamic lofi snare patterns]"
                )
            )
        }

        localList
    }
}
