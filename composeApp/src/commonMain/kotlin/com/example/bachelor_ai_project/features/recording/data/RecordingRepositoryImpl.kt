package com.example.bachelor_ai_project.features.recording.data

import com.example.bachelor_ai_project.core.util.PlatformFileProvider
import com.example.bachelor_ai_project.features.recording.domain.AudioRecorderFactory
import com.example.bachelor_ai_project.features.recording.domain.RecordingRepository

/**
 * Konkrete Implementierung von [RecordingRepository].
 * Delegiert Recorder-Aufrufe an die plattformspezifische [AudioRecorderFactory]
 * und Pfad-Ermittlung an [PlatformFileProvider].
 */
class RecordingRepositoryImpl(
    private val recorderFactory: AudioRecorderFactory,
    private val fileProvider: PlatformFileProvider,
) : RecordingRepository {

    private val recorder by lazy { recorderFactory.create() }
    private var currentPath: String = ""

    override fun startRecording(): String {
        currentPath = fileProvider.recordingFilePath()
        recorder.start(currentPath)
        return currentPath
    }

    override fun stopRecording(): String {
        recorder.stop()
        return currentPath
    }
}

