package com.example.bachelor_ai_project.features.form.domain

import kotlinx.serialization.Serializable

/**
 * JSON-Antwort von GPT-4o-mini für das Formular-Feldmapping.
 *
 * Das Modell wird angewiesen, ausschließlich dieses Schema zurückzugeben.
 * Felder, die aus dem Transkript nicht eindeutig ableitbar sind,
 * müssen als leerer String `""` zurückgegeben werden – niemals erfunden.
 *
 * [answers] ist eine Map `questionId → extrahierter Text`.
 * Felder ohne passenden Inhalt im Transkript werden weggelassen bzw. auf `""` gesetzt.
 */
@Serializable
data class LlmFieldMappingResponse(
    val answers: Map<String, String>,
)

