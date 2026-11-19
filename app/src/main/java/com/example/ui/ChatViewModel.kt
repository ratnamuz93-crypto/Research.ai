package com.example.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ChatRepository
import com.example.model.AttachmentType
import com.example.model.ChatMessage
import com.example.model.ChatSession
import com.example.model.MediaAttachment
import com.example.model.MessageRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "ChatViewModel"

sealed interface GenerationState {
    data object Idle : GenerationState
    data class Streaming(val partialText: String) : GenerationState
    data class Error(val message: String) : GenerationState
}

data class ChatUiState(
    val activeSessionId: Long? = null,
    val sessions: List<ChatSession> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val pendingAttachments: List<MediaAttachment> = emptyList(),
    val promptInput: String = "",
    val searchQuery: String = "",
    val generationState: GenerationState = GenerationState.Idle,
    val isLiveAiEnabled: Boolean = false,
    val isDarkTheme: Boolean = true,
    val inspectedAttachment: MediaAttachment? = null,
    val showPreviewInfoDialog: Boolean = false,
    val isAttachingMedia: Boolean = false
)

sealed interface ChatUiEvent {
    data class ShowSnackbar(val message: String) : ChatUiEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    private val activeSessionId = MutableStateFlow<Long?>(null)
    private val pendingAttachments = MutableStateFlow<List<MediaAttachment>>(emptyList())
    private val promptInput = MutableStateFlow("")
    private val searchQuery = MutableStateFlow("")
    private val generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
    private val isDarkTheme = MutableStateFlow(true)
    private val inspectedAttachment = MutableStateFlow<MediaAttachment?>(null)
    private val showPreviewInfoDialog = MutableStateFlow(false)
    private val isAttachingMedia = MutableStateFlow(false)

    private val _events = MutableSharedFlow<ChatUiEvent>()
    val events: SharedFlow<ChatUiEvent> = _events.asSharedFlow()

    private var activeGenerationJob: Job? = null

