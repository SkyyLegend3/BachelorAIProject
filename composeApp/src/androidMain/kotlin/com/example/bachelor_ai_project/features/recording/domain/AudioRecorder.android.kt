package com.example.bachelor_ai_project.features.recording.domain

import android.content.Context
import android.media.MediaRecorder
import android.os.Build

class AndroidAudioRecorder(private val context: Context) : AudioRecorder {

    private var recorder: MediaRecorder? = null

    override fun start(filePath: String) {
        // MediaRecorder(context) erst ab API 31 verfügbar – Fallback für API 24–30
        @Suppress("DEPRECATION")
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(filePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            prepare()
            start()
        }
    }

    override fun stop() {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
    }
}

class AndroidAudioRecorderFactory(private val context: Context) : AudioRecorderFactory {
    override fun create(): AudioRecorder = AndroidAudioRecorder(context)
}

