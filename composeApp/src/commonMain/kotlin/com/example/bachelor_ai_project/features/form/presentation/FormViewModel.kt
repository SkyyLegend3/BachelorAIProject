package com.example.bachelor_ai_project.features.form.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bachelor_ai_project.core.result.AppResult
import com.example.bachelor_ai_project.features.form.domain.FormAnswer
import com.example.bachelor_ai_project.features.form.domain.FormAutomationMode
import com.example.bachelor_ai_project.features.form.domain.FormDefinitionProvider
import com.example.bachelor_ai_project.features.form.domain.FormEntry
import com.example.bachelor_ai_project.features.form.domain.FormMappingRepository
import com.example.bachelor_ai_project.features.form.domain.MapTranscriptToFormUseCase
import com.example.bachelor_ai_project.features.form.domain.OnDeviceFormMappingConfigurable
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
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
        private const val MAPPING_HEARTBEAT_MS = 5_000L
    }

    private var lastTranscript: TranscriptionResponse? = null
    private var mappingRunId: Long = 0L
    private val onDeviceConfigurableRepository = onDeviceMappingRepository as? OnDeviceFormMappingConfigurable

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
        ),
    )
    val uiState: StateFlow<FormUiState> = _uiState.asStateFlow()

    /**
     * Mappt die fertige [TranscriptionResponse] auf Formularfelder.
     */
    fun applyTranscript(response: TranscriptionResponse) {
        if (_uiState.value.isMappingLoading) return
        val runId = ++mappingRunId
        lastTranscript = response

        viewModelScope.launch {
            val mode = _uiState.value.automationMode
            val startedAt = TimeSource.Monotonic.markNow()
            appendLog("Mapping-Start: mode=${mode.name}")

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
            _uiState.update { it.copy(isMappingLoading = true, mappingError = null) }

            var heartbeatJob: Job? = null
            var safetyBrakeJob: Job? = null
            if (mode == FormAutomationMode.ON_DEVICE) {
                heartbeatJob = viewModelScope.launch(Dispatchers.Default) {
                    runCatching {
                        var elapsedMs = MAPPING_HEARTBEAT_MS
                        while (
                            _uiState.value.isMappingLoading &&
                            runId == mappingRunId
                        ) {
                            delay(MAPPING_HEARTBEAT_MS)
                            if (_uiState.value.isMappingLoading && runId == mappingRunId) {
                                appendLog("On-Device-Mapping laeuft... ${elapsedMs / 1000}s")
                            }
                            elapsedMs += MAPPING_HEARTBEAT_MS
                        }
                    }.onFailure { error ->
                        appendLog("Heartbeat-Fehler: ${error.message ?: "Unbekannt"}")
                    }
                }

                safetyBrakeJob = viewModelScope.launch(Dispatchers.Default) {
                    delay(ON_DEVICE_MAPPING_TIMEOUT_MS + 2_000L)
                    if (_uiState.value.isMappingLoading && runId == mappingRunId) {
                        appendLog("On-Device-Mapping Notbremse: UI-Zustand wird zurueckgesetzt")
                        mappingRunId++
                        _uiState.update {
                            it.copy(
                                isMappingLoading = false,
                                mappingError = "On-Device-Mapping haengt. Bitte erneut versuchen oder auf Cloud wechseln.",
                            )
                        }
                    }
                }
            }

            val result = try {
                if (mode == FormAutomationMode.ON_DEVICE) {
                    supervisorScope {
                        val mappingJob = async(Dispatchers.Default) {
                            MapTranscriptToFormUseCase(activeRepository()).invoke(response)
                        }
                        val awaited = withTimeoutOrNull(ON_DEVICE_MAPPING_TIMEOUT_MS) {
                            mappingJob.await()
                        }
                        if (awaited == null) {
                            appendLog("On-Device-Mapping Watchdog: Job nach ${ON_DEVICE_MAPPING_TIMEOUT_MS / 1000}s abgebrochen")
                            mappingJob.cancel()
                            AppResult.Error(
                                "On-Device-Mapping hat zu lange gedauert. Bitte erneut versuchen oder auf Cloud wechseln."
                            )
                        } else {
                            awaited
                        }
                    }
                } else {
                    withContext(Dispatchers.Default) {
                        MapTranscriptToFormUseCase(activeRepository()).invoke(response)
                    }
                }
            } catch (_: TimeoutCancellationException) {
                appendLog("On-Device-Mapping Timeout nach ${ON_DEVICE_MAPPING_TIMEOUT_MS / 1000}s")
                AppResult.Error(
                    "On-Device-Mapping hat zu lange gedauert. Bitte erneut versuchen oder auf Cloud wechseln."
                )
            } catch (t: Throwable) {
                appendLog("On-Device-Mapping unerwarteter Fehler: ${t.message ?: "Unbekannt"}")
                AppResult.Error(
                    "On-Device-Mapping ist fehlgeschlagen. Bitte erneut versuchen oder auf Cloud wechseln.",
                    t,
                )
            } finally {
                heartbeatJob?.cancel()
                safetyBrakeJob?.cancel()
            }

            if (runId != mappingRunId) {
                appendLog("Mapping-Run verworfen (neuere Anfrage aktiv)")
                return@launch
            }

            val durationMs = startedAt.elapsedNow().inWholeMilliseconds

            when (result) {
                is AppResult.Success -> {
                    val mappingResult = result.data
                    println("DEBUG FormViewModel: Mapping erfolgreich, fieldAnswers=${mappingResult.fieldAnswers}")
                    mappingResult.processLog?.let { appendLog("Mapping-Pfad: $it") }
                    mappingResult.processDetails.takeLast(8).forEach { detail ->
                        appendLog("Mapping-Detail: $detail")
                    }
                    appendLog("Transkript-Debug: speakerBlocks=${mappingResult.speakerBlocks.size}")
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
                            lastMappingProcess = mappingResult.processLog ?: current.lastMappingProcess,
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
            onDeviceConfigurableRepository?.setOrthographyCorrectionEnabled(
                _uiState.value.onDeviceOrthographyCorrectionEnabled
            )
        }

        _uiState.update { it.copy(automationMode = mode, mappingError = null) }
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
        _uiState.update { it.copy(mappingError = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(submitError = null) }
    }

    fun setOnDeviceOrthographyCorrectionEnabled(enabled: Boolean) {
        _uiState.update {
            it.copy(
                onDeviceOrthographyCorrectionEnabled = enabled,
                mappingError = null,
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
        mappingRunId++
        _uiState.update { current ->
            current.copy(
                entries = current.entries.map { entry ->
                    entry.copy(answer = entry.answer.copy(value = ""))
                },
                speakerBlocks = emptyList(),
                isMappingLoading = false,
                mappingError = null,
                lastMappingProcess = null,
                mappingLogs = emptyList(),
                automationMode = FormAutomationMode.CLOUD,
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
}
