package com.example.data.ai

import com.example.model.AttachmentType
import com.example.model.ChatMessage
import com.example.model.MediaAttachment
import com.example.model.formatDuration
import com.example.model.formatFileSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class AiResponseChunk(
    val accumulatedText: String,
    val isComplete: Boolean,
    val isSamplePreview: Boolean,
    val suggestedFollowUps: List<String> = emptyList()
)

interface AiRepository {
    val isLiveAiEnabled: Boolean

    fun generateResponseStream(
        history: List<ChatMessage>,
        userPrompt: String,
        attachments: List<MediaAttachment>
    ): Flow<AiResponseChunk>
}

/**
 * Placeholder implementation used when Firebase AI Logic is not enabled.
 * Inspects real local metadata from attached photos, videos, and data files
 * and streams clearly labeled sample responses so the full multimodal UX can be explored.
 */
class PlaceholderAiRepository : AiRepository {
    override val isLiveAiEnabled: Boolean = false

    override fun generateResponseStream(
        history: List<ChatMessage>,
        userPrompt: String,
        attachments: List<MediaAttachment>
    ): Flow<AiResponseChunk> = flow {
        val recentAttachments = attachments.ifEmpty {
            history.lastOrNull { it.attachments.isNotEmpty() }?.attachments.orEmpty()
        }

        val fullResponse = buildSampleResponse(
            prompt = userPrompt.trim(),
            currentAttachments = attachments,
            contextAttachments = recentAttachments
        )
        val followUps = buildFollowUpSuggestions(recentAttachments)

        // Stream in natural chunks to preview real-time AI generation feel
        val tokens = fullResponse.split(Regex("(?<=\\s)"))
        val builder = StringBuilder()
        delay(220L)
        for (i in tokens.indices) {
            builder.append(tokens[i])
            val isLast = i == tokens.lastIndex
            if (isLast || i % 3 == 0) {
                emit(
                    AiResponseChunk(
                        accumulatedText = builder.toString(),
                        isComplete = isLast,
                        isSamplePreview = true,
                        suggestedFollowUps = if (isLast) followUps else emptyList()
                    )
                )
                if (!isLast) delay(32L)
            }
        }
    }

    private fun buildSampleResponse(
        prompt: String,
        currentAttachments: List<MediaAttachment>,
        contextAttachments: List<MediaAttachment>
    ): String {
        val photos = contextAttachments.filter { it.type == AttachmentType.PHOTO }
        val videos = contextAttachments.filter { it.type == AttachmentType.VIDEO }
        val files = contextAttachments.filter { it.type == AttachmentType.DATA }

        val sb = StringBuilder()
        sb.appendLine("**Sample Preview Response** *(Live Gemini AI requires Firebase)*")
        sb.appendLine()

        if (prompt.isNotBlank()) {
            sb.appendLine("**Your question:** \"$prompt\"")
            sb.appendLine()
        }

        if (currentAttachments.isEmpty() && contextAttachments.isEmpty()) {
            sb.appendLine("Main **Sarva AI** (Preview Mode) hoon — ek all-in-one multimodal assistant jahan aap:")
            sb.appendLine("• **Photos** upload karke scene, text (OCR), objects, ya math problems pooch sakte hain.")
            sb.appendLine("• **Videos** daalkar key moments, actions, speech summary, ya specific timestamps ke baare mein sawaal pooch sakte hain.")
            sb.appendLine("• **Data & Documents** (PDF, CSV, JSON, TXT, audio) upload karke summaries, insights, aur Q&A paa sakte hain.")
            sb.appendLine()
            sb.append("Neeche composer bar se koi bhi photo, video, ya file attach karein. Real Gemini answers ke liye **Firebase AI Logic** enable karein.")
            return sb.toString()
        }

        if (photos.isNotEmpty()) {
            sb.appendLine("**Photo Inspection (${photos.size})**")
            photos.forEach { photo ->
                val dims = if (photo.width != null && photo.height != null) {
                    "${photo.width}×${photo.height}px"
                } else {
                    "Resolution detected on analysis"
                }
                sb.appendLine("• **${photo.displayName}** — $dims • ${formatFileSize(photo.sizeBytes)} (${photo.mimeType})")
            }
            sb.appendLine("• *Preview insight:* Photo successfully attach ho chuka hai. Firebase AI Logic connect karne par Gemini is photo ke objects, text, colors, aur aapke sawaal ka exact visual answer dega.")
            sb.appendLine()
        }

        if (videos.isNotEmpty()) {
            sb.appendLine("**Video Inspection (${videos.size})**")
            videos.forEach { video ->
                val dims = if (video.width != null && video.height != null) {
                    "${video.width}×${video.height}"
                } else {
                    "Video stream"
                }
                sb.appendLine("• **${video.displayName}** — Duration ${formatDuration(video.durationMs)} • $dims • ${formatFileSize(video.sizeBytes)}")
            }
            sb.appendLine("• *Preview insight:* Video aur iska keyframe thumbnail ready hai. Live Gemini AI ke saath aap is video ke scenes, spoken dialogue, aur har second ke events par direct questions pooch sakte hain.")
            sb.appendLine()
        }

        if (files.isNotEmpty()) {
            sb.appendLine("**Data & Document Inspection (${files.size})**")
            files.forEach { file ->
                sb.appendLine("• **${file.displayName}** — ${file.mimeType} • ${formatFileSize(file.sizeBytes)}")
                val snippet = file.textPreview
                if (!snippet.isNullOrBlank()) {
                    val lines = snippet.lines()
                    val previewExcerpt = lines.take(4).joinToString("\n").take(260)
                    sb.appendLine("  Extracted preview (${lines.size} lines sampled):")
                    sb.appendLine("```")
                    sb.appendLine(previewExcerpt)
                    sb.appendLine("```")
                }
            }
            sb.appendLine("• *Preview insight:* Document metadata read kar liya gaya hai. Live Gemini AI enable hone par poora PDF, CSV, JSON, ya document deeply analyze hoga.")
            sb.appendLine()
        }

        sb.append("*Note: Yeh ek sample preview response hai. Live multimodal Gemini answers ke liye AI Studio mein Firebase setup enable karein.*")
        return sb.toString()
    }

    private fun buildFollowUpSuggestions(attachments: List<MediaAttachment>): List<String> {
        val hasPhoto = attachments.any { it.type == AttachmentType.PHOTO }
        val hasVideo = attachments.any { it.type == AttachmentType.VIDEO }
        val hasData = attachments.any { it.type == AttachmentType.DATA }

        return buildList {
            if (hasPhoto) add("Extract all text from this photo")
            if (hasVideo) add("List key moments with timestamps")
            if (hasData) add("Summarize the main findings in bullets")
            add("Explain this in simple Hinglish")
        }.take(3)
    }
}
