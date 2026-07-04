package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.PlaylistEntity
import com.example.data.model.Song
import com.example.ui.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import java.util.Collections

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(viewModel: MusicViewModel) {
    var activeTab by remember { mutableIntStateOf(0) } // 0: Home, 1: Search, 2: Library, 3: Jam, 4: EQ
    var isNowPlayingExpanded by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()

    // Handle back button to collapse Now Playing
    if (isNowPlayingExpanded) {
        BackHandler {
            isNowPlayingExpanded = false
        }
    }

    val darkBg = Color(0xFF0F0E13)
    val accentPink = Color(0xFFFC2A54) // Apple Music-style Neon Pink
    val cardBg = Color(0xFF1E1C24)

    Scaffold(
        bottomBar = {
            Column {
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
                // Bottom Navigation Bar
                NavigationBar(
                    containerColor = darkBg,
                    tonalElevation = 8.dp,
                    modifier = Modifier.shadow(16.dp)
                ) {
                    val items = listOf(
                        Triple("Listen Now", Icons.Default.MusicNote, 0),
                        Triple("Search", Icons.Default.Search, 1),
                        Triple("Library", Icons.Default.LibraryMusic, 2),
                        Triple("Jam", Icons.Default.Group, 3),
                        Triple("Equalizer", Icons.Default.GraphicEq, 4)
                    )
                    items.forEach { (label, icon, index) ->
                        NavigationBarItem(
                            selected = activeTab == index,
                            onClick = { activeTab = index },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = accentPink,
                                selectedTextColor = accentPink,
                                indicatorColor = Color(0xFF2E1A22),
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray
                            )
                        )
                    }
                }
            }
        },
        containerColor = darkBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main dynamic content screen with crossfades
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "TabTransition"
            ) { tab ->
                when (tab) {
                    0 -> HomeScreen(viewModel, onProfileClick = { showSettingsDialog = true }) { viewModel.playSong(it) }
                    1 -> SearchScreen(viewModel)
                    2 -> LibraryScreen(viewModel)
                    3 -> JamScreen(viewModel)
                    4 -> EqualizerScreen(viewModel)
                }
            }

            // Floating Mini Player (Apple Music style)
            if (currentSong != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(14.dp))
                        .background(cardBg.copy(alpha = 0.95f), RoundedCornerShape(14.dp))
                        .clickable { isNowPlayingExpanded = true }
                        .testTag("mini_player")
                        .padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AsyncImage(
                            model = currentSong?.artwork,
                            contentDescription = "Cover",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentSong?.title ?: "Nirvana Music",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${currentSong?.artist ?: "Unknown"} • ${currentSong?.source ?: "Free Sync"}",
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Play/Pause button
                        IconButton(
                            onClick = { viewModel.playOrPause() },
                            modifier = Modifier.testTag("mini_play_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = accentPink,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Skip button
                        IconButton(onClick = { viewModel.next() }) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Skip",
                                tint = Color.White
                            )
                        }
                    }

                    // Bottom progress line
                    val progressRatio = (if (duration > 0) progress.toFloat() / duration else 0f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(progressRatio)
                            .height(2.5.dp)
                            .background(accentPink)
                    )
                }
            }

            // Expanded "Now Playing" Sheet Overlay (Sliding animation)
            AnimatedVisibility(
                visible = isNowPlayingExpanded,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
                ) + fadeOut()
            ) {
                NowPlayingSheet(viewModel) {
                    isNowPlayingExpanded = false
                }
            }

            // settings Dialog overlay
            if (showSettingsDialog) {
                AlertDialog(
                    onDismissRequest = { showSettingsDialog = false },
                    containerColor = Color(0xFF131118),
                    title = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            NirvanaLogo(modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "NIRVANA",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp,
                                letterSpacing = 3.sp
                            )
                            Text(
                                "MUSIC FOR EVERY MOOD",
                                color = Color(0xFFFC2A54),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                        }
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Manage streaming preferences and bandwidth usage policies.", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Use YouTube on Mobile Data", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("Allows streaming full video tracks when not connected to Wi-Fi. (Uses higher data)", color = Color.Gray, fontSize = 12.sp)
                                }
                                var useYoutubeOnMobileData by remember { mutableStateOf(viewModel.useYoutubeOnMobileData) }
                                Switch(
                                    checked = useYoutubeOnMobileData,
                                    onCheckedChange = {
                                        useYoutubeOnMobileData = it
                                        viewModel.useYoutubeOnMobileData = it
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFFFC2A54),
                                        uncheckedThumbColor = Color.Gray,
                                        uncheckedTrackColor = Color(0xFF100F14)
                                    )
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSettingsDialog = false }) {
                            Text("Done", color = Color(0xFFFC2A54), fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // Mobile Data Warning Dialog overlay
            val warningSong by viewModel.showMobileDataWarningForSong.collectAsStateWithLifecycle()
            warningSong?.let { song ->
                val searchResultsList by viewModel.searchResults.collectAsStateWithLifecycle()
                val cleanTitle = song.title.split("-", "(", "[")[0].trim()
                val alternative = searchResultsList.firstOrNull { 
                    it.source != "YouTube" && it.title.contains(cleanTitle, ignoreCase = true) 
                }
                
                AlertDialog(
                    onDismissRequest = { viewModel.dismissMobileDataWarning() },
                    containerColor = Color(0xFF1E1C24),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, "Warning", tint = Color(0xFFFF8B75))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Mobile Data Warning", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column {
                            Text(
                                text = "You are currently connected to mobile data. Streaming '${song.title}' from YouTube will consume more bandwidth.",
                                color = Color.LightGray,
                                fontSize = 14.sp
                            )
                            
                            if (alternative != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF100F14), RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Text("💡 Alternative Found!", color = Color(0xFF8CFFB1), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(alternative.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("By ${alternative.artist} • ${alternative.source}", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (alternative != null) {
                                Button(
                                    onClick = {
                                        viewModel.dismissMobileDataWarning()
                                        viewModel.playSongAnyway(alternative)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B3024)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Play Pure Audio Alternative", color = Color(0xFF8CFFB1), fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Button(
                                onClick = {
                                    viewModel.dismissMobileDataWarning()
                                    viewModel.playSongAnyway(song)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFC2A54)),
                                modifier = Modifier.fillMaxWidth()
                              ) {
                                Text("Stream YouTube Anyway", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            
                            TextButton(
                                onClick = {
                                    viewModel.dismissMobileDataWarning()
                                    viewModel.useYoutubeOnMobileData = true
                                    viewModel.playSongAnyway(song)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Always Allow YouTube & Stream", color = Color.LightGray, fontSize = 13.sp)
                            }
                            
                            TextButton(
                                onClick = { viewModel.dismissMobileDataWarning() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel", color = Color.Gray)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun NirvanaLogo(
    modifier: Modifier = Modifier,
    useGradient: Boolean = true
) {
    Canvas(modifier = modifier) {
        val scaleX = size.width / 108f
        val scaleY = size.height / 108f
        
        val strokeWidthN = 11f * scaleX
        val strokeWidthBar = 4f * scaleX
        
        val brush = if (useGradient) {
            Brush.linearGradient(
                colors = listOf(Color(0xFF7F3DFF), Color(0xFFFC2A54)),
                start = Offset(25f * scaleX, 54f * scaleY),
                end = Offset(85f * scaleX, 54f * scaleY)
            )
        } else {
            SolidColor(Color.White)
        }
        
        val pathN = Path().apply {
            moveTo(25f * scaleX, 74f * scaleY)
            lineTo(25f * scaleX, 34f * scaleY)
            quadraticTo(25f * scaleX, 28f * scaleY, 31f * scaleX, 32f * scaleY)
            lineTo(51f * scaleX, 70f * scaleY)
            quadraticTo(57f * scaleX, 74f * scaleY, 57f * scaleX, 68f * scaleY)
            lineTo(57f * scaleX, 36f * scaleY)
        }
        
        drawPath(
            path = pathN,
            brush = brush,
            style = Stroke(
                width = strokeWidthN,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        
        drawLine(
            brush = brush,
            start = Offset(67f * scaleX, 49f * scaleY),
            end = Offset(67f * scaleX, 59f * scaleY),
            strokeWidth = strokeWidthBar,
            cap = StrokeCap.Round
        )
        drawLine(
            brush = brush,
            start = Offset(73f * scaleX, 43f * scaleY),
            end = Offset(73f * scaleX, 65f * scaleY),
            strokeWidth = strokeWidthBar,
            cap = StrokeCap.Round
        )
        drawLine(
            brush = brush,
            start = Offset(79f * scaleX, 47f * scaleY),
            end = Offset(79f * scaleX, 61f * scaleY),
            strokeWidth = strokeWidthBar,
            cap = StrokeCap.Round
        )
        drawLine(
            brush = brush,
            start = Offset(85f * scaleX, 51f * scaleY),
            end = Offset(85f * scaleX, 57f * scaleY),
            strokeWidth = strokeWidthBar,
            cap = StrokeCap.Round
        )
    }
}

// -------------------------------------------------------------
// 1. HOME SCREEN (LISTEN NOW)
// -------------------------------------------------------------
@Composable
fun HomeScreen(viewModel: MusicViewModel, onProfileClick: () -> Unit, onSongClick: (Song) -> Unit) {
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    val featuredBanner = results.firstOrNull { it.genre == "Rock/Grunge" }
    val electronicHits = results.filter { it.genre == "EDM & Electronic" }
    val classicalChills = results.filter { it.genre == "Classical" }
    val loFiNights = results.filter { it.genre == "Lo-Fi & Chill" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // App Title Section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                NirvanaLogo(modifier = Modifier.size(42.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "NIRVANA",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 2.sp
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E1C24))
                    .clickable { onProfileClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "MUSIC FOR EVERY MOOD",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFC2A54),
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Hero Spotlight Banner
        featuredBanner?.let { song ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSongClick(song) }
                    .shadow(8.dp)
            ) {
                AsyncImage(
                    model = song.artwork,
                    contentDescription = "Spotlight",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFC2A54), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("SPOTLIGHT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(song.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("By ${song.artist} • ${song.source}", fontSize = 14.sp, color = Color.LightGray)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        HomeSectionRow("Seattle Grunge Classics", results.filter { it.genre == "Rock/Grunge" }, onSongClick)
        Spacer(modifier = Modifier.height(24.dp))
        HomeSectionRow("Midnight Lo-Fi & Chill", loFiNights, onSongClick)
        Spacer(modifier = Modifier.height(24.dp))
        HomeSectionRow("Peaceful Masterpieces", classicalChills, onSongClick)
        Spacer(modifier = Modifier.height(24.dp))
        HomeSectionRow("EDM Bassline Pioneers", electronicHits, onSongClick)

        Spacer(modifier = Modifier.height(80.dp)) // Padding for mini player
    }
}

@Composable
fun HomeSectionRow(title: String, songs: List<Song>, onSongClick: (Song) -> Unit) {
    if (songs.isEmpty()) return

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Text("See All", color = Color(0xFFFC2A54), fontSize = 13.sp, modifier = Modifier.clickable {})
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(songs) { song ->
                Column(
                    modifier = Modifier
                        .width(130.dp)
                        .clickable { onSongClick(song) }
                ) {
                    AsyncImage(
                        model = song.artwork,
                        contentDescription = "Cover",
                        modifier = Modifier
                            .size(130.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        song.title,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist,
                        color = Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 2. SEARCH SCREEN (UNIFIED SOURCE-AGNOSTIC)
// -------------------------------------------------------------
@Composable
fun SearchScreen(viewModel: MusicViewModel) {
    val context = LocalContext.current
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()

    var activeMenuSong by remember { mutableStateOf<Song?>(null) }
    var showPlaylistDialog by remember { mutableStateOf(false) }

    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Search", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(12.dp))

        // Rounded unified Search Bar
        TextField(
            value = query,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            placeholder = { Text("Songs, artists, genres, or audio sources...", color = Color.Gray) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .testTag("search_bar"),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E1C24),
                unfocusedContainerColor = Color(0xFF1E1C24),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            leadingIcon = { Icon(Icons.Default.Search, "Search Icon", tint = Color.Gray) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Clear, "Clear Icon", tint = Color.White)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.onSearchQueryChanged(query) })
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFC2A54))
            }
        } else {
            if (results.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MusicOff, "No Music", tint = Color.DarkGray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No results found for '$query'", color = Color.LightGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Try searching 'Nirvana', 'classical', or 'lofi'", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            } else {
                Text(
                    text = if (query.isBlank()) "Recommended Collection" else "Unified Results from All APIs",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.LightGray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(results) { song ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.playSong(song, results) }
                                .padding(vertical = 4.dp)
                        ) {
                            AsyncImage(
                                model = song.artwork,
                                contentDescription = "Artwork",
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        song.artist,
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF2E1A22), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(song.source, color = Color(0xFFFC2A54), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (song.source == "YouTube") Color(0xFF3E1F1A) else Color(0xFF1B3024),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (song.source == "YouTube") "Higher Data" else "Low Data",
                                            color = if (song.source == "YouTube") Color(0xFFFF8B75) else Color(0xFF8CFFB1),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF1E1C24), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(song.genre, color = Color.Gray, fontSize = 10.sp)
                                    }
                                }
                            }
                            IconButton(onClick = { activeMenuSong = song }) {
                                Icon(Icons.Default.MoreVert, "Options", tint = Color.LightGray)
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }

        // Dialog for song option menu
        activeMenuSong?.let { song ->
            AlertDialog(
                onDismissRequest = { activeMenuSong = null },
                containerColor = Color(0xFF1E1C24),
                title = { Text(song.title, color = Color.White) },
                text = {
                    Column {
                        Text("Artist: ${song.artist}", color = Color.LightGray, fontSize = 14.sp)
                        Text("Source Adapter: ${song.source}", color = Color.LightGray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        ListItemButton(
                            label = "Save for Offline Listening",
                            icon = Icons.Default.CloudDownload
                        ) {
                            viewModel.toggleOfflineCache(song)
                            activeMenuSong = null
                        }
                        ListItemButton(
                            label = "Add to Playlist",
                            icon = Icons.Default.PlaylistAdd
                        ) {
                            showPlaylistDialog = true
                        }
                        ListItemButton(
                            label = "Queue Next",
                            icon = Icons.Default.QueueMusic
                        ) {
                            viewModel.playerManager.playSong(song)
                            activeMenuSong = null
                            Toast.makeText(context, "Added to active queue", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeMenuSong = null }) {
                        Text("Close", color = Color(0xFFFC2A54))
                    }
                }
            )
        }

        // Add to Playlist picker
        if (showPlaylistDialog && activeMenuSong != null) {
            Dialog(onDismissRequest = { showPlaylistDialog = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C24)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Select Playlist", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        if (playlists.isEmpty()) {
                            Text("No playlists found. Create one in the 'Library' tab.", color = Color.Gray)
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                                items(playlists) { playlist ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.addSongToPlaylist(playlist.id, activeMenuSong!!)
                                                showPlaylistDialog = false
                                                activeMenuSong = null
                                            }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.QueueMusic, null, tint = Color(0xFFFC2A54))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(playlist.name, color = Color.White)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = { showPlaylistDialog = false },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Cancel", color = Color(0xFFFC2A54))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ListItemButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp)
    ) {
        Icon(icon, null, tint = Color(0xFFFC2A54), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, color = Color.White, fontSize = 15.sp)
    }
}

// -------------------------------------------------------------
// 3. LIBRARY SCREEN (PLAYLISTS, OFFLINE CACHE, LOCAL SCANNER)
// -------------------------------------------------------------
@Composable
fun LibraryScreen(viewModel: MusicViewModel) {
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Offline Cache, 1: Playlists, 2: Device Files
    val cachedList by viewModel.cachedSongs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var playlistDesc by remember { mutableStateOf("") }

    var selectedPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    val playlistSongs = selectedPlaylist?.let { playlist ->
        viewModel.getSongsForPlaylistFlow(playlist.id).collectAsStateWithLifecycle(emptyList()).value
    } ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (selectedPlaylist != null) {
            // View Playlist Sub-Screen
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedPlaylist = null }) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(selectedPlaylist?.name ?: "Playlist", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(selectedPlaylist?.description ?: "", color = Color.LightGray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(16.dp))

            if (playlistSongs.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("This playlist is empty. Add songs from the Search tab!", color = Color.Gray, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(playlistSongs) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.playSong(song, playlistSongs) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = song.artwork,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(song.artist, color = Color.Gray, fontSize = 12.sp)
                            }
                            IconButton(
                                onClick = { viewModel.removeSongFromPlaylist(selectedPlaylist!!.id, song.id) }
                            ) {
                                Icon(Icons.Default.Delete, "Remove", tint = Color.Red)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { 
                    selectedPlaylist?.let { viewModel.deletePlaylist(it.id) }
                    selectedPlaylist = null
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Text("Delete Entire Playlist")
            }

        } else {
            Text("Library", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1C24), RoundedCornerShape(8.dp))
                    .padding(4.dp)
            ) {
                listOf("Offline Cached", "Playlists", "Scanned Device").forEachIndexed { index, title ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selectedSubTab == index) Color(0xFFFC2A54) else Color.Transparent)
                            .clickable { selectedSubTab = index }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (selectedSubTab == index) Color.White else Color.LightGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedSubTab) {
                0 -> {
                    if (cachedList.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.OfflinePin, null, tint = Color.DarkGray, modifier = Modifier.size(56.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No offline cached tracks yet.", color = Color.LightGray)
                                Text("Tap 3 dots in search to cache songs locally.", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(cachedList) { song ->
                                LibrarySongRow(song) { viewModel.playSong(song, cachedList) }
                            }
                        }
                    }
                }
                1 -> {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("My Playlists", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Button(
                                onClick = { showCreatePlaylistDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFC2A54))
                            ) {
                                Icon(Icons.Default.Add, null)
                                Text("New")
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        if (playlists.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No playlists. Create one to organize your music!", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(playlists) { playlist ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedPlaylist = playlist }
                                            .background(Color(0xFF1E1C24), RoundedCornerShape(8.dp))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(Color(0xFF2E1A22), RoundedCornerShape(6.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("🎵", fontSize = 22.sp)
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(playlist.name, color = Color.White, fontWeight = FontWeight.Bold)
                                            Text(playlist.description, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    val results by viewModel.searchResults.collectAsStateWithLifecycle()
                    val localSongs = results.filter { it.source == "Local Device" }

                    if (localSongs.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No on-device media found.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(localSongs) { song ->
                                LibrarySongRow(song) { viewModel.playSong(song, localSongs) }
                            }
                        }
                    }
                }
            }
        }

        // New Playlist popup
        if (showCreatePlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showCreatePlaylistDialog = false },
                containerColor = Color(0xFF1E1C24),
                title = { Text("Create Playlist", color = Color.White) },
                text = {
                    Column {
                        TextField(
                            value = playlistName,
                            onValueChange = { playlistName = it },
                            placeholder = { Text("Playlist Name") },
                            colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TextField(
                            value = playlistDesc,
                            onValueChange = { playlistDesc = it },
                            placeholder = { Text("Description") },
                            colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (playlistName.isNotBlank()) {
                            viewModel.createPlaylist(playlistName, playlistDesc)
                            showCreatePlaylistDialog = false
                            playlistName = ""
                            playlistDesc = ""
                        }
                    }) {
                        Text("Create", color = Color(0xFFFC2A54))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreatePlaylistDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun LibrarySongRow(song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.artwork,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("${song.artist} • ${song.source}", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

// -------------------------------------------------------------
// 4. JAM SCREEN (SPOTIFY-LIKE REAL-TIME CO-LISTEN)
// -------------------------------------------------------------
@Composable
fun JamScreen(viewModel: MusicViewModel) {
    val isJoined by viewModel.isJoinedJam.collectAsStateWithLifecycle()
    val roomCode by viewModel.jamRoomCode.collectAsStateWithLifecycle()
    val logHistory by viewModel.jamLogs.collectAsStateWithLifecycle()
    val participants by viewModel.jammers.collectAsStateWithLifecycle()

    var inputCode by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Nirvana Jam", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("REAL-TIME GROUP LISTENING SESSIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFC2A54))
        Spacer(modifier = Modifier.height(24.dp))

        if (!isJoined) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C24)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Host a Jam", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Start a synchronized music session. Anyone with your link or invite code can listen along live and vote on tracks!", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.createJam() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFC2A54)),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Create Jam Session", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C24)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Join an Existing Jam", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = inputCode,
                        onValueChange = { inputCode = it },
                        placeholder = { Text("Enter 8-digit Room Code", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0F0E13),
                            unfocusedContainerColor = Color(0xFF0F0E13),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { 
                            if (inputCode.isNotBlank()) {
                                viewModel.joinJam(inputCode)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B2CBF)),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Connect Live", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1C24), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("ACTIVE SESSION", color = Color(0xFFFC2A54), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("Code: $roomCode", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
                Button(
                    onClick = { viewModel.leaveJam() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
                ) {
                    Text("Leave")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Active Listeners (${participants.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(participants) { jammer ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E1C24)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(jammer.avatar, fontSize = 24.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            jammer.name,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (jammer.isHost) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(64.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Real-Time Synchronization Logs", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF07060A), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(logHistory) { log ->
                        Text(
                            text = log,
                            color = if (log.contains("You")) Color(0xFFFC2A54) else Color.LightGray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(80.dp))
    }
}

// -------------------------------------------------------------
// 5. EQUALIZER SCREEN
// -------------------------------------------------------------
@Composable
fun EqualizerScreen(viewModel: MusicViewModel) {
    val bands by viewModel.eqBands.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    var activePreset by remember { mutableStateOf("Custom") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Studio Equalizer",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(Color(0xFF1E1C24), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "Visualizer")
                for (i in 0..12) {
                    val duration = (400..1200).random()
                    val heightScale by infiniteTransition.animateFloat(
                        initialValue = 0.1f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(duration, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "BarHeight"
                    )
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(if (isPlaying) (60 * heightScale).dp else 8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFFFC2A54), Color(0xFF7B2CBF))
                                )
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Custom", "Rock/Grunge", "Classical", "Lo-Fi", "Bass Booster").forEach { preset ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (activePreset == preset) Color(0xFFFC2A54) else Color(0xFF1E1C24))
                        .clickable {
                            activePreset = preset
                            when (preset) {
                                "Rock/Grunge" -> {
                                    viewModel.setEqualizerBand(0, 80)
                                    viewModel.setEqualizerBand(1, 40)
                                    viewModel.setEqualizerBand(2, 60)
                                    viewModel.setEqualizerBand(3, 75)
                                    viewModel.setEqualizerBand(4, 30)
                                }
                                "Classical" -> {
                                    viewModel.setEqualizerBand(0, 30)
                                    viewModel.setEqualizerBand(1, 50)
                                    viewModel.setEqualizerBand(2, 40)
                                    viewModel.setEqualizerBand(3, 60)
                                    viewModel.setEqualizerBand(4, 70)
                                }
                                "Lo-Fi" -> {
                                    viewModel.setEqualizerBand(0, 70)
                                    viewModel.setEqualizerBand(1, 60)
                                    viewModel.setEqualizerBand(2, 30)
                                    viewModel.setEqualizerBand(3, 20)
                                    viewModel.setEqualizerBand(4, 40)
                                }
                                "Bass Booster" -> {
                                    viewModel.setEqualizerBand(0, 100)
                                    viewModel.setEqualizerBand(1, 90)
                                    viewModel.setEqualizerBand(2, 50)
                                    viewModel.setEqualizerBand(3, 40)
                                    viewModel.setEqualizerBand(4, 30)
                                }
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(preset, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val bandLabels = listOf("60Hz (Bass)", "230Hz (Low-Mid)", "910Hz (Mid)", "4kHz (High-Mid)", "14kHz (Treble)")
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            bands.forEach { (band, level) ->
                val label = bandLabels.getOrElse(band) { "Band $band" }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(label, color = Color.LightGray, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("${level - 50} dB", color = Color(0xFFFC2A54), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = level.toFloat(),
                        onValueChange = {
                            activePreset = "Custom"
                            viewModel.setEqualizerBand(band, it.toInt())
                        },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFFFC2A54),
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

// -------------------------------------------------------------
// 6. EXPANDED "NOW PLAYING" SHEET WITH GESTURES, LYRICS, QUEUE
// -------------------------------------------------------------
@Composable
fun NowPlayingSheet(viewModel: MusicViewModel, onClose: () -> Unit) {
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val queue by viewModel.playbackQueue.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimerRemaining.collectAsStateWithLifecycle()

    var showLyrics by remember { mutableStateOf(false) }
    var showQueueTab by remember { mutableStateOf(false) }

    var sleepTimerMinutes by remember { mutableIntStateOf(0) }

    val accentPink = Color(0xFFFC2A54)

    val gradientColors = when (currentSong?.genre) {
        "Rock/Grunge" -> listOf(Color(0xFF2E0810), Color(0xFF120306), Color(0xFF0F0E13))
        "Lo-Fi & Chill" -> listOf(Color(0xFF1E0A2D), Color(0xFF0A0312), Color(0xFF0F0E13))
        "Classical" -> listOf(Color(0xFF08221D), Color(0xFF020E0C), Color(0xFF0F0E13))
        "EDM & Electronic" -> listOf(Color(0xFF0E1A33), Color(0xFF030A17), Color(0xFF0F0E13))
        else -> listOf(Color(0xFF1C1A22), Color(0xFF100F15), Color(0xFF0F0E13))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = gradientColors
                    )
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    if (dragAmount.y > 35) {
                        onClose()
                    }
                }
            }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, "Down", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "NOW PLAYING",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.LightGray,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { showQueueTab = !showQueueTab }) {
                    Icon(
                        imageVector = if (showQueueTab) Icons.Default.MusicNote else Icons.Default.QueueMusic,
                        contentDescription = "Queue Toggle",
                        tint = if (showQueueTab) accentPink else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (showQueueTab) {
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Text("Up Next", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    if (queue.isEmpty()) {
                        Text("Queue is empty", color = Color.Gray)
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            itemsIndexed(queue) { index, song ->
                                val isCurrent = song.id == currentSong?.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isCurrent) Color(0xFF2E1A22) else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${index + 1}", color = Color.Gray, modifier = Modifier.width(28.dp))
                                    AsyncImage(
                                        model = song.artwork,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(song.title, color = if (isCurrent) accentPink else Color.White, fontWeight = FontWeight.Bold)
                                        Text(song.artist, color = Color.Gray, fontSize = 12.sp)
                                    }

                                    IconButton(onClick = { viewModel.reorderQueue(index, index - 1) }) {
                                        Icon(Icons.Default.ArrowUpward, "Move Up", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = { viewModel.reorderQueue(index, index + 1) }) {
                                        Icon(Icons.Default.ArrowDownward, "Move Down", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = { viewModel.removeFromQueue(song.id) }) {
                                        Icon(Icons.Default.Close, "Remove", tint = Color.Gray, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (showLyrics) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(24.dp)
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.TopStart
                        ) {
                            Text(
                                text = currentSong?.lyrics ?: "[No lyrics found for this CC track]",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                lineHeight = 36.sp,
                                modifier = Modifier.clickable { showLyrics = false }
                            )
                        }
                    } else {
                        val artworkScale by animateFloatAsState(
                            targetValue = if (isPlaying) 1.0f else 0.82f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "ArtworkScale"
                        )

                        Spacer(modifier = Modifier.weight(0.2f))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    scaleX = artworkScale
                                    scaleY = artworkScale
                                }
                                .shadow(24.dp, RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.DarkGray)
                        ) {
                            AsyncImage(
                                model = currentSong?.artwork,
                                contentDescription = "Active Album Art",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.weight(0.2f))
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentSong?.title ?: "Unknown Song",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${currentSong?.artist ?: "Unknown artist"} • ${currentSong?.source ?: "Free Cloud"}",
                                    fontSize = 17.sp,
                                    color = Color.LightGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(onClick = { showLyrics = !showLyrics }) {
                                Icon(
                                    imageVector = Icons.Default.Lyrics,
                                    contentDescription = "Lyrics Toggle",
                                    tint = if (showLyrics) accentPink else Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                val progressRatio = if (duration > 0) progress.toFloat() / duration else 0f
                Slider(
                    value = progress.toFloat(),
                    onValueChange = { viewModel.seekTo(it.toLong()) },
                    valueRange = 0f..(if (duration > 0) duration.toFloat() else 100f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )

                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(formatTime(progress), color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(formatTime(duration), color = Color.LightGray, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                val isShuffle by viewModel.isShuffle.collectAsStateWithLifecycle()
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) accentPink else Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(onClick = { viewModel.prev() }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { viewModel.playOrPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause Large",
                        tint = Color.Black,
                        modifier = Modifier.size(42.dp)
                    )
                }

                IconButton(onClick = { viewModel.next() }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                val isRepeat by viewModel.isRepeat.collectAsStateWithLifecycle()
                IconButton(onClick = { viewModel.toggleRepeat() }) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (isRepeat) accentPink else Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        sleepTimerMinutes = when (sleepTimerMinutes) {
                            0 -> 15
                            15 -> 30
                            30 -> 45
                            45 -> 60
                            else -> 0
                        }
                        viewModel.setSleepTimer(sleepTimerMinutes)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "Sleep Timer",
                        tint = if (sleepTimer > 0) accentPink else Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (sleepTimer > 0) "Timer: ${formatTime(sleepTimer)}" else "Sleep Timer",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }

                IconButton(onClick = { 
                    Toast.makeText(viewModel.getApplication(), "Casting to home network devices...", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.Cast, "Cast", tint = Color.White)
                }
            }
        }
    }
}

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

