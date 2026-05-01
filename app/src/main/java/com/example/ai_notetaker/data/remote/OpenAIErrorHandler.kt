package com.example.ai_notetaker.data.remote

sealed class OpenAIException(message: String) : Exception(message)

class InsufficientQuotaException(message: String = "Monthly OpenAI limit reached") : OpenAIException(message)

class NetworkException(message: String, cause: Throwable? = null) : OpenAIException(message) {
    init {
        cause?.let { initCause(it) }
    }
}

class TranscriptionException(message: String, cause: Throwable? = null) : OpenAIException(message) {
    init {
        cause?.let { initCause(it) }
    }
}

class SummaryException(message: String, cause: Throwable? = null) : OpenAIException(message) {
    init {
        cause?.let { initCause(it) }
    }
}
