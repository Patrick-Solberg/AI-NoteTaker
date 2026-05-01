package com.example.ai_notetaker.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Entity(
    tableName = "note_entries",
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["noteId"])]
)
data class NoteEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteId: Long,
    val content: String,
    val entryType: EntryType,
    val recordingId: Long? = null, // Link to AudioRecording for transcription entries
    val createdAt: Long = System.currentTimeMillis()
)

enum class EntryType {
    TEXT,
    TRANSCRIPTION,
    IMAGE // For image entries, content field stores the file path
}

class EntryTypeConverter {
    @TypeConverter
    fun fromEntryType(value: EntryType): String {
        return value.name
    }
    
    @TypeConverter
    fun toEntryType(value: String): EntryType {
        return EntryType.valueOf(value)
    }
}
