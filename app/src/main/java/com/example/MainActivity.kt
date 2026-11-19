package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ChatRepository
import com.example.data.ai.PlaceholderAiRepository
import com.example.data.local.AppDatabase
import com.example.data.media.MediaInspector
import com.example.data.voice.VoiceInputRecognizer
import com.example.data.voice.VoiceSpeaker
import com.example.ui.ChatViewModel
import com.example.ui.SarvaAiApp
import com.example.ui.theme.SarvaAiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getInstance(applicationContext)
        val mediaInspector = MediaInspector(applicationContext)
        val aiRepository = PlaceholderAiRepository()
        val voiceSpeaker = VoiceSpeaker(applicationContext)
        val voiceInputRecognizer = VoiceInputRecognizer(applicationContext)
        val chatRepository = ChatRepository(
            chatDao = database.chatDao(),
            mediaInspector = mediaInspector,
            aiRepository = aiRepository
        )

        setContent {
            val chatViewModel: ChatViewModel = viewModel(
                factory = ChatViewModel.Factory(
                    chatRepository,
                    voiceSpeaker,
                    voiceInputRecognizer
                )
            )
            val uiState by chatViewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(uiState.isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (uiState.isDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (uiState.isDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
            }

            SarvaAiTheme(darkTheme = uiState.isDarkTheme) {
                SarvaAiApp(viewModel = chatViewModel)
            }
        }
    }
}
