package com.example.ai_notetaker.data.dao

import androidx.room.*
import com.example.ai_notetaker.data.model.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<Note>>
    
    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getDeletedNotes(): Flow<List<Note>>
    
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): Note?
    
    @Insert
    suspend fun insertNote(note: Note): Long
    
    @Update
    suspend fun updateNote(note: Note)
    
    @Delete
    suspend fun deleteNote(note: Note)
    
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)
    
    @Query("UPDATE notes SET deletedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteNote(id: Long, timestamp: Long)
    
    @Query("UPDATE notes SET deletedAt = NULL WHERE id = :id")
    suspend fun restoreNote(id: Long)
    
    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND deletedAt < :cutoffTimestamp")
    suspend fun deleteOldTrashItems(cutoffTimestamp: Long)
}