    private val sessionsFlow = repository.observeSessions()
    private val activeMessagesFlow = activeSessionId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeMessages(id)
    }

    private data class ControlSlice(
        val pending: List<MediaAttachment>,
        val input: String,
        val search: String,
        val genState: GenerationState,
        val dark: Boolean,
        val inspected: MediaAttachment?,
        val infoDialog: Boolean,
        val attaching: Boolean
    )

    private val controlSliceFlow = combine(
        combine(pendingAttachments, promptInput, searchQuery, generationState) { p, i, s, g ->
            Quad(p, i, s, g)
        },
        combine(isDarkTheme, inspectedAttachment, showPreviewInfoDialog, isAttachingMedia) { d, ins, info, att ->
            Quad(d, ins, info, att)
        }
    ) { first, second ->
        ControlSlice(
            pending = first.a,
            input = first.b,
            search = first.c,
            genState = first.d,
            dark = second.a,
            inspected = second.b,
            infoDialog = second.c,
            attaching = second.d
        )
    }

    val uiState: StateFlow<ChatUiState> = combine(
        activeSessionId,
        sessionsFlow,
        activeMessagesFlow,
        controlSliceFlow
    ) { sessionId, sessions, messages, controls ->
        val filteredSessions = if (controls.search.isBlank()) {
            sessions
        } else {
            val q = controls.search.trim().lowercase()
            sessions.filter {
                it.title.lowercase().contains(q) || it.lastPreview.lowercase().contains(q)
            }
        }
        ChatUiState(
            activeSessionId = sessionId,
            sessions = filteredSessions,
            messages = messages,
            pendingAttachments = controls.pending,
            promptInput = controls.input,
            searchQuery = controls.search,
            generationState = controls.genState,
            isLiveAiEnabled = repository.isLiveAiEnabled,
            isDarkTheme = controls.dark,
            inspectedAttachment = controls.inspected,
            showPreviewInfoDialog = controls.infoDialog,
            isAttachingMedia = controls.attaching
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ChatUiState(isLiveAiEnabled = repository.isLiveAiEnabled)
    )

    fun onPromptChanged(text: String) {
        promptInput.value = text
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun toggleTheme() {
        isDarkTheme.update { !it }
    }

    fun openAttachmentPreview(attachment: MediaAttachment) {
        inspectedAttachment.value = attachment
    }

    fun closeAttachmentPreview() {
        inspectedAttachment.value = null
    }

    fun setShowPreviewInfoDialog(show: Boolean) {
        showPreviewInfoDialog.value = show
    }

    fun startNewChat() {
        stopGeneration()
        activeSessionId.value = null
        pendingAttachments.value = emptyList()
        promptInput.value = ""
        generationState.value = GenerationState.Idle
    }

    fun selectSession(sessionId: Long) {
        if (activeSessionId.value == sessionId) return
        stopGeneration()
        activeSessionId.value = sessionId
        pendingAttachments.value = emptyList()
        generationState.value = GenerationState.Idle
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            if (activeSessionId.value == sessionId) {
                stopGeneration()
                activeSessionId.value = null
            }
            repository.deleteSession(sessionId)
        }
    }

    fun attachMediaUri(uri: Uri, hintType: AttachmentType? = null) {
        viewModelScope.launch {
            isAttachingMedia.value = true
            try {
                val attachment = repository.inspectAttachment(uri, hintType)
                pendingAttachments.update { current -> current + attachment }
                _events.emit(ChatUiEvent.ShowSnackbar("Attached ${attachment.displayName}"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to inspect media URI", e)
                _events.emit(ChatUiEvent.ShowSnackbar("Could not read the selected file"))
            } finally {
                isAttachingMedia.value = false
            }
        }
    }

    fun attachAndAnalyzePhoto(uri: Uri) {
        viewModelScope.launch {
            isAttachingMedia.value = true
            try {
                val attachment = repository.inspectAttachment(uri, AttachmentType.PHOTO)
                pendingAttachments.update { current -> current + attachment }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to inspect captured photo", e)
                _events.emit(ChatUiEvent.ShowSnackbar("Could not read the captured photo"))
                return@launch
            } finally {
                isAttachingMedia.value = false
            }
            sendCurrentPrompt()
        }
    }

    fun removePendingAttachment(id: String) {
        pendingAttachments.update { list -> list.filterNot { it.id == id } }
    }

    fun sendQuickPrompt(prompt: String) {
        promptInput.value = prompt
        sendCurrentPrompt()
    }

    fun sendCurrentPrompt() {
        val rawPrompt = promptInput.value.trim()
        val attachments = pendingAttachments.value
        if (rawPrompt.isEmpty() && attachments.isEmpty()) return
        if (generationState.value is GenerationState.Streaming) return

        val effectivePrompt = rawPrompt.ifEmpty {
            buildDefaultPromptForAttachments(attachments)
        }

        promptInput.value = ""
        pendingAttachments.value = emptyList()

        activeGenerationJob = viewModelScope.launch {
            try {
                val sessionId = activeSessionId.value ?: createNewSessionForPrompt(
                    prompt = effectivePrompt,
                    attachments = attachments
                ).also { newId ->
                    activeSessionId.value = newId
                }

                val historyBefore = repository.getMessagesOnce(sessionId)
                val userMessage = ChatMessage(
                    sessionId = sessionId,
                    role = MessageRole.USER,
                    text = effectivePrompt,
                    attachments = attachments
                )
                repository.saveMessage(userMessage)

                streamAndPersistAssistantReply(
                    sessionId = sessionId,
                    history = historyBefore + userMessage,
                    prompt = effectivePrompt,
                    attachments = attachments
                )
            } catch (e: CancellationException) {
                generationState.value = GenerationState.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed sending prompt", e)
                generationState.value = GenerationState.Error(
                    e.localizedMessage ?: "Something went wrong while generating an answer."
                )
            }
        }
    }

    fun regenerateLastResponse() {
        val sessionId = activeSessionId.value ?: return
        if (generationState.value is GenerationState.Streaming) return

        activeGenerationJob = viewModelScope.launch {
            try {
                val messages = repository.getMessagesOnce(sessionId)
                val lastMessage = messages.lastOrNull() ?: return@launch
                if (lastMessage.role == MessageRole.ASSISTANT) {
                    repository.deleteMessage(lastMessage.id, sessionId)
                }
                val updatedHistory = repository.getMessagesOnce(sessionId)
                val lastUserMessage = updatedHistory.lastOrNull { it.role == MessageRole.USER }
                    ?: return@launch

                streamAndPersistAssistantReply(
                    sessionId = sessionId,
                    history = updatedHistory,
                    prompt = lastUserMessage.text,
                    attachments = lastUserMessage.attachments
                )
            } catch (e: CancellationException) {
                generationState.value = GenerationState.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed regenerating response", e)
                generationState.value = GenerationState.Error(
                    e.localizedMessage ?: "Could not regenerate response."
                )
            }
        }
    }

    fun stopGeneration() {
        val partial = (generationState.value as? GenerationState.Streaming)?.partialText
        val sessionId = activeSessionId.value
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        generationState.value = GenerationState.Idle

        if (!partial.isNullOrBlank() && sessionId != null) {
            viewModelScope.launch {
                repository.saveMessage(
                    ChatMessage(
                        sessionId = sessionId,
                        role = MessageRole.ASSISTANT,
                        text = partial,
                        isSamplePreview = !repository.isLiveAiEnabled
                    )
                )
            }
        }
    }

    fun dismissError() {
        if (generationState.value is GenerationState.Error) {
            generationState.value = GenerationState.Idle
        }
    }

    private suspend fun streamAndPersistAssistantReply(
        sessionId: Long,
        history: List<ChatMessage>,
        prompt: String,
        attachments: List<MediaAttachment>
    ) {
        generationState.value = GenerationState.Streaming("")
        var finalText = ""
        var finalSample = !repository.isLiveAiEnabled
        var finalFollowUps = emptyList<String>()

        repository.streamAiResponse(
            history = history,
            userPrompt = prompt,
            attachments = attachments
        ).collect { chunk ->
            finalText = chunk.accumulatedText
            finalSample = chunk.isSamplePreview
            if (chunk.suggestedFollowUps.isNotEmpty()) {
                finalFollowUps = chunk.suggestedFollowUps
            }
            generationState.value = GenerationState.Streaming(finalText)
        }

        if (finalText.isNotBlank()) {
            repository.saveMessage(
                ChatMessage(
                    sessionId = sessionId,
                    role = MessageRole.ASSISTANT,
                    text = finalText,
                    isSamplePreview = finalSample,
                    suggestedFollowUps = finalFollowUps
                )
            )
        }
        generationState.value = GenerationState.Idle
    }

    private suspend fun createNewSessionForPrompt(
        prompt: String,
        attachments: List<MediaAttachment>
    ): Long {
        val title = when {
            prompt.isNotBlank() -> prompt.take(42).trim()
            attachments.size == 1 -> attachments.first().displayName.take(42)
            else -> "Multimodal Chat (${attachments.size} files)"
        }
        return repository.createSession(
            title = title,
            attachments = attachments,
            preview = prompt.take(90)
        )
    }

    private fun buildDefaultPromptForAttachments(attachments: List<MediaAttachment>): String {
        val hasPhoto = attachments.any { it.type == AttachmentType.PHOTO }
        val hasVideo = attachments.any { it.type == AttachmentType.VIDEO }
        val hasData = attachments.any { it.type == AttachmentType.DATA }
        return when {
            hasPhoto && !hasVideo && !hasData -> "Analyze this photo and explain what you see."
            hasVideo && !hasPhoto && !hasData -> "Watch this video and summarize its key moments."
            hasData && !hasPhoto && !hasVideo -> "Analyze this uploaded file and highlight key insights."
            else -> "Analyze all uploaded photos, videos, and files together."
        }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    class Factory(private val repository: ChatRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(repository) as T
        }
    }
}
