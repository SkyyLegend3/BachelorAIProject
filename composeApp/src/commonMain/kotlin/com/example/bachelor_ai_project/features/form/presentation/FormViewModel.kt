package com.example.bachelor_ai_project.features.form.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bachelor_ai_project.core.result.AppResult
import com.example.bachelor_ai_project.features.form.domain.FormAnswer
import com.example.bachelor_ai_project.features.form.domain.FormAutomationMode
import com.example.bachelor_ai_project.features.form.domain.FormDefinitionProvider
import com.example.bachelor_ai_project.features.form.domain.FormEntry
import com.example.bachelor_ai_project.features.form.domain.FormMappingRepository
import com.example.bachelor_ai_project.features.form.domain.MapTranscriptToFormUseCase
import com.example.bachelor_ai_project.features.form.domain.OnDeviceFormMappingConfigurable
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel für den Formular-Screen.
 *
 * Zuständigkeiten:
 * - Initialisiert Fragen aus [FormDefinitionProvider]
 * - Verarbeitet Nutzereingaben pro Frage ([updateAnswer])
 * - Mappt eine [TranscriptionResponse] auf Formularfelder via [MapTranscriptToFormUseCase]
 * - Stellt Validierungszustand bereit ([FormUiState.isValid])
 */
class FormViewModel(
    definitionProvider: FormDefinitionProvider,
    private val cloudMappingRepository: FormMappingRepository,
    private val onDeviceMappingRepository: FormMappingRepository? = null,
) : ViewModel() {

    companion object {
        private const val MAX_LOG_ENTRIES = 16
    }

    private var lastTranscript: TranscriptionResponse? = null
    private val onDeviceConfigurableRepository = onDeviceMappingRepository as? OnDeviceFormMappingConfigurable

    var onAutomationModeChanged: ((FormAutomationMode) -> Unit)? = null

    private val _uiState = MutableStateFlow(
        FormUiState(
            entries = definitionProvider.questions.map { question ->
                FormEntry(
                    question = question,
                    answer = FormAnswer(questionId = question.id),
                )
            },
            supportsOnDeviceMapping = onDeviceMappingRepository != null,
        ),
    )
    val uiState: StateFlow<FormUiState> = _uiState.asStateFlow()

    /**
     * Mappt die fertige [TranscriptionResponse] auf Formularfelder.
     */
    fun applyTranscript(response: TranscriptionResponse) {
        if (_uiState.value.isMappingLoading) return
        lastTranscript = response

        viewModelScope.launch {
            val nonBlankSegmentCount = response.segments.count { it.text.isNotBlank() }
            val nonBlankWordCount = response.words.count { it.word.isNotBlank() }
            appendLog(
                "Transkript-Debug: textLen=${response.text.trim().length}, " +
                    "segments=${response.segments.size}, segText=${nonBlankSegmentCount}, words=${nonBlankWordCount}"
            )

            appendLog(
                if (_uiState.value.automationMode == FormAutomationMode.ON_DEVICE)
                    "Starte On-Device-Mapping..."
                else
                    "Starte Cloud-Mapping..."
            )
            _uiState.update { it.copy(isMappingLoading = true, mappingError = null) }

            when (val result = MapTranscriptToFormUseCase(activeRepository()).invoke(response)) {
                is AppResult.Success -> {
                    val mappingResult = result.data
                    println("DEBUG FormViewModel: Mapping erfolgreich, fieldAnswers=${mappingResult.fieldAnswers}")
                    appendLog("Transkript-Debug: speakerBlocks=${mappingResult.speakerBlocks.size}")
                    if (mappingResult.fieldAnswers.isEmpty()) {
                        appendLog("Mapping abgeschlossen, aber 0 Felder erkannt")
                    } else {
                        appendLog("Mapping abgeschlossen: ${mappingResult.fieldAnswers.size} Feld(er) befuellt")
                    }
                    _uiState.update { current ->
                        current.copy(
                            isMappingLoading = false,
                            mappingError = if (mappingResult.fieldAnswers.isEmpty()) {
                                "Keine passenden Inhalte fuer die Formularfelder erkannt."
                            } else {
                                null
                            },
                            speakerBlocks = mappingResult.speakerBlocks,
                            entries = current.entries.map { entry ->
                                val prefilled = mappingResult.fieldAnswers[entry.question.id]
                                if (!prefilled.isNullOrBlank()) {
                                    entry.copy(answer = entry.answer.copy(value = prefilled))
                                } else {
                                    entry
                                }
                            },
                        )
                    }
                }
                is AppResult.Error -> {
                    println("DEBUG FormViewModel: Mapping fehlgeschlagen: ${result.message}")
                    appendLog("Mapping fehlgeschlagen: ${result.message}")
                    _uiState.update {
                        it.copy(
                            isMappingLoading = false,
                            mappingError = "Mapping fehlgeschlagen: ${result.message}",
                        )
                    }
                }
            }
        }
    }

    fun setAutomationMode(mode: FormAutomationMode) {
        val current = _uiState.value
        if (current.automationMode == mode) return
        if (mode == FormAutomationMode.ON_DEVICE && onDeviceMappingRepository == null) return

        appendLog(
            if (mode == FormAutomationMode.ON_DEVICE)
                "Automatisierung: On Device"
            else
                "Automatisierung: Cloud"
        )

        if (mode == FormAutomationMode.ON_DEVICE) {
            onDeviceConfigurableRepository?.setOrthographyCorrectionEnabled(
                _uiState.value.onDeviceOrthographyCorrectionEnabled
            )
        }

        _uiState.update { it.copy(automationMode = mode, mappingError = null) }
        onAutomationModeChanged?.invoke(mode)

        lastTranscript?.let { applyTranscript(it) }
    }

    /**
     * Aktualisiert den Antworttext für die Frage mit der angegebenen [questionId].
     */
    fun updateAnswer(questionId: String, value: String) {
        _uiState.update { current ->
            current.copy(
                entries = current.entries.map { entry ->
                    if (entry.question.id == questionId) {
                        entry.copy(answer = entry.answer.copy(value = value))
                    } else {
                        entry
                    }
                },
            )
        }
    }

    fun clearMappingError() {
        _uiState.update { it.copy(mappingError = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(submitError = null) }
    }

    fun setOnDeviceOrthographyCorrectionEnabled(enabled: Boolean) {
        _uiState.update {
            it.copy(
                onDeviceOrthographyCorrectionEnabled = enabled,
                mappingError = null,
            )
        }

        appendLog(
            if (enabled)
                "On-Device Rechtschreibkorrektur: AN"
            else
                "On-Device Rechtschreibkorrektur: AUS"
        )

        onDeviceConfigurableRepository?.setOrthographyCorrectionEnabled(enabled)

        val shouldRemapNow = _uiState.value.automationMode == FormAutomationMode.ON_DEVICE
        if (shouldRemapNow) {
            lastTranscript?.let { applyTranscript(it) }
        }
    }

    /**
     * Setzt das gesamte Formular auf den Ausgangszustand zurück.
     */
    fun reset() {
        _uiState.update { current ->
            current.copy(
                entries = current.entries.map { entry ->
                    entry.copy(answer = entry.answer.copy(value = ""))
                },
                speakerBlocks = emptyList(),
                isMappingLoading = false,
                mappingError = null,
                mappingLogs = emptyList(),
                automationMode = FormAutomationMode.CLOUD,
                onDeviceOrthographyCorrectionEnabled = true,
                isSubmitting = false,
                submitError = null,
                isSubmitted = false,
            )
        }
        onAutomationModeChanged?.invoke(FormAutomationMode.CLOUD)
        lastTranscript = null
    }

    private fun activeRepository(): FormMappingRepository = when (_uiState.value.automationMode) {
        FormAutomationMode.CLOUD -> cloudMappingRepository
        FormAutomationMode.ON_DEVICE -> onDeviceMappingRepository ?: cloudMappingRepository
    }

    private fun appendLog(message: String) {
        _uiState.update { current ->
            val updated = (current.mappingLogs + message).takeLast(MAX_LOG_ENTRIES)
            current.copy(mappingLogs = updated)
        }
    }
}
