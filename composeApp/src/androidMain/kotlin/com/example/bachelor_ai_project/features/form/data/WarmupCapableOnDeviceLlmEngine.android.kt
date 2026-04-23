package com.example.bachelor_ai_project.features.form.data

import com.example.bachelor_ai_project.core.result.AppResult

/**
 * Optionales Engine-Interface fuer einmaliges Modell-Vorladen und Statusabfrage.
 */
interface WarmupCapableOnDeviceLlmEngine : OnDeviceLlmEngine {
    fun isModelLoaded(): Boolean
    fun isModelLoading(): Boolean
    suspend fun warmupModel(): AppResult<Unit>
}

