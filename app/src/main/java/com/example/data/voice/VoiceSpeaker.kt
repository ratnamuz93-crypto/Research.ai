package com.example.data.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

private const val TAG = "VoiceSpeaker"

data class VoicePlaybackState(
    val isEngineReady: Boolean = false,
    val speakingMessageId: Long? = null
)

class VoiceSpeaker(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var pendingSpeech: Pair<Long, String>? = null
    private var activeFinalUtteranceId: String? = null

    private val _playbackState = MutableStateFlow(VoicePlaybackState())
    val playbackState: StateFlow<VoicePlaybackState> = _playbackState.asStateFlow()

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureDefaultVoice()
                setupProgressListener()
                _playbackState.value = _playbackState.value.copy(isEngineReady = true)
                val queued = pendingSpeech
                pendingSpeech = null
                if (queued != null) {
                    speak(queued.first, queued.second)
                }
            } else {
                Log.e(TAG, "TextToSpeech initialization failed with status: $status")
                _playbackState.value = VoicePlaybackState(isEngineReady = false, speakingMessageId = null)
            }
        }
    }

    private fun configureDefaultVoice() {
        val engine = tts ?: return
        engine.setSpeechRate(1.0f)
        engine.setPitch(1.0f)
        selectBestLocale(engine, preferHindi = false)
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId == activeFinalUtteranceId) {
                    activeFinalUtteranceId = null
                    _playbackState.value = _playbackState.value.copy(speakingMessageId = null)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                activeFinalUtteranceId = null
                _playbackState.value = _playbackState.value.copy(speakingMessageId = null)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "TTS utterance error code=$errorCode")
                activeFinalUtteranceId = null
                _playbackState.value = _playbackState.value.copy(speakingMessageId = null)
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (!interrupted || utteranceId == activeFinalUtteranceId) {
                    activeFinalUtteranceId = null
                    _playbackState.value = _playbackState.value.copy(speakingMessageId = null)
                }
            }
        })
    }

    fun speak(messageId: Long, rawText: String): Boolean {
        val cleaned = sanitizeForSpeech(rawText)
        if (cleaned.isBlank()) return false

        val engine = tts ?: return false
        if (!_playbackState.value.isEngineReady) {
            pendingSpeech = messageId to cleaned
            return true
        }

        val containsDevanagari = cleaned.any { it in '\u0900'..'\u097F' }
        selectBestLocale(engine, preferHindi = containsDevanagari)

        val chunks = splitIntoChunks(cleaned, maxChunkLen = 3200)
        if (chunks.isEmpty()) return false

        val batchId = UUID.randomUUID().toString()
        val finalUtteranceId = "${batchId}_${chunks.lastIndex}"
        activeFinalUtteranceId = finalUtteranceId
        _playbackState.value = _playbackState.value.copy(speakingMessageId = messageId)

        chunks.forEachIndexed { index, chunk ->
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = "${batchId}_$index"
            engine.speak(chunk, queueMode, null, utteranceId)
        }
        return true
    }

    fun stop() {
        pendingSpeech = null
        activeFinalUtteranceId = null
        runCatching { tts?.stop() }
        _playbackState.value = _playbackState.value.copy(speakingMessageId = null)
    }

    fun shutdown() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
    }

    private fun selectBestLocale(engine: TextToSpeech, preferHindi: Boolean) {
        val candidates = if (preferHindi) {
            listOf(Locale("hi", "IN"), Locale("en", "IN"), Locale.getDefault(), Locale.US)
        } else {
            listOf(Locale("en", "IN"), Locale.getDefault(), Locale.US)
        }
        for (locale in candidates) {
            val res = runCatching { engine.setLanguage(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                return
            }
        }
    }

    private fun sanitizeForSpeech(raw: String): String {
        return raw
            .replace(Regex("```[\\s\\S]*?```"), " Code snippet included. ")
            .replace("`", "")
            .replace("**", "")
            .replace("*", "")
            .replace(Regex("^[•\\-]+\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("#+\\s*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun splitIntoChunks(text: String, maxChunkLen: Int): List<String> {
        if (text.length <= maxChunkLen) return listOf(text)
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = (start + maxChunkLen).coerceAtMost(text.length)
            if (end == text.length) {
                result.add(text.substring(start).trim())
                break
            }
            val splitAt = text.lastIndexOfAny(charArrayOf('.', '?', '!', '।', '\n'), end)
            val safeEnd = if (splitAt > start + maxChunkLen / 2) splitAt + 1 else end
            result.add(text.substring(start, safeEnd).trim())
            start = safeEnd
        }
        return result.filter { it.isNotBlank() }
    }
}
