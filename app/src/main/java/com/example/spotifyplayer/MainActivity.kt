package com.example.spotifyplayer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.spotifyplayer.ui.theme.SpotifyPlayerTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpotifyPlayerTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(false) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var currentSongIndex by remember { mutableIntStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }

    // Usamos mediaPlayerRef para controlar el MediaPlayer sin provocar recomposiciones constantes
    val mediaPlayerRef = remember { mutableStateOf<MediaPlayer?>(null) }

    var showPlayerDetail by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
   

    // Actualización periódica de 'progress' mientras suena la canción
    LaunchedEffect(mediaPlayerRef.value) {
        while (mediaPlayerRef.value != null) {
            val mp = mediaPlayerRef.value!!
            if (mp.isPlaying) {
                progress = mp.currentPosition.toFloat() / mp.duration.toFloat()
            }
            delay(500L)
        }
    }

    // Lanzador de permiso para Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionGranted = isGranted
        if (isGranted) {
            songs = loadSongs(context)
        }
    }

    // Solicitar permiso al iniciar
    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(permission)
        } else {
            permissionGranted = true
            songs = loadSongs(context)
        }
    }

    // Liberar mediaPlayer al cerrar la actividad
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayerRef.value?.release()
            mediaPlayerRef.value = null
        }
    }

    val currentSong = songs.getOrNull(currentSongIndex)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!showPlayerDetail && currentSong != null) {
                BottomPlayerBar(
                    song = currentSong,
                    isPlaying = isPlaying,
                    onPlayPauseClick = {
                        mediaPlayerRef.value?.let { mp ->
                            if (isPlaying) mp.pause() else mp.start()
                            isPlaying = mp.isPlaying
                        }
                    },
                    onPreviousClick = {
                        if (currentSongIndex > 0) {
                            currentSongIndex--
                            isPlaying = true
                            progress = 0f
                            playSong(mediaPlayerRef, songs[currentSongIndex])
                        }
                    },
                    onNextClick = {
                        if (currentSongIndex < songs.size - 1) {
                            currentSongIndex++
                            isPlaying = true
                            progress = 0f
                            playSong(mediaPlayerRef, songs[currentSongIndex])
                        }
                    },
                    onBarClick = {
                        showPlayerDetail = true
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (permissionGranted) {
                if (songs.isEmpty()) {
                    Text("No se encontraron canciones 🎶", modifier = Modifier.padding(16.dp))
                } else {
                    SongList(songs = songs, onSongClick = { selected ->
                        currentSongIndex = songs.indexOf(selected)
                        isPlaying = true
                        progress = 0f
                        playSong(mediaPlayerRef, selected)
                    })
                }
            } else {
                Text(
                    "Necesitamos tu permiso para acceder a tu música.",
                    modifier = Modifier.padding(16.dp)
                )
            }

            AnimatedVisibility(
                visible = showPlayerDetail && currentSong != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                PlayerDetailSheet(
                    song = currentSong!!,
                    isPlaying = isPlaying,
                    progress = progress,
                    onSeekTo = { sliderPos ->
                        val duration = mediaPlayerRef.value?.duration ?: 0
                        val newPosMs = (duration * sliderPos).toInt()
                        mediaPlayerRef.value?.seekTo(newPosMs)
                    },
                    onPlayPauseClick = {
                        mediaPlayerRef.value?.let { mp ->
                            if (isPlaying) mp.pause() else mp.start()
                            isPlaying = mp.isPlaying
                        }
                    },
                    onPreviousClick = {
                        if (currentSongIndex > 0) {
                            currentSongIndex--
                            isPlaying = true
                            progress = 0f
                            playSong(mediaPlayerRef, songs[currentSongIndex])
                        }
                    },
                    onNextClick = {
                        if (currentSongIndex < songs.size - 1) {
                            currentSongIndex++
                            isPlaying = true
                            progress = 0f
                            playSong(mediaPlayerRef, songs[currentSongIndex])
                        }
                    },
                    onDismiss = {
                        showPlayerDetail = false
                    }
                )
            }
        }
    }
}

/** Función para reproducir una canción usando mediaPlayerRef */
fun playSong(
    mediaPlayerState: androidx.compose.runtime.MutableState<MediaPlayer?>,
    song: Song
) {
    // Liberar si ya existe un MediaPlayer
    mediaPlayerState.value?.release()

    mediaPlayerState.value = MediaPlayer().apply {
        setDataSource(song.data)
        setOnPreparedListener {
            start()
        }
        // Si deseas avanzar automáticamente a la siguiente pista al terminar:
        // setOnCompletionListener { /* manejar avance */ }
        prepareAsync()
    }
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
                Text(song.title, style = MaterialTheme.typography.titleMedium)
                Text(song.artist, style = MaterialTheme.typography.bodySmall)
                Text(formatDuration(song.duration), style = MaterialTheme.typography.labelSmall)
                Divider(modifier = Modifier.padding(top = 8.dp))
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
        shadowElevation = 8.dp,
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onBarClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Gray)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = song.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousClick) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior")
                }
                IconButton(onClick = onPlayPauseClick) {
                    if (isPlaying) {
                        Icon(Icons.Default.Pause, contentDescription = "Pausar")
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir")
                    }
                }
                IconButton(onClick = onNextClick) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Siguiente")
                }
            }
        }
    }
}

@Composable
fun PlayerDetailSheet(
    song: Song,
    isPlaying: Boolean,
    progress: Float,               // progreso real (0f..1f)
    onSeekTo: (Float) -> Unit,     // callback cuando suelta el Slider
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onDismiss: () -> Unit
) {
    // Este estado guarda el “progreso en pantalla” mientras arrastra
    var sliderPosition by remember { mutableFloatStateOf(progress) }

    // Cada vez que el progreso real cambie (canción avanza),
    // sincronizamos el sliderPosition si el usuario no está arrastrando
    LaunchedEffect(progress) {
        sliderPosition = progress
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(20.dp)),
            tonalElevation = 12.dp,
            shadowElevation = 12.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .clickable(enabled = false) { /* evita cerrar con clic interno */ },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Gray)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(song.title, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Text(song.artist, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                // Barra de progreso separada en “value” y “onValueChangeFinished”
                Slider(
                    value = sliderPosition,
                    onValueChange = { newPos ->
                        sliderPosition = newPos
                    },
                    onValueChangeFinished = {
                        onSeekTo(sliderPosition)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row {
                    IconButton(onClick = onPreviousClick) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior")
                    }
                    IconButton(onClick = onPlayPauseClick) {
                        if (isPlaying) {
                            Icon(
                                Icons.Default.Pause,
                                contentDescription = "Pausar",
                                modifier = Modifier.size(48.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Reproducir",
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                    IconButton(onClick = onNextClick) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Siguiente")
                    }
                }
            }
        }
    }
}

fun loadSongs(context: Context): List<Song> {
    val list = mutableListOf<Song>()
    val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

    val cursor = context.contentResolver.query(uri, projection, selection, null, sortOrder)
    cursor?.use {
        val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

        while (it.moveToNext()) {
            val id = it.getLong(idCol)
            val title = it.getString(titleCol) ?: "Desconocido"
            val artist = it.getString(artistCol) ?: "Desconocido"
            val duration = it.getLong(durationCol)
            val data = it.getString(dataCol)
            list.add(Song(id, title, artist, duration, data))
        }
    }
    return list
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
