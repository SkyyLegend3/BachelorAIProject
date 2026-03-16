package com.example.bachelor_ai_project.app

import androidx.lifecycle.ViewModel
import com.example.bachelor_ai_project.core.util.PlatformFileProvider
import com.example.bachelor_ai_project.features.form.data.AiFormMappingRepository
import com.example.bachelor_ai_project.features.form.data.DefaultFormDefinitionProvider
import com.example.bachelor_ai_project.features.form.data.createOnDeviceFormMappingRepository
import com.example.bachelor_ai_project.features.form.presentation.FormViewModel
import com.example.bachelor_ai_project.features.recording.data.RecordingRepositoryImpl
import com.example.bachelor_ai_project.features.recording.domain.AudioRecorderFactory
import com.example.bachelor_ai_project.features.recording.presentation.RecordingViewModel
import com.example.bachelor_ai_project.features.transcription.data.createOnDeviceTranscriptionRepository
import com.example.bachelor_ai_project.features.transcription.data.OpenAiTranscriptionRepository
import com.example.bachelor_ai_project.features.transcription.presentation.TranscriptionViewModel
import io.ktor.client.HttpClient

/**
 * Koordiniert die Feature-ViewModels.
 *
 * Ablauf:
 * 1. Aufnahme fertig → [RecordingViewModel.onRecordingFinished]
 * 2. Transkription starten → [TranscriptionViewModel.transcribe]
 * 3. Transkriptions-Ergebnis → [FormViewModel.applyTranscript]
 *    → GPT-4o-mini ordnet Transkript-Inhalte den Formularfeldern zu
 */
class AppViewModel(
    recorderFactory: AudioRecorderFactory,
    platformFileProvider: PlatformFileProvider,
    httpClient: HttpClient,
    openAiApiKey: String,
) : ViewModel() {

    private val formDefinitionProvider = DefaultFormDefinitionProvider()
    private val onDeviceTranscriptionRepository = createOnDeviceTranscriptionRepository()
    private val onDeviceFormRepository = if (onDeviceTranscriptionRepository != null) {
        createOnDeviceFormMappingRepository(definitionProvider = formDefinitionProvider)
    } else {
        null
    }

    val transcriptionViewModel = TranscriptionViewModel(
        cloudTranscriptionRepository = OpenAiTranscriptionRepository(
            apiKey = openAiApiKey,
            httpClient = httpClient,
        ),
        onDeviceTranscriptionRepository = onDeviceTranscriptionRepository,
    )

    val formViewModel = FormViewModel(
        definitionProvider = formDefinitionProvider,
        cloudMappingRepository = AiFormMappingRepository(
            apiKey = openAiApiKey,
            httpClient = httpClient,
            definitionProvider = formDefinitionProvider,
        ),
        onDeviceMappingRepository = onDeviceFormRepository,
    )

    val recordingViewModel = RecordingViewModel(
        recordingRepository = RecordingRepositoryImpl(
            recorderFactory = recorderFactory,
            fileProvider = platformFileProvider,
        )
    ).also { vm ->
        vm.onRecordingFinished = { filePath ->
            transcriptionViewModel.transcribe(filePath)
        }
    }

    init {
        formViewModel.onAutomationModeChanged = { mode ->
            transcriptionViewModel.setAutomationMode(mode)
        }

        transcriptionViewModel.onTranscriptionResult = { response ->
            formViewModel.applyTranscript(response)
        }
    }
}



