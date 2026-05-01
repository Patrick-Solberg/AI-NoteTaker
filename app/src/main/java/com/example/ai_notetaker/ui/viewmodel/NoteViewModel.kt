package com.example.ai_notetaker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_notetaker.data.audio.AudioRecordingException
import com.example.ai_notetaker.data.model.AudioRecording
import com.example.ai_notetaker.data.model.Note
import com.example.ai_notetaker.data.remote.InsufficientQuotaException
import com.example.ai_notetaker.data.remote.NetworkException
import com.example.ai_notetaker.data.remote.SummaryException
import com.example.ai_notetaker.data.remote.TranscriptionException
import com.example.ai_notetaker.data.repository.NoteRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.serialization.json.Json

data class NoteUiState(
    val notes: List<Note> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val quotaExceeded: Boolean = false
)

data class NoteDetailUiState(
    val note: Note? = null,
    val entries: List<com.example.ai_notetaker.data.model.NoteEntry> = emptyList(),
    val recordings: List<AudioRecording> = emptyList(),
    val summary: String? = null,
    val isRecording: Boolean = false,
    val isLoading: Boolean = false,
    val isTranscribing: Boolean = false,
    val isGeneratingSummary: Boolean = false,
    val showAddEntryDialog: Boolean = false,
    val error: String? = null,
    val quotaExceeded: Boolean = false
)

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NoteRepository(application)
    
    private val _uiState = MutableStateFlow(NoteUiState())
    val uiState: StateFlow<NoteUiState> = _uiState.asStateFlow()
    
    private val _detailUiState = MutableStateFlow(NoteDetailUiState())
    val detailUiState: StateFlow<NoteDetailUiState> = _detailUiState.asStateFlow()
    
    private val _deletedNotes = MutableStateFlow<List<Note>>(emptyList())
    val deletedNotes: StateFlow<List<Note>> = _deletedNotes.asStateFlow()
    
    private var currentNoteId: Long? = null
    private var observationJobs: List<Job> = emptyList()
    
    init {
        loadNotes()
        loadDeletedNotes()
        // Clean up old trash items on startup
        cleanupOldTrashItems()
    }
    
    private fun loadNotes() {
        viewModelScope.launch {
            repository.getAllNotes()
                .catch { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
                .collect { notes ->
                    _uiState.update { it.copy(notes = notes, isLoading = false) }
                }
        }
    }
    
    fun createNote(onNoteCreated: (Long) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val noteId = repository.createNote()
                _uiState.update { it.copy(isLoading = false) }
                onNoteCreated(noteId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }
    
    fun loadNoteDetail(noteId: Long) {
        // Cancel any existing observation jobs to prevent stale updates
        observationJobs.forEach { it.cancel() }
        observationJobs = emptyList()
        
        // Always reset state immediately when loading a note to prevent showing old data
        _detailUiState.update { 
            NoteDetailUiState(
                isLoading = true,
                quotaExceeded = _detailUiState.value.quotaExceeded
            )
        }
        
        currentNoteId = noteId
        
        // Load note data
        val loadJob = viewModelScope.launch {
            try {
                val note = repository.getNoteById(noteId)
                if (note != null && currentNoteId == noteId) {
                    _detailUiState.update {
                        it.copy(
                            note = note,
                            summary = note.summary,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                if (currentNoteId == noteId) {
                    _detailUiState.update { it.copy(error = e.message, isLoading = false) }
                }
            }
        }
        
        // Observe entries - only update if this is still the current note
        val entriesJob = viewModelScope.launch {
            repository.getEntriesByNoteId(noteId)
                .catch { e ->
                    if (currentNoteId == noteId) {
                        _detailUiState.update { it.copy(error = e.message) }
                    }
                }
                .collect { entries ->
                    if (currentNoteId == noteId) {
                        _detailUiState.update { it.copy(entries = entries) }
                    }
                }
        }
        
        // Observe recordings - only update if this is still the current note
        val recordingsJob = viewModelScope.launch {
            repository.getRecordingsByNoteId(noteId)
                .catch { e ->
                    if (currentNoteId == noteId) {
                        _detailUiState.update { it.copy(error = e.message) }
                    }
                }
                .collect { recordings ->
                    if (currentNoteId == noteId) {
                        val hasPendingTranscriptions = recordings.any { it.transcription == null }
                        _detailUiState.update { 
                            it.copy(
                                recordings = recordings,
                                isTranscribing = hasPendingTranscriptions
                            ) 
                        }
                    }
                }
        }
        
        // Observe note updates (for summary changes) - only update if this is still the current note
        val notesJob = viewModelScope.launch {
            repository.getAllNotes()
                .collect { notes ->
                    if (currentNoteId == noteId) {
                        val updatedNote = notes.find { it.id == noteId }
                        updatedNote?.let { note ->
                            _detailUiState.update {
                                it.copy(
                                    note = note,
                                    summary = note.summary,
                                    isGeneratingSummary = false
                                )
                            }
                        }
                    }
                }
        }
        
        observationJobs = listOf(loadJob, entriesJob, recordingsJob, notesJob)
    }
    
    fun clearNoteDetail() {
        // Cancel all observation jobs
        observationJobs.forEach { it.cancel() }
        observationJobs = emptyList()
        
        // Reset state when navigating away
        _detailUiState.update { 
            NoteDetailUiState(
                quotaExceeded = _detailUiState.value.quotaExceeded
            )
        }
        
        currentNoteId = null
    }
    
    fun showAddEntryDialog() {
        _detailUiState.update { it.copy(showAddEntryDialog = true) }
    }
    
    fun hideAddEntryDialog() {
        _detailUiState.update { it.copy(showAddEntryDialog = false) }
    }
    
    fun addTextEntry(text: String) {
        val noteId = currentNoteId ?: return
        if (text.isBlank()) return
        
        viewModelScope.launch {
            try {
                _detailUiState.update { it.copy(showAddEntryDialog = false) }
                repository.addTextEntry(noteId, text)
                // Summary generation happens in background, will update when complete
            } catch (e: InsufficientQuotaException) {
                _detailUiState.update { 
                    it.copy(
                        quotaExceeded = true,
                        error = "Monthly OpenAI limit reached",
                        showAddEntryDialog = false
                    ) 
                }
                _uiState.update { it.copy(quotaExceeded = true) }
            } catch (e: Exception) {
                _detailUiState.update { 
                    it.copy(
                        error = "Failed to add entry: ${e.message}",
                        showAddEntryDialog = false
                    ) 
                }
            }
        }
    }
    
    fun generateSummaryManually() {
        val noteId = currentNoteId
        android.util.Log.d("NoteViewModel", "=== MANUAL SUMMARY TRIGGERED ===")
        android.util.Log.d("NoteViewModel", "Current note ID: $noteId")
        
        if (noteId == null) {
            android.util.Log.e("NoteViewModel", "Cannot generate summary - no current note ID")
            return
        }
        
        viewModelScope.launch {
            try {
                android.util.Log.d("NoteViewModel", "Setting isGeneratingSummary = true")
                _detailUiState.update { it.copy(isGeneratingSummary = true, error = null) }
                
                android.util.Log.d("NoteViewModel", "Calling repository.generateSummaryManually($noteId)")
                repository.generateSummaryManually(noteId)
                
                android.util.Log.d("NoteViewModel", "Repository call completed, waiting for note update...")
                // Summary will be updated via note observation flow
            } catch (e: InsufficientQuotaException) {
                android.util.Log.e("NoteViewModel", "=== QUOTA EXCEEDED IN VIEWMODEL ===", e)
                _detailUiState.update { 
                    it.copy(
                        isGeneratingSummary = false,
                        quotaExceeded = true,
                        error = "Monthly OpenAI limit reached"
                    ) 
                }
                _uiState.update { it.copy(quotaExceeded = true) }
            } catch (e: Exception) {
                android.util.Log.e("NoteViewModel", "=== EXCEPTION IN VIEWMODEL ===", e)
                android.util.Log.e("NoteViewModel", "Exception type: ${e.javaClass.name}")
                android.util.Log.e("NoteViewModel", "Exception message: ${e.message}")
                android.util.Log.e("NoteViewModel", "Exception cause: ${e.cause}")
                e.printStackTrace()
                _detailUiState.update { 
                    it.copy(
                        isGeneratingSummary = false,
                        error = "Failed to generate summary: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    fun startRecording() {
        val noteId = currentNoteId ?: return
        viewModelScope.launch {
            try {
                repository.startRecording(noteId)
                _detailUiState.update { it.copy(isRecording = true, error = null) }
            } catch (e: AudioRecordingException) {
                _detailUiState.update {
                    it.copy(
                        isRecording = false,
                        error = "Failed to start recording: ${e.message}"
                    )
                }
            } catch (e: Exception) {
                _detailUiState.update {
                    it.copy(
                        isRecording = false,
                        error = "Unexpected error: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun stopRecording() {
        val noteId = currentNoteId ?: return
        viewModelScope.launch {
            try {
                _detailUiState.update { it.copy(isRecording = false) }
                repository.stopRecording(noteId)
                // Check if any recordings are still being transcribed
                val recordings = repository.getRecordingsByNoteId(noteId).first()
                val hasPendingTranscriptions = recordings.any { it.transcription == null }
                _detailUiState.update { it.copy(isTranscribing = hasPendingTranscriptions) }
            } catch (e: AudioRecordingException) {
                _detailUiState.update {
                    it.copy(
                        error = "Failed to stop recording: ${e.message}"
                    )
                }
            } catch (e: Exception) {
                _detailUiState.update {
                    it.copy(
                        error = "Unexpected error: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            try {
                repository.softDeleteNote(noteId)
                loadNotes()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    private fun loadDeletedNotes() {
        viewModelScope.launch {
            repository.getDeletedNotes()
                .catch { e ->
                    android.util.Log.e("NoteViewModel", "Error loading deleted notes", e)
                }
                .collect { notes ->
                    _deletedNotes.value = notes
                }
        }
    }
    
    fun restoreNote(noteId: Long) {
        viewModelScope.launch {
            try {
                repository.restoreNote(noteId)
                loadNotes()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun deleteNotePermanently(noteId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteNotePermanently(noteId)
                loadNotes()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun emptyTrash() {
        viewModelScope.launch {
            try {
                val deletedNotes = _deletedNotes.value
                deletedNotes.forEach { note ->
                    repository.deleteNotePermanently(note.id)
                }
                loadNotes()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    private fun cleanupOldTrashItems() {
        viewModelScope.launch {
            try {
                repository.deleteOldTrashItems()
            } catch (e: Exception) {
                android.util.Log.e("NoteViewModel", "Error cleaning up old trash items", e)
            }
        }
    }
    
    fun updateNoteTitle(noteId: Long, title: String) {
        viewModelScope.launch {
            try {
                repository.updateNoteTitle(noteId, title)
            } catch (e: Exception) {
                android.util.Log.e("NoteViewModel", "Error updating note title", e)
                _detailUiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun deleteEntry(entryId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteEntry(entryId)
            } catch (e: Exception) {
                android.util.Log.e("NoteViewModel", "Error deleting entry", e)
                _detailUiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun addImageEntry(imageFilePath: String) {
        val noteId = currentNoteId ?: return
        viewModelScope.launch {
            try {
                repository.addImageEntry(noteId, imageFilePath)
            } catch (e: Exception) {
                android.util.Log.e("NoteViewModel", "Error adding image entry", e)
                _detailUiState.update { it.copy(error = "Failed to add image: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
        _detailUiState.update { it.copy(error = null) }
    }
    
    override fun onCleared() {
        super.onCleared()
        repository.releaseRecorder()
        repository.dispose()
    }
}
