package com.example.bachelor_ai_project.features.recording.domain

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioQualityHigh
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSURL

class IosAudioRecorder : AudioRecorder {

    private var recorder: AVAudioRecorder? = null

    @OptIn(ExperimentalForeignApi::class)
    override fun start(filePath: String) {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryRecord, error = null)
        session.setActive(true, error = null)

        val url = NSURL.fileURLWithPath(filePath)
        val settings: Map<Any?, Any?> = mapOf(
            AVFormatIDKey to kAudioFormatMPEG4AAC,
            AVSampleRateKey to 44100.0,
            AVNumberOfChannelsKey to 1,
            AVEncoderAudioQualityKey to AVAudioQualityHigh,
        )

        recorder = AVAudioRecorder(uRL = url, settings = settings, error = null)
        recorder?.prepareToRecord()
        recorder?.record()
    }

    override fun stop() {
        recorder?.stop()
        recorder = null
    }
}

class IosAudioRecorderFactory : AudioRecorderFactory {
    override fun create(): AudioRecorder = IosAudioRecorder()
}

actual fun createAudioRecorderFactory(): AudioRecorderFactory = IosAudioRecorderFactory()

