package com.example.ai_notetaker.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Summary(
    val title: String,
    val tldr: String,
    val bulletPoints: List<String> = emptyList(),
    val actionItems: List<String> = emptyList()
)
