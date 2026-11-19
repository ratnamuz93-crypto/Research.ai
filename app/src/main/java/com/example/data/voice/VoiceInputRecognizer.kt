package com.example.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "VoiceInputRecognizer"

data class VoiceInputState(
    val isListening: Boolean = false,
    val partialTranscript: String = "",
    val rmsDb: Float = 0f
)

class VoiceInputRecognizer(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isStoppingManually = false

    private val _inputState = MutableStateFlow(VoiceInputState())
    val inputState: StateFlow<VoiceInputState> = _inputState.asStateFlow()

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun startListening(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        runOnMainThread {
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                _inputState.value = VoiceInputState()
                onError("Speech recognition is not available on this device.")
                return@runOnMainThread
            }

            isStoppingManually = false
            destroyInternal()

            val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _inputState.value = VoiceInputState(isListening = true)
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) {
                    if (_inputState.value.isListening) {
                        _inputState.value = _inputState.value.copy(rmsDb = rmsdB)
                    }
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    _inputState.value = _inputState.value.copy(isListening = false, rmsDb = 0f)
                }

                override fun onError(error: Int) {
                    val wasManualStop = isStoppingManually
                    isStoppingManually = false
                    _inputState.value = VoiceInputState()

                    if (wasManualStop && (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_NO_MATCH)) {
                        return
                    }

                    val message = mapErrorCode(error)
                    Log.w(TAG, "SpeechRecognizer error=$error ($message)")
                    onError(message)
                }

                override fun onResults(results: Bundle?) {
                    isStoppingManually = false
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    _inputState.value = VoiceInputState()
                    if (spoken.isNotBlank()) {
                        onFinalResult(spoken)
                    } else {
                        onError("Could not hear anything clearly. Please try again.")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        _inputState.value = _inputState.value.copy(partialTranscript = partial)
                        onPartialResult(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            }

            try {
                _inputState.value = VoiceInputState(isListening = true)
                recognizer.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SpeechRecognizer", e)
                _inputState.value = VoiceInputState()
                onError(e.localizedMessage ?: "Failed to start voice input.")
            }
        }
    }

    fun stopListening() {
        runOnMainThread {
            isStoppingManually = true
            runCatching { speechRecognizer?.stopListening() }
            _inputState.value = VoiceInputState()
        }
    }

    fun destroy() {
        runOnMainThread {
            isStoppingManually = true
            destroyInternal()
            _inputState.value = VoiceInputState()
        }
    }

    private fun destroyInternal() {
        runCatching { speechRecognizer?.cancel() }
        runCatching { speechRecognizer?.destroy() }
        speechRecognizer = null
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun mapErrorCode(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice input."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Network error during speech recognition."
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Tap the mic and speak again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognizer is busy. Please wait a moment."
        SpeechRecognizer.ERROR_SERVER -> "Speech recognition service error. Try again."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap the mic and try again."
        else -> "Voice input stopped. Please try again."
    }
}
