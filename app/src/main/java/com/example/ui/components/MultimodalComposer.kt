package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.AttachmentType
import com.example.model.MediaAttachment
import com.example.model.formatDuration
import com.example.model.formatFileSize

@Composable
fun MultimodalComposer(
    promptText: String,
    pendingAttachments: List<MediaAttachment>,
    isStreaming: Boolean,
    isAttachingMedia: Boolean,
    isListeningVoice: Boolean,
    onPromptChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
    onVoiceInputClick: () -> Unit,
    onOpenCamera: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickVideo: () -> Unit,
    onPickData: () -> Unit,
    onOpenAttachmentSheet: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onInspectAttachment: (MediaAttachment) -> Unit,
    modifier: Modifier = Modifier
) {
    val canSend = promptText.isNotBlank() || pendingAttachments.isNotEmpty()
    val listenPulse = rememberInfiniteTransition(label = "mic_pulse")
    val micAlpha by listenPulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(550),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_alpha"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
            ) {
                AnimatedVisibility(visible = isListeningVoice) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("voice_listening_indicator")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .alpha(micAlpha)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.voice_listening_status),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = pendingAttachments.isNotEmpty() || isAttachingMedia) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pending_attachments_row")
                    ) {
                        items(pendingAttachments, key = { it.id }) { attachment ->
                            PendingAttachmentChip(
                                attachment = attachment,
                                onRemove = { onRemoveAttachment(attachment.id) },
                                onClick = { onInspectAttachment(attachment) }
                            )
                        }
                        if (isAttachingMedia) {
                            item(key = "attaching_spinner") {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.size(68.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.5.dp,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Main Composer Container
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(
                        1.dp,
                        if (isListeningVoice) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                            ) {
                                if (promptText.isEmpty()) {
                                    Text(
                                        text = if (isListeningVoice) {
                                            stringResource(R.string.voice_listening_status)
                                        } else {
                                            stringResource(R.string.composer_placeholder)
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isListeningVoice) {
                                            MaterialTheme.colorScheme.tertiary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                BasicTextField(
                                    value = promptText,
                                    onValueChange = onPromptChange,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    maxLines = 5,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(
                                        onSend = { if (canSend && !isStreaming) onSendClick() }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("prompt_input_field")
                                )
                            }

                            // Hands-free SpeechRecognizer Microphone Button inside chat input field
                            Surface(
                                onClick = onVoiceInputClick,
                                shape = CircleShape,
                                color = if (isListeningVoice) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    Color.Transparent
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("voice_input_mic_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isListeningVoice) {
                                            Icons.Rounded.GraphicEq
                                        } else {
                                            Icons.Rounded.Mic
                                        },
                                        contentDescription = stringResource(
                                            if (isListeningVoice) {
                                                R.string.cd_voice_input_stop
                                            } else {
                                                R.string.cd_voice_input_start
                                            }
                                        ),
                                        tint = if (isListeningVoice) {
                                            MaterialTheme.colorScheme.onTertiary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier
                                            .size(22.dp)
                                            .alpha(if (isListeningVoice) micAlpha else 1f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // 1-Tap Multimodal Upload Buttons (More, CameraX, Gallery, Video, File)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                IconButton(
                                    onClick = onOpenAttachmentSheet,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("composer_more_attachments_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = stringResource(R.string.cd_more_attachments),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = onOpenCamera,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("composer_camera_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PhotoCamera,
                                        contentDescription = stringResource(R.string.cd_open_camera),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = onPickPhoto,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("composer_photo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PhotoLibrary,
                                        contentDescription = stringResource(R.string.cd_attach_photo),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = onPickVideo,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("composer_video_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Videocam,
                                        contentDescription = stringResource(R.string.cd_attach_video),
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                IconButton(
                                    onClick = onPickData,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("composer_data_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Description,
                                        contentDescription = stringResource(R.string.cd_attach_data),
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }

                            // Send or Stop CTA
                            if (isStreaming) {
                                Surface(
                                    onClick = onStopClick,
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("stop_generation_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Stop,
                                            contentDescription = stringResource(R.string.cd_stop_generation),
                                            tint = MaterialTheme.colorScheme.onError,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    onClick = onSendClick,
                                    enabled = canSend,
                                    shape = CircleShape,
                                    color = if (canSend) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("send_message_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Send,
                                            contentDescription = stringResource(R.string.cd_send_message),
                                            tint = if (canSend) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingAttachmentChip(
    attachment: MediaAttachment,
    onRemove: () -> Unit,
    onClick: () -> Unit
) {
    Box(modifier = Modifier.testTag("pending_attachment_chip")) {
        when (attachment.type) {
            AttachmentType.PHOTO, AttachmentType.VIDEO -> {
                Surface(
                    onClick = onClick,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val thumb = attachment.thumbnailUriString ?: attachment.uriString
                        if (attachment.type == AttachmentType.PHOTO || !attachment.thumbnailUriString.isNullOrBlank()) {
                            AsyncImage(
                                model = thumb,
                                contentDescription = attachment.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Videocam,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        if (attachment.type == AttachmentType.VIDEO) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Surface(
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 3.dp)
                            ) {
                                Text(
                                    text = formatDuration(attachment.durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            AttachmentType.DATA -> {
                Surface(
                    onClick = onClick,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .height(72.dp)
                        .widthIn(min = 132.dp, max = 188.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.tertiaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = attachment.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatFileSize(attachment.sizeBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Remove badge
        Surface(
            onClick = onRemove,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(24.dp)
                .testTag("remove_pending_attachment_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cd_remove_attachment),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
