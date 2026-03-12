package com.example.bachelor_ai_project.features.form.domain

/**
 * Definiert eine einzelne Formular-Frage.
 *
 * @param id       Eindeutige, stabile ID – wird für Antworten-Mapping verwendet.
 * @param label    Anzeigetext der Frage.
 * @param hint     Optionaler Platzhaltertext im Eingabefeld.
 * @param required Ob die Frage beantwortet sein muss.
 */
data class FormQuestion(
    val id: String,
    val label: String,
    val hint: String = "",
    val required: Boolean = false,
)

/**
 * Hält den aktuellen Antwortwert für eine Frage.
 *
 * @param questionId Referenz auf [FormQuestion.id].
 * @param value      Aktuell eingegebener Text.
 */
data class FormAnswer(
    val questionId: String,
    val value: String = "",
)

/**
 * Kombiniert Frage und zugehörige Antwort für die UI.
 */
data class FormEntry(
    val question: FormQuestion,
    val answer: FormAnswer,
)

