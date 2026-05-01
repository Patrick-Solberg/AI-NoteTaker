package com.example.ai_notetaker.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ai_notetaker.data.model.AudioRecording
import com.example.ai_notetaker.data.model.NoteEntry
import com.example.ai_notetaker.data.model.EntryType
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NoteEntryItem(
    entry: NoteEntry,
    recording: AudioRecording? = null,
    onDelete: () -> Unit = {},
    onImageClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // For IMAGE entries, use ImageEntryItem component
    if (entry.entryType == EntryType.IMAGE) {
        ImageEntryItem(
            imageFilePath = entry.content,
            createdAt = entry.createdAt,
            onImageClick = onImageClick ?: {},
            onDelete = onDelete,
            modifier = modifier
        )
        return
    }
    
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val dateText = dateFormat.format(Date(entry.createdAt))
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (entry.entryType) {
                EntryType.TEXT -> MaterialTheme.colorScheme.surfaceVariant
                EntryType.TRANSCRIPTION -> MaterialTheme.colorScheme.secondaryContainer
                EntryType.IMAGE -> MaterialTheme.colorScheme.tertiaryContainer
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (entry.entryType) {
                        EntryType.TEXT -> "Text Entry"
                        EntryType.TRANSCRIPTION -> "Voice Transcription"
                        EntryType.IMAGE -> "Image Entry"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Entry",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            // Audio player for transcription entries
            if (entry.entryType == EntryType.TRANSCRIPTION && recording != null) {
                Spacer(modifier = Modifier.height(8.dp))
                AudioPlayer(
                    audioFilePath = recording.filePath,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
