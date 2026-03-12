package com.example.bachelor_ai_project.features.form.data

import com.example.bachelor_ai_project.features.form.domain.FormDefinitionProvider
import com.example.bachelor_ai_project.features.form.domain.FormQuestion

/**
 * Konkrete Implementierung von [FormDefinitionProvider].
 *
 * Neue Fragen einfach als weiteres [FormQuestion]-Objekt anhängen.
 * Die [id] muss eindeutig und stabil sein (wird für Antworten-Mapping genutzt).
 */
class DefaultFormDefinitionProvider : FormDefinitionProvider {

    override val questions: List<FormQuestion> = listOf(
        FormQuestion(
            id = "name",
            label = "Wie heißt du?",
            hint = "Deinen Namen eingeben…",
            required = true,
        ),
        FormQuestion(
            id = "problem",
            label = "Was war das Problem?",
            hint = "Beschreibe das Problem…",
            required = true,
        ),
        FormQuestion(
            id = "learning",
            label = "Was hast du gelernt?",
            hint = "Was nimmst du mit?",
            required = false,
        ),
    )
}

