package com.example.bachelor_ai_project.features.recording.domain

/**
 * Verwaltet den Lebenszyklus einer Aufnahme-Session.
 * Kapselt Pfad-Ermittlung und Recorder-Steuerung.
 */
interface RecordingRepository {
    /** Startet eine neue Aufnahme. Gibt den Dateipfad zurück. */
    fun startRecording(): String

    /** Beendet die laufende Aufnahme und gibt den fertigen Dateipfad zurück. */
    fun stopRecording(): String
}

