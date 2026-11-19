package com.example.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AttachmentType {
    PHOTO,
    VIDEO,
    DATA
}

data class MediaAttachment(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val thumbnailUriString: String? = null,
    val type: AttachmentType,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val textPreview: String? = null
)

enum class MessageRole {
    USER,
    ASSISTANT
}

data class ChatMessage(
    val id: Long = 0L,
    val sessionId: Long,
    val role: MessageRole,
    val text: String,
    val attachments: List<MediaAttachment> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val isSamplePreview: Boolean = false,
    val suggestedFollowUps: List<String> = emptyList()
)

data class ChatSession(
    val id: Long = 0L,
    val title: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val dataCount: Int = 0,
    val lastPreview: String = ""
)

object AttachmentCodec {
    fun encodeAttachments(list: List<MediaAttachment>): String {
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("uriString", item.uriString)
                put("thumbnailUriString", item.thumbnailUriString ?: JSONObject.NULL)
                put("type", item.type.name)
                put("displayName", item.displayName)
                put("mimeType", item.mimeType)
                put("sizeBytes", item.sizeBytes)
                put("width", item.width ?: JSONObject.NULL)
                put("height", item.height ?: JSONObject.NULL)
                put("durationMs", item.durationMs ?: JSONObject.NULL)
                put("textPreview", item.textPreview ?: JSONObject.NULL)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun decodeAttachments(raw: String): List<MediaAttachment> {
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val typeName = obj.optString("type", AttachmentType.DATA.name)
                    val type = runCatching { AttachmentType.valueOf(typeName) }
                        .getOrDefault(AttachmentType.DATA)
                    add(
                        MediaAttachment(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            uriString = obj.optString("uriString", ""),
                            thumbnailUriString = obj.optNullableString("thumbnailUriString"),
                            type = type,
                            displayName = obj.optString("displayName", "Attachment"),
                            mimeType = obj.optString("mimeType", "application/octet-stream"),
                            sizeBytes = obj.optLong("sizeBytes", 0L),
                            width = obj.optNullableInt("width"),
                            height = obj.optNullableInt("height"),
                            durationMs = obj.optNullableLong("durationMs"),
                            textPreview = obj.optNullableString("textPreview")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeStrings(list: List<String>): String {
        val array = JSONArray()
        list.forEach { array.put(it) }
        return array.toString()
    }

    fun decodeStrings(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "Unknown size"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}

fun formatDuration(durationMs: Long?): String {
    if (durationMs == null || durationMs <= 0L) return "0:00"
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
