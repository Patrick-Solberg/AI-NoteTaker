package com.example.ai_notetaker.data.dao

import androidx.room.*
import com.example.ai_notetaker.data.model.AudioRecording
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioRecordingDao {
    @Query("SELECT * FROM audio_recordings WHERE noteId = :noteId ORDER BY recordedAt DESC")
    fun getRecordingsByNoteId(noteId: Long): Flow<List<AudioRecording>>
    
    @Query("SELECT * FROM audio_recordings WHERE id = :id")
    suspend fun getRecordingById(id: Long): AudioRecording?
    
    @Insert
    suspend fun insertRecording(recording: AudioRecording): Long
    
    @Update
    suspend fun updateRecording(recording: AudioRecording)
    
    @Delete
    suspend fun deleteRecording(recording: AudioRecording)
}
