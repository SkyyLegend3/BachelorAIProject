package com.example.bachelor_ai_project.features.recording.domain

actual fun createAudioRecorderFactory(): AudioRecorderFactory {
    return AndroidAudioRecorderFactory(AppContextHolder.applicationContext)
}


