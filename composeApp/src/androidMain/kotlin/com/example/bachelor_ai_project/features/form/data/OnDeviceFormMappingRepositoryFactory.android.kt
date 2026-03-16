package com.example.bachelor_ai_project.features.form.data

import com.example.bachelor_ai_project.features.form.domain.FormDefinitionProvider
import com.example.bachelor_ai_project.features.form.domain.FormMappingRepository

actual fun createOnDeviceFormMappingRepository(
    definitionProvider: FormDefinitionProvider,
): FormMappingRepository? = OnDeviceLlmFormMappingRepository(
    definitionProvider = definitionProvider,
    llmEngine = createDefaultOnDeviceLlmEngine(),
    fallbackRepository = OnDeviceKeywordFormMappingRepository(definitionProvider),
)


