package com.example.spotifyplayer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.spotifyplayer.ui.theme.SpotifyPlayerTheme
import kotlinx.coroutines.delay

// -----------------------------------------------------------------------------
// Colores estilo Spotify
// -----------------------------------------------------------------------------
private val SpotifyBackground    = Color(0xFF121212)
private val SpotifySurface       = Color(0xFF181818)
private val SpotifySurfaceAlt    = Color(0xFF282828)
private val SpotifyAccent        = Color(0xFF1DB954)
private val SpotifyTextPrimary   = Color(0xFFFFFFFF)
private val SpotifyTextSecondary = Color(0xFFB3B3B3)

// -----------------------------------------------------------------------------
// Función playSong a nivel top-level
// -----------------------------------------------------------------------------
fun playSong(
    mediaPlayerState: MutableState<MediaPlayer?>,
    song: Song
) {
    mediaPlayerState.value?.release()
    mediaPlayerState.value = MediaPlayer().apply {
        setDataSource(song.data)
        setOnPreparedListener { start() }
        prepareAsync()
    }
}

// -----------------------------------------------------------------------------
// MainActivity
// -----------------------------------------------------------------------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pantalla full-screen con status bar transparente
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            SpotifyPlayerTheme {
                MainScreen()
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Composable principal
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context            = LocalContext.current
    var permissionGranted by remember { mutableStateOf(false) }
    var songs             by remember { mutableStateOf<List<Song>>(emptyList()) }
    var currentSongIndex  by remember { mutableStateOf(-1) }
    var isPlaying         by remember { mutableStateOf(false) }
    val mediaPlayerRef    = remember { mutableStateOf<MediaPlayer?>(null) }
    var showPlayerDetail  by remember { mutableStateOf(false) }
    var progress          by remember { mutableStateOf(0f) }

    var searchQuery       by remember { mutableStateOf(TextFieldValue("")) }
    val filteredSongs     = remember(songs, searchQuery) {
        if (searchQuery.text.isBlank()) songs
        else songs.filter {
            val q = searchQuery.text.trim().lowercase()
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }
    }

    // Reproduce canción y configura callback de siguiente automática
    fun playWithAutoNext(index: Int) {
        currentSongIndex = index
        progress = 0f
        isPlaying = true
        playSong(mediaPlayerRef, songs[index])
        mediaPlayerRef.value?.setOnCompletionListener {
            if (currentSongIndex < songs.size - 1) playWithAutoNext(currentSongIndex + 1)
            else isPlaying = false
        }
    }

    // Actualiza barra de progreso cada 500ms
    LaunchedEffect(mediaPlayerRef.value) {
        while (mediaPlayerRef.value != null) {
            mediaPlayerRef.value?.let { mp ->
                if (mp.isPlaying) {
                    progress = mp.currentPosition.toFloat() / mp.duration.toFloat()
                }
            }
            delay(500L)
        }
    }

    // Lanzador para permiso
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) songs = loadSongs(context)
    }

    // Solicita permiso al iniciar
    LaunchedEffect(Unit) {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(perm)
        } else {
            permissionGranted = true
            songs = loadSongs(context)
        }
    }

    // Libera MediaPlayer al salir
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayerRef.value?.release()
            mediaPlayerRef.value = null
        }
    }

    val currentSong = songs.getOrNull(currentSongIndex)

    Scaffold(
        containerColor = SpotifyBackground,
        topBar = {
            // Barra superior transparente
            TopAppBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SpotifyBackground,
                    titleContentColor = SpotifyTextPrimary
                )
            )
            // Buscador estilo Spotify
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SpotifySurfaceAlt,
                tonalElevation = 4.dp
            ) {
                SearchBar(
                    value         = searchQuery,
                    onValueChange = { searchQuery = it },
                    onClear       = { searchQuery = TextFieldValue("") }
                )
            }
        },
        bottomBar = {
            // Barra inferior player
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                if (!showPlayerDetail && currentSong != null) {
                    BottomPlayerBar(
                        song             = currentSong,
                        isPlaying        = isPlaying,
                        onPlayPauseClick = {
                            mediaPlayerRef.value?.let { mp ->
                                if (isPlaying) mp.pause() else mp.start()
                                isPlaying = mp.isPlaying
                            }
                        },
                        onPreviousClick  = { if (currentSongIndex > 0) playWithAutoNext(currentSongIndex - 1) },
                        onNextClick      = { if (currentSongIndex < songs.size - 1) playWithAutoNext(currentSongIndex + 1) },
                        onBarClick       = { showPlayerDetail = true }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(SpotifyBackground)
        ) {
            when {
                !permissionGranted -> Text(
                    "Necesitamos tu permiso para acceder a tu música.",
                    color = SpotifyTextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
                filteredSongs.isEmpty() -> Text(
                    "No se encontraron resultados 🎶",
                    color = SpotifyTextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
                else -> SongList(filteredSongs) { selected ->
                    playWithAutoNext(songs.indexOf(selected))
                }
            }

            // Detalle con transición slide + fade
            AnimatedVisibility(
                visible = showPlayerDetail && currentSong != null,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(200)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(200))
            ) {
                currentSong?.let {
                    PlayerDetailSheet(
                        song             = it,
                        isPlaying        = isPlaying,
                        progress         = progress,
                        onSeekTo         = { pos ->
                            mediaPlayerRef.value?.seekTo(
                                (mediaPlayerRef.value!!.duration * pos).toInt()
                            )
                        },
                        onPlayPauseClick = {
                            mediaPlayerRef.value?.let { mp ->
                                if (isPlaying) mp.pause() else mp.start()
                                isPlaying = mp.isPlaying
                            }
                        },
                        onPreviousClick  = { if (currentSongIndex > 0) playWithAutoNext(currentSongIndex - 1) },
                        onNextClick      = { if (currentSongIndex < songs.size - 1) playWithAutoNext(currentSongIndex + 1) },
                        onDismiss        = { showPlayerDetail = false }
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Componentes reutilizables
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onClear: () -> Unit
) {
    TextField(
        value            = value,
        onValueChange    = onValueChange,
        placeholder      = {
            Text("Buscar canción o artista", color = SpotifyTextSecondary)
        },
        leadingIcon      = {
            Icon(Icons.Default.Search, contentDescription = null, tint = SpotifyTextSecondary)
        },
        trailingIcon     = {
            if (value.text.isNotEmpty()) IconButton(onClick = onClear) {
                Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = SpotifyTextSecondary)
            }
        },
        singleLine       = true,
        shape            = RoundedCornerShape(50),
        textStyle        = LocalTextStyle.current.copy(color = SpotifyTextPrimary),
        modifier         = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(56.dp),
        colors           = TextFieldDefaults.colors(
            focusedContainerColor   = SpotifySurfaceAlt,
            unfocusedContainerColor = SpotifySurfaceAlt,
            disabledContainerColor  = SpotifySurfaceAlt,
            focusedIndicatorColor   = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor  = Color.Transparent,
            cursorColor             = SpotifyAccent
        )
    )
}

@Composable
fun SongList(songs: List<Song>, onSongClick: (Song) -> Unit) {
    LazyColumn {
        items(songs) { song ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSongClick(song) }
                    .padding(vertical = 12.dp, horizontal = 16.dp)
            ) {
                Text(song.title, color = SpotifyTextPrimary, fontWeight = FontWeight.SemiBold)
                Text(song.artist, color = SpotifyTextSecondary, style = MaterialTheme.typography.bodySmall)
                Text(formatDuration(song.duration), color = SpotifyTextSecondary, style = MaterialTheme.typography.labelSmall)
                Divider(
                    color = SpotifySurfaceAlt,
                    thickness = 1.dp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun BottomPlayerBar(
    song: Song,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onBarClick: () -> Unit
) {
    Surface(
        color          = SpotifySurface,
        tonalElevation = 8.dp,
        shadowElevation= 8.dp,
        modifier       = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onBarClick() }
    ) {
        Row(
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement= Arrangement.SpaceBetween,
            modifier             = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(albumArtUri(song.albumId))
                    .error(R.drawable.default_cover)
                    .placeholder(R.drawable.default_cover)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text      = song.title,
                    fontWeight= FontWeight.Bold,
                    fontSize  = 16.sp,
                    color     = SpotifyTextPrimary,
                    maxLines  = 1,
                    overflow  = TextOverflow.Ellipsis
                )
                Text(
                    text     = song.artist,
                    color    = SpotifyTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousClick) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = SpotifyTextPrimary)
                }
                IconButton(onClick = onPlayPauseClick) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = SpotifyTextPrimary
                    )
                }
                IconButton(onClick = onNextClick) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, tint = SpotifyTextPrimary)
                }
            }
        }
    }
}

