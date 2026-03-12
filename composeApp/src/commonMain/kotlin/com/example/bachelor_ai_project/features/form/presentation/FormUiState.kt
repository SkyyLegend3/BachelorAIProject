package com.example.bachelor_ai_project.features.form.presentation

import com.example.bachelor_ai_project.features.form.domain.FormEntry
import com.example.bachelor_ai_project.features.form.domain.SpeakerBlock

/**
 * Repräsentiert den vollständigen UI-Zustand des Formular-Screens.
 *
 * @param entries        Kombinierte Liste aus Fragen und deren Antworten.
 * @param speakerBlocks  Sprecher-Blöcke aus dem Transkript (für Anzeige unterhalb des Formulars).
 * @param isMappingLoading  Wird gesetzt, während das Transkript auf Felder gemappt wird.
 * @param mappingError   Fehlermeldung, falls das Mapping fehlschlug.
 * @param isSubmitting   Wird gesetzt, während das Formular abgesendet wird.
 * @param submitError    Fehlermeldung nach einem fehlgeschlagenen Absenden.
 * @param isSubmitted    Wird `true`, sobald das Formular erfolgreich abgesendet wurde.
 */
data class FormUiState(
    val entries: List<FormEntry> = emptyList(),
    val speakerBlocks: List<SpeakerBlock> = emptyList(),
    val isMappingLoading: Boolean = false,
    val mappingError: String? = null,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val isSubmitted: Boolean = false,
) {
    /** `true` sobald Sprecher-Blöcke vorhanden sind. */
    val hasTranscript: Boolean get() = speakerBlocks.isNotEmpty()

    /** Gibt an, ob alle Pflichtfelder ausgefüllt sind. */
    val isValid: Boolean
        get() = entries.all { entry ->
            !entry.question.required || entry.answer.value.isNotBlank()
        }
}

