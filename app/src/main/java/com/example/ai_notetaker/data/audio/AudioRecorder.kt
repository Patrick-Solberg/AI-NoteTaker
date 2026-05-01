package com.example.ai_notetaker.data.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentFilePath: String? = null
    
    fun startRecording(noteId: Long): String {
        val audioDir = File(context.filesDir, "audio")
        if (!audioDir.exists()) {
            audioDir.mkdirs()
        }
        
        val timestamp = System.currentTimeMillis()
        val fileName = "${noteId}_${timestamp}.m4a"
        val file = File(audioDir, fileName)
        currentFilePath = file.absolutePath
        
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            
            try {
                prepare()
                start()
            } catch (e: IOException) {
                release()
                throw AudioRecordingException("Failed to start recording", e)
            }
        }
        
        return file.absolutePath
    }
    
    fun stopRecording(): String {
        val filePath = currentFilePath ?: throw AudioRecordingException("No active recording")
        
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            throw AudioRecordingException("Failed to stop recording", e)
        } finally {
            mediaRecorder = null
        }
        
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            throw AudioRecordingException("Recording file is empty or missing")
        }
        
        return filePath
    }
    
    fun isRecording(): Boolean = mediaRecorder != null
    
    fun release() {
        mediaRecorder?.release()
        mediaRecorder = null
        currentFilePath = null
    }
}

class AudioRecordingException(message: String, cause: Throwable? = null) : Exception(message, cause)
