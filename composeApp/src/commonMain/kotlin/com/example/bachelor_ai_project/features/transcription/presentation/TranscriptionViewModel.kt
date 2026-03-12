package com.example.bachelor_ai_project.features.transcription.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bachelor_ai_project.core.result.AppResult
import com.example.bachelor_ai_project.features.transcription.domain.TranscribeAudioUseCase
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionRepository
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel für den Transcription-Screen.
 *
 * Zuständigkeiten:
 * - Nimmt den Dateipfad der fertigen Aufnahme entgegen
 * - Delegiert die Transkription an [TranscribeAudioUseCase]
 * - Hält [TranscriptionUiState] als [StateFlow]
 * - Ruft [onTranscriptionResult] auf, sobald ein Ergebnis vorliegt
 */
class TranscriptionViewModel(
    transcriptionRepository: TranscriptionRepository,
) : ViewModel() {

    private val transcribeAudio = TranscribeAudioUseCase(transcriptionRepository)

    /**
     * Optionaler Callback – wird nach erfolgreicher Transkription aufgerufen.
     * Wird vom übergeordneten [AppViewModel] gesetzt.
     */
    var onTranscriptionResult: ((TranscriptionResponse) -> Unit)? = null

    private val _uiState = MutableStateFlow(TranscriptionUiState())
    val uiState: StateFlow<TranscriptionUiState> = _uiState.asStateFlow()

    /**
     * Startet die Transkription für die angegebene Audiodatei.
     */
    fun transcribe(audioFilePath: String) {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = transcribeAudio(audioFilePath)) {
                is AppResult.Success -> {
                    println("DEBUG TranscriptionViewModel: Transkription erfolgreich, ${result.data.segments.size} Segmente, text='${result.data.text.take(100)}'")
                    _uiState.update {
                        it.copy(isLoading = false, segments = result.data.segments)
                    }
                    onTranscriptionResult?.invoke(result.data)
                }
                is AppResult.Error -> {
                    println("DEBUG TranscriptionViewModel: Transkription fehlgeschlagen: ${result.message}")
                    _uiState.update {
                        it.copy(isLoading = false, error = "Transkription fehlgeschlagen: ${result.message}")
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun reset() {
        _uiState.update { TranscriptionUiState() }
    }
}



