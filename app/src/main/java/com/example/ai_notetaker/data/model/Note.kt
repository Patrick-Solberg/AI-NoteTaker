package com.example.ai_notetaker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String = "Note",
    val summary: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null // Timestamp when note was soft-deleted, null if not deleted
) {
    val isDeleted: Boolean
        get() = deletedAt != null
}
