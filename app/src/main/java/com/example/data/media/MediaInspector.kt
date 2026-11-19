package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.example.model.AttachmentType
import com.example.model.MediaAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MediaInspector(private val context: Context) {

    suspend fun inspectUri(uri: Uri, hintType: AttachmentType? = null): MediaAttachment =
        withContext(Dispatchers.IO) {
            val contentResolver = context.contentResolver
            val detectedMime = contentResolver.getType(uri)
                ?: inferMimeFromExtension(uri)
                ?: when (hintType) {
                    AttachmentType.PHOTO -> "image/jpeg"
                    AttachmentType.VIDEO -> "video/mp4"
                    else -> "application/octet-stream"
                }

            val resolvedType = when {
                detectedMime.startsWith("image/") -> AttachmentType.PHOTO
                detectedMime.startsWith("video/") -> AttachmentType.VIDEO
                hintType != null -> hintType
                else -> AttachmentType.DATA
            }

            var displayName = "attachment"
            var sizeBytes = 0L
            val localFile = if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                File(uri.path!!)
            } else {
                null
            }

            if (localFile != null && localFile.exists()) {
                displayName = localFile.name
                sizeBytes = localFile.length()
            } else {
                runCatching {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex >= 0) {
                                displayName = cursor.getString(nameIndex) ?: displayName
                            }
                            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                                sizeBytes = cursor.getLong(sizeIndex)
                            }
                        }
                    }
                }
            }

            val attachmentId = UUID.randomUUID().toString()
            var width: Int? = null
            var height: Int? = null
            var durationMs: Long? = null
            var thumbnailUriString: String? = null
            var persistedUriString: String = uri.toString()
            var textPreview: String? = null

            when (resolvedType) {
                AttachmentType.PHOTO -> {
                    val alreadyInAppDir = localFile != null &&
                        localFile.exists() &&
                        localFile.absolutePath.startsWith(context.filesDir.absolutePath)

                    if (!alreadyInAppDir) {
                        val cachedPhoto = cacheSmallFile(
                            uri,
                            "photo_${attachmentId}.img",
                            maxBytes = 10L * 1024L * 1024L
                        )
                        if (cachedPhoto != null) {
                            persistedUriString = Uri.fromFile(cachedPhoto).toString()
                            if (sizeBytes <= 0L) sizeBytes = cachedPhoto.length()
                        }
                    }

                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    runCatching {
                        if (localFile != null && localFile.exists()) {
                            BitmapFactory.decodeFile(localFile.absolutePath, bounds)
                        } else {
                            contentResolver.openInputStream(uri)?.use { stream ->
                                BitmapFactory.decodeStream(stream, null, bounds)
                            }
                        }
                    }
                    if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                        width = bounds.outWidth
                        height = bounds.outHeight
                    }
                    thumbnailUriString = persistedUriString
                }

                AttachmentType.VIDEO -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        durationMs = retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull()
                        width = retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                            ?.toIntOrNull()
                        height = retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                            ?.toIntOrNull()
                        val frameTimeUs = if ((durationMs ?: 0L) > 1500L) 1_000_000L else 0L
                        val frame = retriever.getFrameAtTime(
                            frameTimeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                        if (frame != null) {
                            val thumbFile = saveBitmapThumbnail(frame, "vthumb_${attachmentId}.jpg")
                            frame.recycle()
                            thumbnailUriString = Uri.fromFile(thumbFile).toString()
                        }
                    } catch (_: Exception) {
                        // Video metadata extraction may fail on unsupported codecs; proceed gracefully
                    } finally {
                        runCatching { retriever.release() }
                    }
                }

                AttachmentType.DATA -> {
                    if (isTextReadable(detectedMime, displayName)) {
                        textPreview = readTextSnippet(uri, maxChars = 1800)
                    }
                }
            }

            MediaAttachment(
                id = attachmentId,
                uriString = persistedUriString,
                thumbnailUriString = thumbnailUriString,
                type = resolvedType,
                displayName = displayName,
                mimeType = detectedMime,
                sizeBytes = sizeBytes,
                width = width,
                height = height,
                durationMs = durationMs,
                textPreview = textPreview
            )
        }

    private fun cacheSmallFile(uri: Uri, fileName: String, maxBytes: Long): File? {
        return runCatching {
            val dir = File(context.filesDir, "attachments").apply { mkdirs() }
            val outFile = File(dir, fileName)
            var total = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > maxBytes) {
                            outFile.delete()
                            return null
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (total > 0L) outFile else {
                outFile.delete()
                null
            }
        }.getOrNull()
    }

    private fun saveBitmapThumbnail(bitmap: Bitmap, fileName: String): File {
        val dir = File(context.filesDir, "thumbnails").apply { mkdirs() }
        val file = File(dir, fileName)
        val scaled = if (bitmap.width > 640) {
            val ratio = 640f / bitmap.width.toFloat()
            Bitmap.createScaledBitmap(
                bitmap,
                640,
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        }
        if (scaled !== bitmap) scaled.recycle()
        return file
    }

    private fun readTextSnippet(uri: Uri, maxChars: Int): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val buffer = CharArray(maxChars)
                val count = reader.read(buffer, 0, maxChars)
                if (count > 0) String(buffer, 0, count).trim() else null
            }
        }.getOrNull()
    }

    private fun isTextReadable(mimeType: String, fileName: String): Boolean {
        val lowerName = fileName.lowercase()
        val textExtensions = listOf(
            ".txt", ".csv", ".tsv", ".json", ".xml", ".md", ".log",
            ".yaml", ".yml", ".html", ".htm", ".kt", ".java", ".py", ".js", ".sql"
        )
        return mimeType.startsWith("text/") ||
            mimeType == "application/json" ||
            mimeType == "application/xml" ||
            textExtensions.any { lowerName.endsWith(it) }
    }

    private fun inferMimeFromExtension(uri: Uri): String? {
        val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return if (!ext.isNullOrBlank()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
        } else {
            null
        }
    }
}
