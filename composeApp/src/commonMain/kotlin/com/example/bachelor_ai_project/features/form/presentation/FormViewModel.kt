package com.example.bachelor_ai_project.features.form.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bachelor_ai_project.core.result.AppResult
import com.example.bachelor_ai_project.features.form.domain.FormAnswer
import com.example.bachelor_ai_project.features.form.domain.FormAutomationMode
import com.example.bachelor_ai_project.features.form.domain.FormDefinitionProvider
import com.example.bachelor_ai_project.features.form.domain.FormEntry
import com.example.bachelor_ai_project.features.form.domain.FormMappingRepository
import com.example.bachelor_ai_project.features.form.domain.MappingStrategy
import com.example.bachelor_ai_project.features.form.domain.MapTranscriptToFormUseCase
import com.example.bachelor_ai_project.features.form.domain.OnDeviceFormMappingConfigurable
import com.example.bachelor_ai_project.features.form.domain.OnDeviceLlmModelStatusProvider
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

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
        private const val ON_DEVICE_MAPPING_TIMEOUT_MS = 90_000L
    }

    private var lastTranscript: TranscriptionResponse? = null
    private val onDeviceConfigurableRepository = onDeviceMappingRepository as? OnDeviceFormMappingConfigurable
    private val onDeviceModelStatusProvider = onDeviceMappingRepository as? OnDeviceLlmModelStatusProvider

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
            isOnDeviceLlmModelReady = isOnDeviceLlmModelReady(),
            isOnDeviceLlmModelLoading = isOnDeviceLlmModelLoading(),
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
            val mode = _uiState.value.automationMode
            val startedAt = TimeSource.Monotonic.markNow()
            appendLog("Mapping-Start: mode=${mode.name}")

            if (mode == FormAutomationMode.ON_DEVICE) {
                refreshOnDeviceLlmModelReady()
            }

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
            _uiState.update { it.copy(isMappingLoading = true, mappingError = null, mappingSourceError = null) }

            val result = try {
                withContext(Dispatchers.Default) {
                    if (mode == FormAutomationMode.ON_DEVICE) {
                        withTimeout(ON_DEVICE_MAPPING_TIMEOUT_MS) {
                            MapTranscriptToFormUseCase(activeRepository()).invoke(response)
                        }
                    } else {
                        MapTranscriptToFormUseCase(activeRepository()).invoke(response)
                    }
                }
            } catch (_: TimeoutCancellationException) {
                appendLog("On-Device-Mapping Timeout nach ${ON_DEVICE_MAPPING_TIMEOUT_MS / 1000}s")
                AppResult.Error(
                    "On-Device-Mapping hat zu lange gedauert. Bitte erneut versuchen oder auf Cloud wechseln."
                )
            }

            val durationMs = startedAt.elapsedNow().inWholeMilliseconds

            when (result) {
                is AppResult.Success -> {
                    val mappingResult = result.data
                    println("DEBUG FormViewModel: Mapping erfolgreich, fieldAnswers=${mappingResult.fieldAnswers}")
                    appendLog("Transkript-Debug: speakerBlocks=${mappingResult.speakerBlocks.size}")
                    appendLog(
                        when (mappingResult.mappingStrategy) {
                            MappingStrategy.CLOUD_LLM -> "Mapping-Quelle: LLM-Mapping (Cloud)"
                            MappingStrategy.ON_DEVICE_LLM -> "Mapping-Quelle: LLM-Mapping (On Device)"
                            MappingStrategy.MIXED -> "Mapping-Quelle: LLM + Heuristik/Fallback"
                            MappingStrategy.HEURISTIC_FALLBACK -> "Mapping-Quelle: Heuristik/Fallback"
                            MappingStrategy.UNKNOWN -> "Mapping-Quelle: Unbekannt"
                        }
                    )
                    val sourceErrorMessage = if (
                        mode == FormAutomationMode.ON_DEVICE &&
                        (mappingResult.mappingStrategy == MappingStrategy.HEURISTIC_FALLBACK || mappingResult.mappingStrategy == MappingStrategy.UNKNOWN) &&
                        !mappingResult.llmFailureReason.isNullOrBlank()
                    ) {
                        "LLM-Mapping nicht moeglich: ${mappingResult.llmFailureReason}"
                    } else {
                        null
                    }
                    if (!sourceErrorMessage.isNullOrBlank()) {
                        appendLog(sourceErrorMessage)
                    }
                    if (mappingResult.fieldAnswers.isEmpty()) {
                        appendLog("Mapping abgeschlossen: keine automatische Feldzuordnung erkannt")
                    } else {
                        appendLog("Mapping abgeschlossen: ${mappingResult.fieldAnswers.size} Feld(er) befuellt")
                    }
                    appendLog("Mapping-Ende: ${durationMs}ms")
                    _uiState.update { current ->
                        current.copy(
                            isMappingLoading = false,
                            mappingError = null,
                            mappingSourceError = sourceErrorMessage,
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
                    appendLog("Mapping-Ende (Fehler): ${durationMs}ms")
                    val uiMessage = if (result.message.contains("Transkript ist leer", ignoreCase = true)) {
                        "Kein Transkriptinhalt erkannt. Bitte Aufnahme und On-Device-Transkription pruefen."
                    } else {
                        "Mapping fehlgeschlagen: ${result.message}"
                    }
                    _uiState.update {
                        it.copy(
                            isMappingLoading = false,
                            mappingError = uiMessage,
                            mappingSourceError = null,
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
            refreshOnDeviceLlmModelReady()
            warmupOnDeviceModelIfNeeded()
            onDeviceConfigurableRepository?.setOrthographyCorrectionEnabled(
                _uiState.value.onDeviceOrthographyCorrectionEnabled
            )
        }

        _uiState.update {
            it.copy(
                automationMode = mode,
                mappingError = null,
                mappingSourceError = null,
                isLlmTestRunning = false,
                llmTestSuccess = null,
            )
        }
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
        _uiState.update { it.copy(mappingError = null, mappingSourceError = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(submitError = null) }
    }

    fun runOnDeviceLlmTest() {
        val provider = onDeviceModelStatusProvider ?: return
        val current = _uiState.value
        if (current.automationMode != FormAutomationMode.ON_DEVICE) return
        if (!current.supportsOnDeviceMapping) return
        if (current.isLlmTestRunning) return

        viewModelScope.launch {
            appendLog("LLM-Test gestartet")
            _uiState.update { it.copy(isLlmTestRunning = true, llmTestSuccess = null) }

            val testResult = withContext(Dispatchers.Default) {
                provider.runOnDeviceLlmSelfTest()
            }

            when (testResult) {
                is AppResult.Success -> {
                    appendLog("LLM-Test erfolgreich")
                    _uiState.update { it.copy(isLlmTestRunning = false, llmTestSuccess = true) }
                }
                is AppResult.Error -> {
                    appendLog("LLM-Test fehlgeschlagen: ${testResult.message}")
                    _uiState.update { it.copy(isLlmTestRunning = false, llmTestSuccess = false) }
                }
            }
        }
    }

    fun setOnDeviceOrthographyCorrectionEnabled(enabled: Boolean) {
        _uiState.update {
            it.copy(
                onDeviceOrthographyCorrectionEnabled = enabled,
                mappingError = null,
                mappingSourceError = null,
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
                mappingSourceError = null,
                mappingLogs = emptyList(),
                automationMode = FormAutomationMode.CLOUD,
                isOnDeviceLlmModelReady = isOnDeviceLlmModelReady(),
                isOnDeviceLlmModelLoading = false,
                isLlmTestRunning = false,
                llmTestSuccess = null,
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

    private fun refreshOnDeviceLlmModelReady() {
        _uiState.update { current ->
            current.copy(
                isOnDeviceLlmModelReady = isOnDeviceLlmModelReady(),
                isOnDeviceLlmModelLoading = isOnDeviceLlmModelLoading(),
            )
        }
    }

    private fun isOnDeviceLlmModelReady(): Boolean {
        return onDeviceModelStatusProvider?.isOnDeviceLlmModelReady() == true
    }

    private fun isOnDeviceLlmModelConfigured(): Boolean {
        return onDeviceModelStatusProvider?.isOnDeviceLlmModelConfigured() == true
    }

    private fun isOnDeviceLlmModelLoading(): Boolean {
        return onDeviceModelStatusProvider?.isOnDeviceLlmModelLoading() == true
    }

    private fun warmupOnDeviceModelIfNeeded() {
        val provider = onDeviceModelStatusProvider ?: return
        if (!isOnDeviceLlmModelConfigured()) return
        if (provider.isOnDeviceLlmModelReady()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isOnDeviceLlmModelLoading = true) }

            val warmup = withContext(Dispatchers.Default) {
                provider.warmupOnDeviceLlmModel()
            }
            when (warmup) {
                is AppResult.Success -> {
                    appendLog("LLM-Model geladen und bereit")
                }
                is AppResult.Error -> {
                    appendLog("LLM-Model laden fehlgeschlagen: ${warmup.message}")
                }
            }

            refreshOnDeviceLlmModelReady()
        }
    }
}
