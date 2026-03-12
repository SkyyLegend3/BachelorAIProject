package com.example.bachelor_ai_project.features.form.domain

/**
 * Stellt die Liste aller Formular-Fragen bereit.
 *
 * Um eine neue Frage hinzuzufügen, genügt es, einen weiteren [FormQuestion]-Eintrag
 * in [questions] anzufügen. Reihenfolge = Anzeigereihenfolge.
 */
interface FormDefinitionProvider {
    val questions: List<FormQuestion>
}

