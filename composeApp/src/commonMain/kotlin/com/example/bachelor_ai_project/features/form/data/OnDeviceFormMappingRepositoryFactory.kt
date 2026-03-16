package com.example.bachelor_ai_project.features.form.data

import com.example.bachelor_ai_project.features.form.domain.FormDefinitionProvider
import com.example.bachelor_ai_project.features.form.domain.FormMappingRepository

/**
 * Liefert eine plattformspezifische On-Device-Implementierung.
 *
 * Gibt `null` zurück, wenn die Plattform noch keine lokale Automatisierung unterstützt.
 */
expect fun createOnDeviceFormMappingRepository(
    definitionProvider: FormDefinitionProvider,
): FormMappingRepository?
