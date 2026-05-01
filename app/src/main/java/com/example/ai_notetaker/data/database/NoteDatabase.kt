package com.example.ai_notetaker.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.ai_notetaker.data.dao.AudioRecordingDao
import com.example.ai_notetaker.data.dao.NoteDao
import com.example.ai_notetaker.data.dao.NoteEntryDao
import com.example.ai_notetaker.data.model.AudioRecording
import com.example.ai_notetaker.data.model.Note
import com.example.ai_notetaker.data.model.NoteEntry

@Database(
    entities = [Note::class, AudioRecording::class, NoteEntry::class],
    version = 4,
    exportSchema = false
)
@androidx.room.TypeConverters(com.example.ai_notetaker.data.model.EntryTypeConverter::class)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun audioRecordingDao(): AudioRecordingDao
    abstract fun noteEntryDao(): NoteEntryDao
    
    companion object {
        const val DATABASE_NAME = "note_database"
        
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create note_entries table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS note_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        noteId INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        entryType TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(noteId) REFERENCES notes(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_note_entries_noteId ON note_entries(noteId)")
                
                // Migrate existing manualText to note_entries
                database.execSQL("""
                    INSERT INTO note_entries (noteId, content, entryType, createdAt)
                    SELECT id, manualText, 'TEXT', updatedAt
                    FROM notes
                    WHERE manualText != '' AND manualText IS NOT NULL
                """.trimIndent())
            }
        }
        
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add title column to notes table
                database.execSQL("ALTER TABLE notes ADD COLUMN title TEXT NOT NULL DEFAULT 'Note'")
                
                // Add recordingId column to note_entries table
                database.execSQL("ALTER TABLE note_entries ADD COLUMN recordingId INTEGER")
            }
        }
        
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add deletedAt column to notes table for soft delete functionality
                database.execSQL("ALTER TABLE notes ADD COLUMN deletedAt INTEGER")
                // EntryType enum extension (IMAGE) doesn't require database migration
                // as it's stored as TEXT and the converter handles it
            }
        }
    }
}