@Composable
fun PlayerDetailSheet(
    song: Song,
    isPlaying: Boolean,
    progress: Float,
    onSeekTo: (Float) -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onDismiss: () -> Unit
) {
    var sliderPosition by remember { mutableStateOf(progress) }
    LaunchedEffect(progress) { sliderPosition = progress }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier       = Modifier.fillMaxWidth(0.9f).clip(RoundedCornerShape(20.dp)),
            tonalElevation = 12.dp,
            shadowElevation= 12.dp,
            color          = SpotifySurface
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .clickable(enabled = false) { },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(albumArtUri(song.albumId))
                        .error(R.drawable.default_cover)
                        .placeholder(R.drawable.default_cover)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(16.dp))
                Text(song.title, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = SpotifyTextPrimary)
                Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = SpotifyTextSecondary)
                Spacer(Modifier.height(16.dp))
                Slider(
                    value                = sliderPosition,
                    onValueChange        = { sliderPosition = it },
                    onValueChangeFinished= { onSeekTo(sliderPosition) },
                    modifier             = Modifier.fillMaxWidth(),
                    colors               = SliderDefaults.colors(
                        thumbColor       = SpotifyAccent,
                        activeTrackColor = SpotifyAccent
                    )
                )
                Spacer(Modifier.height(16.dp))
                Row {
                    IconButton(onClick = onPreviousClick) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = SpotifyTextPrimary)
                    }
                    IconButton(onClick = onPlayPauseClick) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint     = SpotifyAccent
                        )
                    }
                    IconButton(onClick = onNextClick) {
                        Icon(Icons.Default.SkipNext, contentDescription = null, tint = SpotifyTextPrimary)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Funciones originales: albumArtUri, loadSongs, formatDuration
// -----------------------------------------------------------------------------
fun albumArtUri(albumId: Long): Uri =
    ContentUris.withAppendedId(
        "content://media/external/audio/albumart".toUri(),
        albumId
    )

fun loadSongs(context: Context): List<Song> {
    val list = mutableListOf<Song>()
    val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.ALBUM_ID
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

    context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
        val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val dataCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val albumIdCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

        while (cursor.moveToNext()) {
            val id       = cursor.getLong(idCol)
            val title    = cursor.getString(titleCol) ?: "Desconocido"
            val artist   = cursor.getString(artistCol) ?: "Desconocido"
            val duration = cursor.getLong(durationCol)
            val data     = cursor.getString(dataCol)
            val albumId  = cursor.getLong(albumIdCol)
            list.add(Song(id, title, artist, duration, data, albumId))
        }
    }
    return list
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes      = totalSeconds / 60
    val seconds      = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
