package com.example.bachelor_ai_project.features.transcription.presentation

import com.example.bachelor_ai_project.features.transcription.domain.TranscriptSegment
import com.example.bachelor_ai_project.features.transcription.domain.OnDeviceWhisperModelState

/**
 * Repräsentiert den gesamten UI-Zustand des Transcription-Screens.
 */
data class TranscriptionUiState(
    val isLoading: Boolean = false,
    val segments: List<TranscriptSegment> = emptyList(),
    val error: String? = null,
    val debugLogs: List<String> = emptyList(),
    val onDeviceModelState: OnDeviceWhisperModelState = OnDeviceWhisperModelState(),
) {
    /** `true` sobald mindestens ein Segment vorhanden ist. */
    val hasResult: Boolean get() = segments.isNotEmpty()
}

