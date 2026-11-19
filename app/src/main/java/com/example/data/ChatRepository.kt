package com.example.data

import android.net.Uri
import com.example.data.ai.AiRepository
import com.example.data.ai.AiResponseChunk
import com.example.data.local.ChatDao
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.local.toDomain
import com.example.data.media.MediaInspector
import com.example.model.AttachmentCodec
import com.example.model.AttachmentType
import com.example.model.ChatMessage
import com.example.model.ChatSession
import com.example.model.MediaAttachment
import com.example.model.MessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepository(
    private val chatDao: ChatDao,
    private val mediaInspector: MediaInspector,
    private val aiRepository: AiRepository
) {
    val isLiveAiEnabled: Boolean
        get() = aiRepository.isLiveAiEnabled

    fun observeSessions(): Flow<List<ChatSession>> =
        chatDao.observeSessions().map { entities -> entities.map { it.toDomain() } }

    fun observeMessages(sessionId: Long): Flow<List<ChatMessage>> =
        chatDao.observeMessages(sessionId).map { entities -> entities.map { it.toDomain() } }

    suspend fun inspectAttachment(uri: Uri, hintType: AttachmentType? = null): MediaAttachment =
        mediaInspector.inspectUri(uri, hintType)

    suspend fun createSession(
        title: String,
        attachments: List<MediaAttachment>,
        preview: String
    ): Long {
        val now = System.currentTimeMillis()
        return chatDao.insertSession(
            ChatSessionEntity(
                title = title,
                updatedAt = now,
                photoCount = attachments.count { it.type == AttachmentType.PHOTO },
                videoCount = attachments.count { it.type == AttachmentType.VIDEO },
                dataCount = attachments.count { it.type == AttachmentType.DATA },
                lastPreview = preview
            )
        )
    }

    suspend fun saveMessage(message: ChatMessage): Long {
        val id = chatDao.insertMessage(
            ChatMessageEntity(
                id = message.id,
                sessionId = message.sessionId,
                role = message.role.name,
                text = message.text,
                attachmentsJson = AttachmentCodec.encodeAttachments(message.attachments),
                timestamp = message.timestamp,
                isSamplePreview = message.isSamplePreview,
                followUpsJson = AttachmentCodec.encodeStrings(message.suggestedFollowUps)
            )
        )
        refreshSessionSummary(message.sessionId)
        return id
    }

    suspend fun getMessagesOnce(sessionId: Long): List<ChatMessage> =
        chatDao.getMessagesOnce(sessionId).map { it.toDomain() }

    suspend fun deleteMessage(messageId: Long, sessionId: Long) {
        chatDao.deleteMessageById(messageId)
        refreshSessionSummary(sessionId)
    }

    suspend fun deleteSession(sessionId: Long) {
        chatDao.deleteSessionById(sessionId)
    }

    fun streamAiResponse(
        history: List<ChatMessage>,
        userPrompt: String,
        attachments: List<MediaAttachment>
    ): Flow<AiResponseChunk> = aiRepository.generateResponseStream(
        history = history,
        userPrompt = userPrompt,
        attachments = attachments
    )

    private suspend fun refreshSessionSummary(sessionId: Long) {
        val session = chatDao.getSessionOnce(sessionId) ?: return
        val messages = chatDao.getMessagesOnce(sessionId).map { it.toDomain() }
        val allAttachments = messages.flatMap { it.attachments }
        val latestText = messages.lastOrNull()?.text
            ?.replace("**", "")
            ?.replace("\n", " ")
            ?.trim()
            ?.take(90)
            .orEmpty()

        chatDao.updateSessionSummary(
            sessionId = sessionId,
            title = session.title,
            updatedAt = System.currentTimeMillis(),
            photoCount = allAttachments.count { it.type == AttachmentType.PHOTO },
            videoCount = allAttachments.count { it.type == AttachmentType.VIDEO },
            dataCount = allAttachments.count { it.type == AttachmentType.DATA },
            lastPreview = latestText
        )
    }
}
