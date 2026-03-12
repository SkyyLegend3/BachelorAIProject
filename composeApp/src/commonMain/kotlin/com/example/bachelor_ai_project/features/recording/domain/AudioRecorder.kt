package com.example.bachelor_ai_project.features.recording.domain

/**
 * Plattformunabhängige Schnittstelle für die Audioaufnahme.
 * Actual-Implementierungen steuern MediaRecorder (Android) bzw. AVAudioRecorder (iOS).
 */
interface AudioRecorder {
    fun start(filePath: String)
    fun stop()
}

