package com.example.bachelor_ai_project.features.transcription.data

import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionRepository

/**
 * Liefert eine plattformspezifische On-Device-Transkriptions-Implementierung.
 *
 * Gibt `null` zurueck, wenn lokal keine Engine konfiguriert oder verfuegbar ist.
 */
expect fun createOnDeviceTranscriptionRepository(): TranscriptionRepository?

