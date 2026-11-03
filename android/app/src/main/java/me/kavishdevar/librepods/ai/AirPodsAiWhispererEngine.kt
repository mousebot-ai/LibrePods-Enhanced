package me.kavishdevar.librepods.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * AirPodsAiWhispererEngine
 *
 * Privacy-First, Instant Conversational Ear Whisperer for AirPods Pro.
 * Guarantees:
 * 1. Zero 24/7 Listening: Microphone hardware is completely idle and destroyed until nod gesture.
 * 2. Instant Streaming Whisper: Answers are parsed instantly and whispered into earbud voice call stream.
 * 3. Master Hardware Switch: Strictly checks "ai_whisperer_enabled". If false, zero processing happens.
 * 4. Separate Per-Provider API Keys: OpenRouter, Gemini, and NVIDIA NIM each keep their own dedicated keys.
 */
class AirPodsAiWhispererEngine(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "AirPodsAiWhisperer"
        private const val TIMEOUT_MS = 6000L
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var isListening = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listeningTimeoutJob: Job? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            tts?.setSpeechRate(1.25f) // Optimized speed for instant concise whispers
            tts?.setPitch(0.95f)
            isTtsReady = true
            Log.d(TAG, "TTS initialized and primed for ear whispers")
        }
    }

    /**
     * Called when a head nod gesture is validated.
     */
    fun onDoubleNodDetected() {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val masterEnabled = prefs.getBoolean("ai_whisperer_enabled", false)
        if (!masterEnabled) {
            Log.d(TAG, "AI Whisperer is disabled via Master Switch - ignoring gesture")
            return
        }

        // Check if head gesture chime is enabled
        val chimeEnabled = prefs.getBoolean("ai_head_chime_enabled", false)
        if (chimeEnabled) {
            playActivationChime()
        }

        Log.i(TAG, "Double-nod gesture validated! Starting instant privacy-guarded listening cycle...")
        startGuardedListening()
    }

    private fun startGuardedListening() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    Log.w(TAG, "Speech recognition not available on device")
                    return@launch
                }

                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        Log.d(TAG, "Microphone ACTIVE. Listening for friend's question...")
                        listeningTimeoutJob?.cancel()
                        listeningTimeoutJob = scope.launch {
                            delay(TIMEOUT_MS)
                            stopListening()
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "Speech detected")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        Log.d(TAG, "Speech finished, processing audio...")
                    }

                    override fun onError(error: Int) {
                        Log.w(TAG, "Speech recognition error: $error")
                        stopListening()
                    }

                    override fun onResults(results: Bundle?) {
                        stopListening()
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognizedText = matches[0]
                            Log.i(TAG, "Heard question: \"$recognizedText\"")
                            queryAiAndWhisper(recognizedText)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition: ${e.message}")
                stopListening()
            }
        }
    }

    private fun stopListening() {
        listeningTimeoutJob?.cancel()
        listeningTimeoutJob = null
        if (!isListening) return
        isListening = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
                Log.d(TAG, "Microphone completely powered OFF & destroyed.")
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up SpeechRecognizer: ${e.message}")
            }
        }
    }

    private fun queryAiAndWhisper(question: String) {
        scope.launch {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val provider = prefs.getString("ai_whisperer_provider", "openrouter") ?: "openrouter"

            val prompt = "You are a discreet personal conversational whisperer in an earbud. Answer this conversational question concisely in 1 or 2 natural spoken sentences maximum. Do not use bullet points, markdown, or greetings. Directly state the facts: $question"

            val answer = when (provider) {
                "openrouter" -> {
                    val key = prefs.getString("ai_key_openrouter", "") ?: ""
                    callOpenRouterApi(prompt, key)
                }
                "nvidia" -> {
                    val key = prefs.getString("ai_key_nvidia", "") ?: ""
                    callNvidiaApi(prompt, key)
                }
                else -> {
                    val key = prefs.getString("ai_key_gemini", "") ?: ""
                    callGeminiFlashApi(prompt, key)
                }
            }

            if (answer.isNotBlank()) {
                Log.i(TAG, "AI Ear Answer received: \"$answer\"")
                whisperIntoAirPods(answer)
            }
        }
    }

    fun callGeminiFlashApi(prompt: String, apiKey: String): String {
        return try {
            val key = apiKey.trim()
            val urlString = if (key.startsWith("AQ.")) {
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
            } else {
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$key"
            }
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            if (key.startsWith("AQ.")) {
                conn.setRequestProperty("Authorization", "Bearer $key")
                conn.setRequestProperty("x-goog-api-key", key)
            }
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.doOutput = true

            val jsonBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        }
                        put("parts", parts)
                    })
                }
                put("contents", contents)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()); it.flush() }

            if (conn.responseCode == 200) {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val root = JSONObject(response)
                val text = root.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                text.trim()
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.w(TAG, "Gemini HTTP error ${conn.responseCode}: $err")
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini query error: ${e.message}")
            ""
        }
    }

    fun callNvidiaApi(prompt: String, apiKey: String): String {
        return try {
            val url = URL("https://integrate.api.nvidia.com/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.doOutput = true

            val jsonBody = JSONObject().apply {
                put("model", "deepseek-ai/deepseek-v4.1-flash")
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                }
                put("messages", messages)
                put("max_tokens", 80)
                put("temperature", 0.3)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()); it.flush() }

            if (conn.responseCode == 200) {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val root = JSONObject(response)
                val choice = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                choice.optString("content", "").trim()
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.w(TAG, "NVIDIA HTTP error ${conn.responseCode}: $err")
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "NVIDIA API error: ${e.message}")
            ""
        }
    }

    fun callOpenRouterApi(prompt: String, apiKey: String): String {
        return try {
            val url = URL("https://openrouter.ai/api/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.doOutput = true

            val jsonBody = JSONObject().apply {
                put("model", "openrouter/free")
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                }
                put("messages", messages)
                put("max_tokens", 120)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()); it.flush() }

            if (conn.responseCode == 200) {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val root = JSONObject(response)
                val choice = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                var text = choice.optString("content", "")
                if (text.isBlank()) {
                    text = choice.optString("reasoning", "")
                }
                text.trim()
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.w(TAG, "OpenRouter HTTP error ${conn.responseCode}: $err")
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenRouter API error: ${e.message}")
            ""
        }
    }

    private fun whisperIntoAirPods(text: String) {
        if (!isTtsReady || tts == null) return
        CoroutineScope(Dispatchers.Main).launch {
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_VOICE_CALL)
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "EAR_WHISPER_${System.currentTimeMillis()}")
            Log.d(TAG, "Whispered answer directly to AirPods earbud")
        }
    }

    private fun playActivationChime() {
        try {
            val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_VOICE_CALL, 90)
            toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 180)
            CoroutineScope(Dispatchers.Default).launch {
                delay(250)
                toneGen.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play activation chime: " + e.message)
        }
    }

    fun testApiConnection(provider: String, apiKey: String, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            try {
                val testPrompt = "Reply with exactly one word: OK"
                val response = when (provider) {
                    "openrouter" -> callOpenRouterApi(testPrompt, apiKey)
                    "nvidia" -> callNvidiaApi(testPrompt, apiKey)
                    else -> callGeminiFlashApi(testPrompt, apiKey)
                }
                if (response.isNotBlank()) {
                    onResult(true, "Success: AI replied: \"$response\"")
                } else {
                    onResult(false, "Failed: empty or invalid response. Check your API key.")
                }
            } catch (e: Exception) {
                onResult(false, "Error: " + (e.message ?: "network error"))
            }
        }
    }

    fun release() {
        stopListening()
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
        scope.cancel()
    }
}
