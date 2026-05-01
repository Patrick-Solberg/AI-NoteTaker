package com.example.ai_notetaker.data.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SummaryDebouncer(
    private val delayMillis: Long = 3000L,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private var debounceJob: Job? = null
    private val _isPending = MutableStateFlow(false)
    val isPending: StateFlow<Boolean> = _isPending
    private val _error = MutableStateFlow<Exception?>(null)
    val error: StateFlow<Exception?> = _error
    
    fun trigger(action: suspend () -> Unit) {
        debounceJob?.cancel()
        _isPending.value = true
        _error.value = null
        
        debounceJob = scope.launch {
            delay(delayMillis)
            try {
                action()
            } catch (e: Exception) {
                _error.value = e
                android.util.Log.e("SummaryDebouncer", "Summary generation failed", e)
            } finally {
                _isPending.value = false
            }
        }
    }
    
    fun cancel() {
        debounceJob?.cancel()
        debounceJob = null
        _isPending.value = false
    }
    
    fun dispose() {
        cancel()
        scope.cancel()
    }
}
