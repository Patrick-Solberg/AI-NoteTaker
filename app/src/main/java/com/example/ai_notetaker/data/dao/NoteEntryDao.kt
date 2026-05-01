package com.example.ai_notetaker.data.dao

import androidx.room.*
import com.example.ai_notetaker.data.model.NoteEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteEntryDao {
    @Query("SELECT * FROM note_entries WHERE noteId = :noteId ORDER BY createdAt ASC")
    fun getEntriesByNoteId(noteId: Long): Flow<List<NoteEntry>>
    
    @Query("SELECT * FROM note_entries WHERE id = :id")
    suspend fun getEntryById(id: Long): NoteEntry?
    
    @Insert
    suspend fun insertEntry(entry: NoteEntry): Long
    
    @Update
    suspend fun updateEntry(entry: NoteEntry)
    
    @Delete
    suspend fun deleteEntry(entry: NoteEntry)
    
    @Query("DELETE FROM note_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)
}
