package com.example.ai_notetaker.data.repository

import android.content.Context
import com.example.ai_notetaker.data.audio.AudioRecorder
import com.example.ai_notetaker.data.audio.AudioRecordingException
import com.example.ai_notetaker.data.database.NoteDatabase
import com.example.ai_notetaker.data.model.AudioRecording
import com.example.ai_notetaker.data.model.Note
import com.example.ai_notetaker.data.model.NoteEntry
import com.example.ai_notetaker.data.model.EntryType
import com.example.ai_notetaker.data.remote.InsufficientQuotaException
import com.example.ai_notetaker.data.remote.NetworkException
import com.example.ai_notetaker.data.remote.OpenAIService
import com.example.ai_notetaker.data.remote.SummaryException
import com.example.ai_notetaker.data.remote.TranscriptionException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import android.graphics.Bitmap
import android.graphics.BitmapFactory

class NoteRepository(context: Context) {
    private val database = NoteDatabase.getDatabase(context)
    private val noteDao = database.noteDao()
    private val audioRecordingDao = database.audioRecordingDao()
    private val noteEntryDao = database.noteEntryDao()
    private val audioRecorder = AudioRecorder(context)
    private val openAIService = OpenAIService(context)
    private val backgroundScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()
    )
    
    fun getAllNotes(): Flow<List<Note>> = noteDao.getAllNotes()
    
    suspend fun getNoteById(id: Long): Note? = noteDao.getNoteById(id)
    
    suspend fun getRecordingsByNoteId(noteId: Long): Flow<List<AudioRecording>> =
        audioRecordingDao.getRecordingsByNoteId(noteId)
    
    suspend fun getRecordingById(recordingId: Long): AudioRecording? =
        audioRecordingDao.getRecordingById(recordingId)
    
    suspend fun getEntriesByNoteId(noteId: Long): Flow<List<NoteEntry>> =
        noteEntryDao.getEntriesByNoteId(noteId)
    
    suspend fun createNote(): Long {
        val note = Note()
        return noteDao.insertNote(note)
    }
    
    suspend fun addTextEntry(noteId: Long, text: String) {
        if (text.isBlank()) return
        
        val entry = NoteEntry(
            noteId = noteId,
            content = text,
            entryType = EntryType.TEXT
        )
        noteEntryDao.insertEntry(entry)
        
        // Update note timestamp
        val note = noteDao.getNoteById(noteId) ?: return
        noteDao.updateNote(note.copy(updatedAt = System.currentTimeMillis()))
        
        // Generate summary immediately
        generateSummaryImmediately(noteId)
    }
    
    suspend fun addImageEntry(noteId: Long, imageFilePath: String) {
        val entry = NoteEntry(
            noteId = noteId,
            content = imageFilePath, // Store file path in content field
            entryType = EntryType.IMAGE
        )
        noteEntryDao.insertEntry(entry)
        
        // Update note timestamp
        val note = noteDao.getNoteById(noteId) ?: return
        noteDao.updateNote(note.copy(updatedAt = System.currentTimeMillis()))
        
        // Don't generate summary for image entries (they're excluded)
    }
    
    private fun getImageStorageDirectory(context: Context): File {
        val imagesDir = File(context.filesDir, "images")
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }
        return imagesDir
    }
    
    suspend fun saveImageFile(context: Context, bitmap: Bitmap, noteId: Long): String {
        val imagesDir = getImageStorageDirectory(context)
        val fileName = "note_${noteId}_${System.currentTimeMillis()}.jpg"
        val imageFile = File(imagesDir, fileName)
        
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        
        return imageFile.absolutePath
    }
    
    private fun deleteImageFile(filePath: String) {
        val imageFile = File(filePath)
        if (imageFile.exists()) {
            imageFile.delete()
        }
    }
    
    suspend fun startRecording(noteId: Long): String {
        return try {
            audioRecorder.startRecording(noteId)
        } catch (e: AudioRecordingException) {
            throw e
        }
    }
    
    suspend fun stopRecording(noteId: Long): Long {
        val filePath = try {
            audioRecorder.stopRecording()
        } catch (e: AudioRecordingException) {
            throw e
        }
        
        val file = File(filePath)
        val fileSize = file.length()
        
        val recording = AudioRecording(
            noteId = noteId,
            filePath = filePath,
            recordedAt = System.currentTimeMillis(),
            fileSize = fileSize
        )
        
        val recordingId = audioRecordingDao.insertRecording(recording)
        
        // Transcribe in background (fire and forget)
        backgroundScope.launch {
            transcribeRecording(recordingId, file)
        }
        
        return recordingId
    }
    
    private suspend fun transcribeRecording(recordingId: Long, audioFile: File) {
        try {
            val transcription = openAIService.transcribeAudio(audioFile)
            
            val recording = audioRecordingDao.getRecordingById(recordingId) ?: return
            val updatedRecording = recording.copy(transcription = transcription)
            audioRecordingDao.updateRecording(updatedRecording)
            
            // Create note entry from transcription
            val noteId = recording.noteId
            val entry = NoteEntry(
                noteId = noteId,
                content = transcription,
                entryType = EntryType.TRANSCRIPTION,
                recordingId = recordingId // Link to audio recording
            )
            noteEntryDao.insertEntry(entry)
            
            // Update note timestamp
            val note = noteDao.getNoteById(noteId)
            note?.let {
                noteDao.updateNote(it.copy(updatedAt = System.currentTimeMillis()))
            }
            
            // Generate summary immediately
            generateSummaryImmediately(noteId)
        } catch (e: InsufficientQuotaException) {
            // Quota exceeded - mark recording with error (could store error state)
            // For now, just leave transcription as null
        } catch (e: TranscriptionException) {
            // Transcription failed - could retry later
            // Leave transcription as null for now
        } catch (e: NetworkException) {
            // Network error - could retry later
            // Leave transcription as null for now
        }
    }
    
    private fun generateSummaryImmediately(noteId: Long) {
        backgroundScope.launch {
            try {
                android.util.Log.d("NoteRepository", "Auto-generating summary for note $noteId")
                generateSummary(noteId)
                android.util.Log.d("NoteRepository", "Auto-summary generation completed for note $noteId")
            } catch (e: InsufficientQuotaException) {
                android.util.Log.e("NoteRepository", "Quota exceeded during auto-summary", e)
            } catch (e: Exception) {
                android.util.Log.e("NoteRepository", "Auto-summary generation failed", e)
            }
        }
    }
    
    suspend fun generateSummaryManually(noteId: Long) {
        android.util.Log.d("NoteRepository", "Manually generating summary for note $noteId")
        generateSummary(noteId)
        android.util.Log.d("NoteRepository", "Manual summary generation completed for note $noteId")
    }
    
    private suspend fun generateSummary(noteId: Long) {
        android.util.Log.d("NoteRepository", "=== GENERATE SUMMARY START ===")
        android.util.Log.d("NoteRepository", "Note ID: $noteId")
        
        val note = noteDao.getNoteById(noteId)
        if (note == null) {
            android.util.Log.e("NoteRepository", "Note not found with ID: $noteId")
            return
        }
        android.util.Log.d("NoteRepository", "Note found, current summary: ${note.summary?.take(50)}...")
        
        // Collect all entries
        android.util.Log.d("NoteRepository", "Collecting entries for note $noteId...")
        val entries = noteEntryDao.getEntriesByNoteId(noteId).first()
        android.util.Log.d("NoteRepository", "Found ${entries.size} entries")
        
        if (entries.isEmpty()) {
            android.util.Log.w("NoteRepository", "Skipping summary generation - no entries")
            return
        }
        
        // Log each entry
        entries.forEachIndexed { index, entry ->
            android.util.Log.d("NoteRepository", "Entry $index: type=${entry.entryType}, length=${entry.content.length}, preview=${entry.content.take(50)}...")
        }
        
        // Filter out IMAGE entries from summary generation
        val textEntries = entries.filter { it.entryType != EntryType.IMAGE }
        
        if (textEntries.isEmpty()) {
            android.util.Log.w("NoteRepository", "Skipping summary generation - no text entries (only images)")
            return
        }
        
        // Combine all text entries
        val fullText = textEntries.joinToString("\n\n") { it.content }
        android.util.Log.d("NoteRepository", "Combined text length: ${fullText.length}")
        android.util.Log.d("NoteRepository", "Combined text preview: ${fullText.take(300)}...")
        
        if (fullText.isBlank()) {
            android.util.Log.w("NoteRepository", "Combined text is blank, skipping summary")
            return
        }
        
        android.util.Log.d("NoteRepository", "Calling OpenAI service to generate summary...")
        
        try {
            val summaryText = openAIService.generateSummary(fullText)
            android.util.Log.d("NoteRepository", "Summary received from OpenAI, length: ${summaryText.length}")
            android.util.Log.d("NoteRepository", "Summary content: $summaryText")
            
            // Generate title from summary
            android.util.Log.d("NoteRepository", "Generating title from summary...")
            val generatedTitle = try {
                openAIService.generateTitle(summaryText)
            } catch (e: Exception) {
                android.util.Log.e("NoteRepository", "Failed to generate title, keeping existing title", e)
                note.title // Keep existing title if generation fails
            }
            android.util.Log.d("NoteRepository", "Generated title: $generatedTitle")
            
            val updatedNote = note.copy(
                title = generatedTitle,
                summary = summaryText,
                updatedAt = System.currentTimeMillis()
            )
            
            android.util.Log.d("NoteRepository", "Updating note in database...")
            noteDao.updateNote(updatedNote)
            android.util.Log.d("NoteRepository", "=== SUMMARY AND TITLE SAVED SUCCESSFULLY ===")
        } catch (e: InsufficientQuotaException) {
            android.util.Log.e("NoteRepository", "=== QUOTA EXCEEDED ===", e)
            throw e
        } catch (e: SummaryException) {
            android.util.Log.e("NoteRepository", "=== SUMMARY EXCEPTION ===", e)
            android.util.Log.e("NoteRepository", "Exception message: ${e.message}")
            android.util.Log.e("NoteRepository", "Exception cause: ${e.cause}")
            throw e
        } catch (e: NetworkException) {
            android.util.Log.e("NoteRepository", "=== NETWORK EXCEPTION ===", e)
            android.util.Log.e("NoteRepository", "Exception message: ${e.message}")
            android.util.Log.e("NoteRepository", "Exception cause: ${e.cause}")
            throw e
        } catch (e: Exception) {
            android.util.Log.e("NoteRepository", "=== UNEXPECTED EXCEPTION ===", e)
            android.util.Log.e("NoteRepository", "Exception type: ${e.javaClass.name}")
            android.util.Log.e("NoteRepository", "Exception message: ${e.message}")
            android.util.Log.e("NoteRepository", "Exception cause: ${e.cause}")
            e.printStackTrace()
            throw SummaryException("Unexpected error: ${e.message}", e)
        }
    }
    
    suspend fun updateNoteTitle(noteId: Long, title: String) {
        val note = noteDao.getNoteById(noteId) ?: return
        noteDao.updateNote(note.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteNote(noteId: Long) {
        // Soft delete the note instead of hard delete
        softDeleteNote(noteId)
    }
    
    suspend fun softDeleteNote(noteId: Long) {
        val timestamp = System.currentTimeMillis()
        noteDao.softDeleteNote(noteId, timestamp)
    }
    
    suspend fun restoreNote(noteId: Long) {
        noteDao.restoreNote(noteId)
    }
    
    fun getDeletedNotes(): Flow<List<Note>> = noteDao.getDeletedNotes()
    
    suspend fun deleteOldTrashItems() {
        // Calculate 30 days ago in milliseconds
        val thirtyDaysInMillis = 30L * 24L * 60L * 60L * 1000L
        val cutoffTimestamp = System.currentTimeMillis() - thirtyDaysInMillis
        
        // Get all deleted notes to clean up associated files
        val deletedNotes = noteDao.getDeletedNotes().first()
        val oldNotes = deletedNotes.filter { it.deletedAt != null && it.deletedAt!! < cutoffTimestamp }
        
        // Delete associated files (audio recordings and image entries)
        oldNotes.forEach { note ->
            // Delete audio files
            val recordings = audioRecordingDao.getRecordingsByNoteId(note.id).first()
            recordings.forEach { recording ->
                File(recording.filePath).delete()
            }
            
            // Delete image files
            val entries = noteEntryDao.getEntriesByNoteId(note.id).first()
            entries.filter { it.entryType == EntryType.IMAGE }.forEach { entry ->
                val imageFile = File(entry.content)
                if (imageFile.exists()) {
                    imageFile.delete()
                }
            }
        }
        
        // Permanently delete old notes from database
        noteDao.deleteOldTrashItems(cutoffTimestamp)
    }
    
    suspend fun deleteNotePermanently(noteId: Long) {
        // Get recordings to delete audio files
        val recordings = audioRecordingDao.getRecordingsByNoteId(noteId).first()
        recordings.forEach { recording ->
            File(recording.filePath).delete()
        }
        
        // Get image entries to delete image files
        val entries = noteEntryDao.getEntriesByNoteId(noteId).first()
        entries.filter { it.entryType == EntryType.IMAGE }.forEach { entry ->
            val imageFile = File(entry.content)
            if (imageFile.exists()) {
                imageFile.delete()
            }
        }

        noteDao.deleteNoteById(noteId)
    }
    
    suspend fun deleteEntry(entryId: Long) {
        // Get entry to check if it's an image entry
        val entry = noteEntryDao.getEntryById(entryId)
        if (entry != null && entry.entryType == EntryType.IMAGE) {
            // Delete the image file
            deleteImageFile(entry.content)
        }
        noteEntryDao.deleteEntryById(entryId)
    }
    
    fun isRecording(): Boolean = audioRecorder.isRecording()
    
    fun releaseRecorder() {
        audioRecorder.release()
    }
    
    fun dispose() {
        backgroundScope.cancel()
    }
}

// Extension to get database instance
private fun NoteDatabase.Companion.getDatabase(context: Context): NoteDatabase {
    return androidx.room.Room.databaseBuilder(
        context.applicationContext,
        NoteDatabase::class.java,
        DATABASE_NAME
    )
    .addMigrations(NoteDatabase.MIGRATION_1_2, NoteDatabase.MIGRATION_2_3, NoteDatabase.MIGRATION_3_4)
    .build()
}
