package com.example.ai_notetaker.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class TranscriptionRequest(
    val file: String, // Will be sent as multipart
    val model: String = "whisper-1"
)

@Serializable
data class TranscriptionResponse(
    val text: String
)

@Serializable
data class ChatCompletionRequest(
    val model: String, // Required field, no default value
    val messages: List<ChatMessage>,
    val response_format: ResponseFormat? = null,
    val temperature: Double? = null
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ResponseFormat(
    val type: String = "json_object"
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<Choice>
)

@Serializable
data class Choice(
    val message: ChatMessage
)
