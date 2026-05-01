package com.example.ai_notetaker.data.remote

import android.content.Context
import com.example.ai_notetaker.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAIService(private val context: Context) {
    private val apiKey: String by lazy {
        val key = context.getString(R.string.openai_api_key)
        android.util.Log.d("OpenAIService", "API Key loaded, length: ${key.length}, starts with: ${key.take(10)}...")
        if (key == "YOUR_API_KEY_HERE" || key.isBlank()) {
            android.util.Log.e("OpenAIService", "WARNING: API key is not set or is placeholder!")
        }
        key
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true // Always encode default values to ensure model is included
    }
    
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()
                chain.proceed(request)
            }
            .build()
    }
    
    suspend fun transcribeAudio(audioFile: File): String = withContext(Dispatchers.IO) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/m4a".toMediaType())
            )
            .addFormDataPart("model", "gpt-4o-mini-transcribe")
            .build()
        
        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .post(requestBody)
            .build()
        
        executeWithRetry(request) { response ->
            val responseBody = response.body?.string()
                ?: throw TranscriptionException("Empty response body")
            
            try {
                val transcriptionResponse = json.decodeFromString<TranscriptionResponse>(
                    responseBody
                )
                transcriptionResponse.text
            } catch (e: Exception) {
                throw TranscriptionException("Failed to parse transcription response", e)
            }
        }
    }
    
    suspend fun generateSummary(fullNoteText: String): String = withContext(Dispatchers.IO) {
        android.util.Log.d("OpenAIService", "=== STARTING SUMMARY GENERATION ===")
        android.util.Log.d("OpenAIService", "Input text length: ${fullNoteText.length}")
        android.util.Log.d("OpenAIService", "Input text preview: ${fullNoteText.take(200)}...")
        
        val prompt = """
Oppsummer teksten nedenfor på norsk.

KRITISKE KRAV (MÅ FØLGES):
- Noen av tekstene kan være fra talegjenkjenning (STT), så det kan være feiltolking av ord
- Hvis en setning ikke gir mening, prøv å finne det riktige ordet basert på konteksten
- VIKTIG: Hvis teksten inneholder korreksjoner (f.eks. "endre X til Y", "korriger X til Y", "bytt ut X med Y"), må du APPLISERE disse korreksjonene på innholdet. Ikke bare gjenta korreksjonen som en ny informasjon - faktisk endre ordene i oppsummeringen
- Eksempel: Hvis teksten sier "dette må sjekkes i spiresstans" og senere "endre spiresstans til skyrespons", skal oppsummeringen si "dette må sjekkes i skyrespons" - IKKE "dette må sjekkes i spiresstans, og det er viktig å endre spiresstans til skyrespons"
- Oppsummeringen skal være KORTERE enn originalteksten
- Ikke bruk punktlister med mange linjer
- Samle relaterte punkter i én setning, separert med komma
- Bruk så få avsnitt som mulig, men sørg for at teksten fortsatt er lett å lese og gir mening
- Ingen overforklaringer
- Ikke lag seksjoner som "Tematisk gruppering"
- Ikke start med "notatet handler om" eller lignende - gå direkte til innholdet
- Skriv som et raskt internt notat til deg selv

Struktur (hvis relevant):
1. Direkte til innholdet (ikke "notatet handler om...")
2. Hva som trengs (i én eller to setninger, med komma)
3. Eventuelle avklaringer eller foreslått løsning (valgfritt)

TEKST:
\"\"\"
$fullNoteText
\"\"\"
""".trimIndent()
        
        android.util.Log.d("OpenAIService", "Prompt length: ${prompt.length}")
        
        val requestBodyJson = json.encodeToString(
            ChatCompletionRequest.serializer(),
            ChatCompletionRequest(
                model = "gpt-4o-mini",
                messages = listOf(
                    ChatMessage(role = "user", content = prompt)
                ),
                response_format = null, // Plain text response, not JSON
                temperature = 0.1 // Match Python script
            )
        )
        
        android.util.Log.d("OpenAIService", "Request body length: ${requestBodyJson.length}")
        android.util.Log.d("OpenAIService", "Request body preview: ${requestBodyJson.take(500)}...")
        android.util.Log.d("OpenAIService", "Full request body: $requestBodyJson")
        
        // Verify model is in the JSON
        if (!requestBodyJson.contains("\"model\"")) {
            android.util.Log.e("OpenAIService", "ERROR: 'model' field is missing from request body!")
        } else {
            android.util.Log.d("OpenAIService", "✓ 'model' field is present in request body")
        }
        
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
            .build()
        
        android.util.Log.d("OpenAIService", "Request URL: ${request.url}")
        android.util.Log.d("OpenAIService", "Request method: ${request.method}")
        android.util.Log.d("OpenAIService", "Has Authorization header: ${request.header("Authorization") != null}")
        
        executeWithRetry(request) { response ->
            android.util.Log.d("OpenAIService", "=== RESPONSE RECEIVED ===")
            android.util.Log.d("OpenAIService", "Response code: ${response.code}")
            android.util.Log.d("OpenAIService", "Response message: ${response.message}")
            android.util.Log.d("OpenAIService", "Response headers: ${response.headers}")
            
            val responseBody = response.body?.string()
                ?: throw SummaryException("Empty response body")
            
            android.util.Log.d("OpenAIService", "Response body length: ${responseBody.length}")
            android.util.Log.d("OpenAIService", "Response body: $responseBody")
            
            try {
                android.util.Log.d("OpenAIService", "Attempting to parse JSON response...")
                val completionResponse = json.decodeFromString<ChatCompletionResponse>(
                    responseBody
                )
                android.util.Log.d("OpenAIService", "JSON parsed successfully")
                android.util.Log.d("OpenAIService", "Number of choices: ${completionResponse.choices.size}")
                
                val content = completionResponse.choices.firstOrNull()?.message?.content
                    ?: throw SummaryException("No content in response. Choices: ${completionResponse.choices.size}")
                
                android.util.Log.d("OpenAIService", "Content extracted, length: ${content.length}")
                android.util.Log.d("OpenAIService", "Content preview: ${content.take(200)}...")
                
                // Return plain text summary (already cleaned by OpenAI)
                val summary = content.trim()
                android.util.Log.d("OpenAIService", "=== SUMMARY GENERATION SUCCESS ===")
                android.util.Log.d("OpenAIService", "Final summary length: ${summary.length}")
                android.util.Log.d("OpenAIService", "Final summary: $summary")
                summary
            } catch (e: kotlinx.serialization.SerializationException) {
                android.util.Log.e("OpenAIService", "=== JSON PARSING ERROR ===", e)
                android.util.Log.e("OpenAIService", "Response body that failed to parse: $responseBody")
                throw SummaryException("Failed to parse summary JSON: ${e.message}", e)
            } catch (e: Exception) {
                android.util.Log.e("OpenAIService", "=== PARSE ERROR ===", e)
                android.util.Log.e("OpenAIService", "Response body: $responseBody")
                throw SummaryException("Failed to parse summary response: ${e.message}", e)
            }
        }
    }
    
    suspend fun generateTitle(summary: String): String = withContext(Dispatchers.IO) {
        android.util.Log.d("OpenAIService", "=== STARTING TITLE GENERATION ===")
        android.util.Log.d("OpenAIService", "Summary length: ${summary.length}")
        android.util.Log.d("OpenAIService", "Summary preview: ${summary.take(200)}...")
        
        val prompt = """
Generer en kort tittel basert på oppsummeringen nedenfor.

KRITISKE KRAV:
- Tittelen skal være maksimum 3 ord
- Tittelen skal være på norsk
- Tittelen skal fange opp hovedtemaet i oppsummeringen
- Ikke bruk anførselstegn eller punktum
- Returner bare tittelen, ingen ekstra tekst

OPPSUMMERING:
\"\"\"
$summary
\"\"\"
""".trimIndent()
        
        android.util.Log.d("OpenAIService", "Title prompt length: ${prompt.length}")
        
        val requestBodyJson = json.encodeToString(
            ChatCompletionRequest.serializer(),
            ChatCompletionRequest(
                model = "gpt-4o-mini",
                messages = listOf(
                    ChatMessage(role = "user", content = prompt)
                ),
                response_format = null,
                temperature = 0.1
            )
        )
        
        android.util.Log.d("OpenAIService", "Title request body: $requestBodyJson")
        
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
            .build()
        
        executeWithRetry(request) { response ->
            android.util.Log.d("OpenAIService", "=== TITLE RESPONSE RECEIVED ===")
            android.util.Log.d("OpenAIService", "Response code: ${response.code}")
            
            val responseBody = response.body?.string()
                ?: throw SummaryException("Empty response body for title generation")
            
            android.util.Log.d("OpenAIService", "Title response body: $responseBody")
            
            try {
                val completionResponse = json.decodeFromString<ChatCompletionResponse>(
                    responseBody
                )
                
                val content = completionResponse.choices.firstOrNull()?.message?.content
                    ?: throw SummaryException("No content in title response")
                
                // Clean the title: remove quotes, trim, and ensure max 3 words
                val title = content.trim()
                    .removeSurrounding("\"", "\"")
                    .removeSurrounding("'", "'")
                    .trim()
                
                // Ensure max 3 words
                val words = title.split("\\s+".toRegex())
                val finalTitle = if (words.size > 3) {
                    words.take(3).joinToString(" ")
                } else {
                    title
                }
                
                android.util.Log.d("OpenAIService", "=== TITLE GENERATION SUCCESS ===")
                android.util.Log.d("OpenAIService", "Generated title: $finalTitle")
                finalTitle
            } catch (e: kotlinx.serialization.SerializationException) {
                android.util.Log.e("OpenAIService", "=== TITLE JSON PARSING ERROR ===", e)
                android.util.Log.e("OpenAIService", "Response body that failed to parse: $responseBody")
                throw SummaryException("Failed to parse title JSON: ${e.message}", e)
            } catch (e: Exception) {
                android.util.Log.e("OpenAIService", "=== TITLE PARSE ERROR ===", e)
                android.util.Log.e("OpenAIService", "Response body: $responseBody")
                throw SummaryException("Failed to parse title response: ${e.message}", e)
            }
        }
    }
    
    private suspend fun <T> executeWithRetry(
        request: Request,
        maxRetries: Int = 3,
        parseResponse: (Response) -> T
    ): T {
        var lastException: Exception? = null
        
        android.util.Log.d("OpenAIService", "=== STARTING RETRY LOOP ===")
        android.util.Log.d("OpenAIService", "Max retries: $maxRetries")
        
        repeat(maxRetries) { attempt ->
            android.util.Log.d("OpenAIService", "--- Attempt ${attempt + 1}/$maxRetries ---")
            try {
                android.util.Log.d("OpenAIService", "Executing HTTP request...")
                val response = client.newCall(request).execute()
                android.util.Log.d("OpenAIService", "HTTP request completed")
                android.util.Log.d("OpenAIService", "Response code: ${response.code}")
                android.util.Log.d("OpenAIService", "Response successful: ${response.isSuccessful}")
                
                when {
                    response.code == 429 -> {
                        android.util.Log.e("OpenAIService", "=== QUOTA EXCEEDED (429) ===")
                        val errorBody = response.body?.string() ?: "No error body"
                        android.util.Log.e("OpenAIService", "Error body: $errorBody")
                        response.close()
                        throw InsufficientQuotaException()
                    }
                    !response.isSuccessful -> {
                        android.util.Log.e("OpenAIService", "=== HTTP ERROR ===")
                        android.util.Log.e("OpenAIService", "Status code: ${response.code}")
                        android.util.Log.e("OpenAIService", "Status message: ${response.message}")
                        val errorBody = response.body?.string() ?: "Unknown error"
                        android.util.Log.e("OpenAIService", "Error body: $errorBody")
                        response.close()
                        throw NetworkException("HTTP ${response.code}: $errorBody")
                    }
                    else -> {
                        android.util.Log.d("OpenAIService", "Response successful, parsing...")
                        return parseResponse(response).also { 
                            response.close()
                            android.util.Log.d("OpenAIService", "=== REQUEST SUCCESSFUL ===")
                        }
                    }
                }
            } catch (e: InsufficientQuotaException) {
                android.util.Log.e("OpenAIService", "=== QUOTA EXCEPTION (not retrying) ===", e)
                throw e
            } catch (e: java.net.SocketTimeoutException) {
                android.util.Log.e("OpenAIService", "=== TIMEOUT ERROR (attempt ${attempt + 1}) ===", e)
                lastException = e
                if (attempt < maxRetries - 1) {
                    val delay = (1000L * (1 shl attempt)) // Exponential backoff
                    android.util.Log.d("OpenAIService", "Retrying after ${delay}ms...")
                    kotlinx.coroutines.delay(delay)
                }
            } catch (e: java.net.UnknownHostException) {
                android.util.Log.e("OpenAIService", "=== NETWORK ERROR - Unknown Host (attempt ${attempt + 1}) ===", e)
                lastException = e
                if (attempt < maxRetries - 1) {
                    val delay = (1000L * (1 shl attempt))
                    android.util.Log.d("OpenAIService", "Retrying after ${delay}ms...")
                    kotlinx.coroutines.delay(delay)
                }
            } catch (e: java.io.IOException) {
                android.util.Log.e("OpenAIService", "=== IO ERROR (attempt ${attempt + 1}) ===", e)
                android.util.Log.e("OpenAIService", "Error message: ${e.message}")
                android.util.Log.e("OpenAIService", "Error cause: ${e.cause}")
                lastException = e
                if (attempt < maxRetries - 1) {
                    val delay = (1000L * (1 shl attempt))
                    android.util.Log.d("OpenAIService", "Retrying after ${delay}ms...")
                    kotlinx.coroutines.delay(delay)
                }
            } catch (e: Exception) {
                android.util.Log.e("OpenAIService", "=== UNEXPECTED ERROR (attempt ${attempt + 1}) ===", e)
                android.util.Log.e("OpenAIService", "Error type: ${e.javaClass.name}")
                android.util.Log.e("OpenAIService", "Error message: ${e.message}")
                android.util.Log.e("OpenAIService", "Error cause: ${e.cause}")
                lastException = e
                if (attempt < maxRetries - 1) {
                    val delay = (1000L * (1 shl attempt))
                    android.util.Log.d("OpenAIService", "Retrying after ${delay}ms...")
                    kotlinx.coroutines.delay(delay)
                }
            }
        }
        
        android.util.Log.e("OpenAIService", "=== ALL RETRIES FAILED ===")
        android.util.Log.e("OpenAIService", "Last exception: ${lastException?.javaClass?.name}")
        android.util.Log.e("OpenAIService", "Last exception message: ${lastException?.message}")
        if (lastException != null) {
            android.util.Log.e("OpenAIService", "Last exception stack trace:", lastException)
        }
        
        throw NetworkException(
            "Failed after $maxRetries attempts. Last error: ${lastException?.message}",
            lastException
        )
    }
}
