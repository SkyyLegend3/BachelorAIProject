package com.example.bachelor_ai_project.features.recording.domain

/**
 * Factory, die eine plattformspezifische [AudioRecorder]-Instanz erzeugt.
 * Wird per expect/actual in den Plattform-Sourcessets implementiert.
 */
interface AudioRecorderFactory {
    fun create(): AudioRecorder
}

expect fun createAudioRecorderFactory(): AudioRecorderFactory

