package com.example.bachelor_ai_project.features.transcription.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bachelor_ai_project.core.result.AppResult
import com.example.bachelor_ai_project.features.form.domain.FormAutomationMode
import com.example.bachelor_ai_project.features.transcription.domain.TranscribeAudioUseCase
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionRepository
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

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
    cloudTranscriptionRepository: TranscriptionRepository,
    onDeviceTranscriptionRepository: TranscriptionRepository? = null,
) : ViewModel() {

    companion object {
        private const val MAX_LOG_ENTRIES = 20
    }

    private val transcribeCloudAudio = TranscribeAudioUseCase(cloudTranscriptionRepository)
    private val transcribeOnDeviceAudio = onDeviceTranscriptionRepository?.let { TranscribeAudioUseCase(it) }
    private var automationMode: FormAutomationMode = FormAutomationMode.CLOUD

    /**
     * Optionaler Callback – wird nach erfolgreicher Transkription aufgerufen.
     * Wird vom uebergeordneten App-ViewModel gesetzt.
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
            val startedAt = TimeSource.Monotonic.markNow()
            appendLog("Transkriptions-Start: mode=${automationMode.name}")

            appendLog(
                if (automationMode == FormAutomationMode.ON_DEVICE)
                    "Transkriptionsmodus: On Device"
                else
                    "Transkriptionsmodus: Cloud"
            )
            appendLog("Transkription gestartet")
            appendLog("Audio-Quelle: ${audioFilePath.takeLast(60)}")
            _uiState.update { it.copy(isLoading = true, error = null) }

            val useCase = activeUseCase()
            val result = withContext(Dispatchers.Default) {
                useCase(audioFilePath)
            }

            val durationMs = startedAt.elapsedNow().inWholeMilliseconds

            when (result) {
                is AppResult.Success -> {
                    println("DEBUG TranscriptionViewModel: Transkription erfolgreich, ${result.data.segments.size} Segmente, text='${result.data.text.take(100)}'")
                    appendLog(
                        "Transkript-Ergebnis: textLen=${result.data.text.trim().length}, " +
                            "segments=${result.data.segments.size}, words=${result.data.words.size}"
                    )
                    appendLog("Transkriptions-Ende: ${durationMs}ms")
                    _uiState.update {
                        it.copy(isLoading = false, segments = result.data.segments)
                    }
                    onTranscriptionResult?.invoke(result.data)
                }
                is AppResult.Error -> {
                    println("DEBUG TranscriptionViewModel: Transkription fehlgeschlagen: ${result.message}")
                    appendLog("Transkription fehlgeschlagen: ${result.message}")
                    appendLog("Transkriptions-Ende (Fehler): ${durationMs}ms")
                    if (automationMode == FormAutomationMode.ON_DEVICE) {
                        appendLog("On-Device-Diagnose: ${result.message}")
                    }
                    _uiState.update {
                        it.copy(isLoading = false, error = "Transkription fehlgeschlagen: ${result.message}")
                    }
                }
            }
        }
    }

    fun setAutomationMode(mode: FormAutomationMode) {
        if (mode == automationMode) return

        automationMode = mode
        appendLog(
            if (mode == FormAutomationMode.ON_DEVICE)
                "Transkription: On Device"
            else
                "Transkription: Cloud"
        )
    }

    fun supportsOnDeviceTranscription(): Boolean = transcribeOnDeviceAudio != null

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun reset() {
        _uiState.update { TranscriptionUiState() }
    }

    private fun appendLog(message: String) {
        _uiState.update { current ->
            current.copy(debugLogs = (current.debugLogs + message).takeLast(MAX_LOG_ENTRIES))
        }
    }

    private fun activeUseCase(): TranscribeAudioUseCase = when (automationMode) {
        FormAutomationMode.CLOUD -> transcribeCloudAudio
        FormAutomationMode.ON_DEVICE -> transcribeOnDeviceAudio ?: transcribeCloudAudio
    }
}



