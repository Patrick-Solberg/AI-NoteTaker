package com.example.ai_notetaker.ui.component

import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.IOException

@Composable
fun AudioPlayer(
    audioFilePath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Initialize MediaPlayer when audioFilePath changes
    LaunchedEffect(audioFilePath) {
        // Release previous player if exists
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        currentPosition = 0
        duration = 0
        errorMessage = null
        
        try {
            val player = MediaPlayer().apply {
                setDataSource(audioFilePath)
                prepare()
                setOnCompletionListener {
                    isPlaying = false
                    currentPosition = 0
                }
                setOnErrorListener { _, what, extra ->
                    android.util.Log.e("AudioPlayer", "MediaPlayer error: what=$what, extra=$extra")
                    errorMessage = "Playback error"
                    isPlaying = false
                    true
                }
            }
            duration = player.duration
            mediaPlayer = player
            android.util.Log.d("AudioPlayer", "MediaPlayer initialized for: $audioFilePath, duration: $duration")
        } catch (e: IOException) {
            android.util.Log.e("AudioPlayer", "Failed to initialize MediaPlayer", e)
            errorMessage = "Failed to load audio file"
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayer", "Unexpected error initializing MediaPlayer", e)
            errorMessage = "Unexpected error"
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        player.stop()
                    }
                    player.release()
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayer", "Error releasing MediaPlayer", e)
                }
            }
            mediaPlayer = null
        }
    }
    
    // Handle play/pause
    LaunchedEffect(isPlaying) {
        mediaPlayer?.let { player ->
            try {
                if (isPlaying) {
                    if (!player.isPlaying) {
                        player.start()
                        android.util.Log.d("AudioPlayer", "Started playback")
                    }
                } else {
                    if (player.isPlaying) {
                        player.pause()
                        android.util.Log.d("AudioPlayer", "Paused playback")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayer", "Error controlling playback", e)
                errorMessage = "Playback error"
                isPlaying = false
            }
        }
    }
    
    // Update position while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying && mediaPlayer != null) {
            kotlinx.coroutines.delay(100)
            mediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        currentPosition = player.currentPosition
                    } else if (isPlaying) {
                        // Player stopped unexpectedly
                        isPlaying = false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayer", "Error getting position", e)
                    isPlaying = false
                }
            }
        }
    }
    
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = {
                if (mediaPlayer != null) {
                    isPlaying = !isPlaying
                } else {
                    android.util.Log.w("AudioPlayer", "MediaPlayer not initialized, cannot play")
                }
            },
            enabled = mediaPlayer != null && errorMessage == null
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play"
            )
        }
        
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        } else if (duration > 0) {
            val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
            )
            
            Text(
                text = formatTime(currentPosition) + " / " + formatTime(duration),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        } else {
            Text(
                text = "Loading...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun formatTime(milliseconds: Int): String {
    val seconds = milliseconds / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%d:%02d", minutes, remainingSeconds)
}
