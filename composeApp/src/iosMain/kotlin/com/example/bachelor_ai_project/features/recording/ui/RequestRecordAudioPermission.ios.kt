package com.example.bachelor_ai_project.features.recording.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted

@Composable
actual fun RequestRecordAudioPermission(
    onGranted: () -> Unit,
    onDenied: () -> Unit,
) {
    LaunchedEffect(Unit) {
        val session = AVAudioSession.sharedInstance()
        when (session.recordPermission()) {
            AVAudioSessionRecordPermissionGranted -> onGranted()
            else -> session.requestRecordPermission { granted ->
                if (granted) onGranted() else onDenied()
            }
        }
    }
}

