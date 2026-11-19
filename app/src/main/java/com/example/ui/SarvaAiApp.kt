package com.example.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.AttachmentType
import com.example.ui.components.AttachmentPickerSheet
import com.example.ui.components.AttachmentPreviewDialog
import com.example.ui.components.CameraCaptureScreen
import com.example.ui.components.ChatMessageList
import com.example.ui.components.FirebasePreviewInfoDialog
import com.example.ui.components.HistoryDrawerContent
import com.example.ui.components.MultimodalComposer
import com.example.ui.components.WelcomeCanvas
import kotlinx.coroutines.launch

@Composable
fun SarvaAiApp(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showCameraCapture by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    // Zero-permission Android Photo Picker (Images)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.attachMediaUri(uri, AttachmentType.PHOTO)
        }
    }

    // Zero-permission Android Photo Picker (Videos)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.attachMediaUri(uri, AttachmentType.VIDEO)
        }
    }

    // Zero-permission Storage Access Framework (PDF, CSV, JSON, TXT, Audio, etc.)
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.attachMediaUri(uri, AttachmentType.DATA)
        }
    }

    val micPermissionDeniedMsg = stringResource(R.string.voice_permission_denied_snackbar)
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceInput()
        } else {
            viewModel.onVoicePermissionDenied(micPermissionDeniedMsg)
        }
    }

    val handleVoiceInputClick = {
        if (uiState.isListeningVoice) {
            viewModel.stopVoiceInput()
        } else {
            val hasMicPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasMicPermission) {
                viewModel.startVoiceInput()
            } else {
                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val launchPhotoPicker = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
    val launchVideoPicker = {
        videoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }
    val launchDataPicker = {
        documentPickerLauncher.launch(arrayOf("*/*"))
    }

    val copiedMessage = stringResource(R.string.copied_snackbar)
    val copyToClipboard: (String) -> Unit = { text ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Sarva AI Response", text))
        scope.launch {
            snackbarHostState.showSnackbar(copiedMessage)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 840.dp
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

        BackHandler(enabled = !isExpandedScreen && drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }

        val drawerContent: @Composable () -> Unit = {
            HistoryDrawerContent(
                sessions = uiState.sessions,
                activeSessionId = uiState.activeSessionId,
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = viewModel::onSearchQueryChanged,
                onNewChatClick = {
                    viewModel.startNewChat()
                    if (!isExpandedScreen) scope.launch { drawerState.close() }
                },
                onSessionClick = { id ->
                    viewModel.selectSession(id)
                    if (!isExpandedScreen) scope.launch { drawerState.close() }
                },
                onDeleteSession = viewModel::deleteSession
            )
        }

        val workspaceContent: @Composable () -> Unit = {
            WorkspaceScaffold(
                uiState = uiState,
                snackbarHostState = snackbarHostState,
                showDrawerToggle = !isExpandedScreen,
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onNewChat = viewModel::startNewChat,
                onToggleTheme = viewModel::toggleTheme,
                onToggleAutoSpeak = viewModel::toggleAutoSpeak,
                onOpenPreviewInfo = { viewModel.setShowPreviewInfoDialog(true) },
                onOpenCamera = { showCameraCapture = true },
                onPickPhoto = launchPhotoPicker,
                onPickVideo = launchVideoPicker,
                onPickData = launchDataPicker,
                onOpenAttachmentSheet = { showAttachmentSheet = true },
                onPromptChange = viewModel::onPromptChanged,
                onSendClick = viewModel::sendCurrentPrompt,
                onStopClick = viewModel::stopGeneration,
                onVoiceInputClick = handleVoiceInputClick,
                onQuickPromptClick = viewModel::sendQuickPrompt,
                onRemoveAttachment = viewModel::removePendingAttachment,
                onInspectAttachment = viewModel::openAttachmentPreview,
                onCopyResponse = copyToClipboard,
                onToggleSpeakMessage = viewModel::toggleSpeakMessage,
                onRegenerate = viewModel::regenerateLastResponse,
                onDismissError = viewModel::dismissError
            )
        }

        if (isExpandedScreen) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surface
                    ) {
                        drawerContent()
                    }
                },
                content = workspaceContent
            )
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surface
                    ) {
                        drawerContent()
                    }
                },
                content = workspaceContent
            )
        }
    }

    if (showAttachmentSheet) {
        AttachmentPickerSheet(
            onDismiss = { showAttachmentSheet = false },
            onOpenCamera = { showCameraCapture = true },
            onPickPhoto = launchPhotoPicker,
            onPickVideo = launchVideoPicker,
            onPickData = launchDataPicker
        )
    }

    if (showCameraCapture) {
        CameraCaptureScreen(
            onClose = { showCameraCapture = false },
            onPhotoAttachOnly = { uri ->
                showCameraCapture = false
                viewModel.attachMediaUri(uri, AttachmentType.PHOTO)
            },
            onPhotoAnalyzeNow = { uri ->
                showCameraCapture = false
                viewModel.attachAndAnalyzePhoto(uri)
            }
        )
    }

    uiState.inspectedAttachment?.let { attachment ->
        AttachmentPreviewDialog(
            attachment = attachment,
            onDismiss = viewModel::closeAttachmentPreview
        )
    }

    if (uiState.showPreviewInfoDialog) {
        FirebasePreviewInfoDialog(
            onDismiss = { viewModel.setShowPreviewInfoDialog(false) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceScaffold(
    uiState: ChatUiState,
    snackbarHostState: SnackbarHostState,
    showDrawerToggle: Boolean,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onOpenPreviewInfo: () -> Unit,
    onOpenCamera: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickVideo: () -> Unit,
    onPickData: () -> Unit,
    onOpenAttachmentSheet: () -> Unit,
    onPromptChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
    onVoiceInputClick: () -> Unit,
    onQuickPromptClick: (String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onInspectAttachment: (com.example.model.MediaAttachment) -> Unit,
    onCopyResponse: (String) -> Unit,
    onToggleSpeakMessage: (Long, String) -> Unit,
    onRegenerate: () -> Unit,
    onDismissError: () -> Unit
) {
    val activeTitle = uiState.sessions
        .firstOrNull { it.id == uiState.activeSessionId }
        ?.title
        ?.takeIf { it.isNotBlank() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    navigationIcon = {
                        if (showDrawerToggle) {
                            IconButton(
                                onClick = onOpenDrawer,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("open_drawer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Menu,
                                    contentDescription = stringResource(R.string.cd_open_drawer)
                                )
                            }
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                if (activeTitle != null) {
                                    Text(
                                        text = activeTitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onToggleAutoSpeak,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("toggle_auto_speak_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.isAutoSpeakEnabled) {
                                    Icons.AutoMirrored.Rounded.VolumeUp
                                } else {
                                    Icons.AutoMirrored.Rounded.VolumeOff
                                },
                                contentDescription = stringResource(
                                    if (uiState.isAutoSpeakEnabled) {
                                        R.string.cd_auto_speak_on
                                    } else {
                                        R.string.cd_auto_speak_off
                                    }
                                ),
                                tint = if (uiState.isAutoSpeakEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("toggle_theme_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.isDarkTheme) {
                                    Icons.Rounded.LightMode
                                } else {
                                    Icons.Rounded.DarkMode
                                },
                                contentDescription = stringResource(R.string.cd_toggle_theme)
                            )
                        }
                        IconButton(
                            onClick = onNewChat,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("topbar_new_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.EditNote,
                                contentDescription = stringResource(R.string.cd_new_chat)
                            )
                        }
                    }
                )

                // Notice banner when live Firebase AI Logic is not enabled
                if (!uiState.isLiveAiEnabled) {
                    Surface(
                        onClick = onOpenPreviewInfo,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("firebase_preview_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = stringResource(R.string.cd_info_preview_mode),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${stringResource(R.string.preview_banner_title)} • ${stringResource(R.string.preview_banner_subtitle)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            MultimodalComposer(
                promptText = uiState.promptInput,
                pendingAttachments = uiState.pendingAttachments,
                isStreaming = uiState.generationState is GenerationState.Streaming,
                isAttachingMedia = uiState.isAttachingMedia,
                isListeningVoice = uiState.isListeningVoice,
                onPromptChange = onPromptChange,
                onSendClick = onSendClick,
                onStopClick = onStopClick,
                onVoiceInputClick = onVoiceInputClick,
                onOpenCamera = onOpenCamera,
                onPickPhoto = onPickPhoto,
                onPickVideo = onPickVideo,
                onPickData = onPickData,
                onOpenAttachmentSheet = onOpenAttachmentSheet,
                onRemoveAttachment = onRemoveAttachment,
                onInspectAttachment = onInspectAttachment
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.messages.isEmpty() && uiState.generationState is GenerationState.Idle) {
                WelcomeCanvas(
                    onOpenCamera = onOpenCamera,
                    onPickPhoto = onPickPhoto,
                    onPickVideo = onPickVideo,
                    onPickData = onPickData,
                    onStarterPromptClick = onQuickPromptClick
                )
            } else {
                ChatMessageList(
                    messages = uiState.messages,
                    generationState = uiState.generationState,
                    isLiveAiEnabled = uiState.isLiveAiEnabled,
                    speakingMessageId = uiState.speakingMessageId,
                    onAttachmentClick = onInspectAttachment,
                    onCopyClick = onCopyResponse,
                    onToggleSpeakClick = onToggleSpeakMessage,
                    onRegenerateClick = onRegenerate,
                    onFollowUpClick = onQuickPromptClick,
                    onInfoBadgeClick = onOpenPreviewInfo,
                    onDismissError = onDismissError
                )
            }
        }
    }
}
