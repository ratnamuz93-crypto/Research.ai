package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.model.AttachmentCodec
import com.example.model.ChatMessage
import com.example.model.ChatSession
import com.example.model.MessageRole
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val updatedAt: Long,
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val dataCount: Int = 0,
    val lastPreview: String = ""
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val role: String,
    val text: String,
    val attachmentsJson: String = "[]",
    val timestamp: Long,
    val isSamplePreview: Boolean = false,
    val followUpsJson: String = "[]"
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    fun observeMessages(sessionId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    suspend fun getMessagesOnce(sessionId: Long): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionOnce(sessionId: Long): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity): Long

    @Query(
        """
        UPDATE chat_sessions
        SET title = :title,
            updatedAt = :updatedAt,
            photoCount = :photoCount,
            videoCount = :videoCount,
            dataCount = :dataCount,
            lastPreview = :lastPreview
        WHERE id = :sessionId
        """
    )
    suspend fun updateSessionSummary(
        sessionId: Long,
        title: String,
        updatedAt: Long,
        photoCount: Int,
        videoCount: Int,
        dataCount: Int,
        lastPreview: String
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Long)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)
}

@Database(
    entities = [ChatSessionEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sarva_ai_chats.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

fun ChatSessionEntity.toDomain(): ChatSession = ChatSession(
    id = id,
    title = title,
    updatedAt = updatedAt,
    photoCount = photoCount,
    videoCount = videoCount,
    dataCount = dataCount,
    lastPreview = lastPreview
)

fun ChatMessageEntity.toDomain(): ChatMessage = ChatMessage(
    id = id,
    sessionId = sessionId,
    role = runCatching { MessageRole.valueOf(role) }.getOrDefault(MessageRole.USER),
    text = text,
    attachments = AttachmentCodec.decodeAttachments(attachmentsJson),
    timestamp = timestamp,
    isSamplePreview = isSamplePreview,
    suggestedFollowUps = AttachmentCodec.decodeStrings(followUpsJson)
)
