package com.example.bachelor_ai_project.features.form.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bachelor_ai_project.core.result.AppResult
import com.example.bachelor_ai_project.features.form.domain.FormAnswer
import com.example.bachelor_ai_project.features.form.domain.FormDefinitionProvider
import com.example.bachelor_ai_project.features.form.domain.FormEntry
import com.example.bachelor_ai_project.features.form.domain.FormMappingRepository
import com.example.bachelor_ai_project.features.form.domain.MapTranscriptToFormUseCase
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
    mappingRepository: FormMappingRepository,
) : ViewModel() {

    private val mapTranscriptToForm = MapTranscriptToFormUseCase(mappingRepository)

    private val _uiState = MutableStateFlow(
        FormUiState(
            entries = definitionProvider.questions.map { question ->
                FormEntry(
                    question = question,
                    answer = FormAnswer(questionId = question.id),
                )
            },
        ),
    )
    val uiState: StateFlow<FormUiState> = _uiState.asStateFlow()

    /**
     * Mappt die fertige [TranscriptionResponse] auf Formularfelder.
     */
    fun applyTranscript(response: TranscriptionResponse) {
        if (_uiState.value.isMappingLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isMappingLoading = true, mappingError = null) }

            when (val result = mapTranscriptToForm(response)) {
                is AppResult.Success -> {
                    val mappingResult = result.data
                    println("DEBUG FormViewModel: Mapping erfolgreich, fieldAnswers=${mappingResult.fieldAnswers}")
                    _uiState.update { current ->
                        current.copy(
                            isMappingLoading = false,
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
                isSubmitting = false,
                submitError = null,
                isSubmitted = false,
            )
        }
    }
}
