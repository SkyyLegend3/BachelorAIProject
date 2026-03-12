package com.example.bachelor_ai_project.features.recording.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bachelor_ai_project.features.recording.domain.RecordingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel für den Recording-Screen.
 *
 * Zuständigkeiten:
 * - Startet / stoppt die Aufnahme via [RecordingRepository] auf [Dispatchers.IO]
 * - Hält den [RecordingUiState] als [StateFlow]
 * - Signalisiert per onRecordingFinished den fertigen Dateipfad an den übergeordneten
 *   ViewModel / Navigator, damit die Transkription angestoßen werden kann
 */
class RecordingViewModel(
    private val recordingRepository: RecordingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    /** Callback, der nach dem Stoppen der Aufnahme mit dem Dateipfad aufgerufen wird. */
    var onRecordingFinished: ((filePath: String) -> Unit)? = null

    fun onPermissionGranted() {
        _uiState.update { it.copy(isPermissionGranted = true, error = null) }
    }

    fun onPermissionDenied() {
        _uiState.update {
            it.copy(
                isPermissionGranted = false,
                error = "Mikrofonzugriff wurde verweigert. Bitte in den Einstellungen freigeben."
            )
        }
    }

    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { recordingRepository.startRecording() }
                .onSuccess { path ->
                    _uiState.update { it.copy(isRecording = true, recordingFilePath = path, error = null) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = "Aufnahme konnte nicht gestartet werden: ${e.message}") }
                }
        }
    }

    private fun stopRecording() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { recordingRepository.stopRecording() }
                .onSuccess { path ->
                    _uiState.update { it.copy(isRecording = false, recordingFilePath = path) }
                    onRecordingFinished?.invoke(path)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isRecording = false, error = "Aufnahme konnte nicht gestoppt werden: ${e.message}") }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
