package com.example.bachelor_ai_project.core.util

import com.example.bachelor_ai_project.features.recording.domain.AppContextHolder

class AndroidPlatformFileProvider : PlatformFileProvider {
    override fun recordingFilePath(): String {
        return AppContextHolder.applicationContext
            .cacheDir
            .resolve("recording.m4a")
            .absolutePath
    }
}

actual fun createPlatformFileProvider(): PlatformFileProvider = AndroidPlatformFileProvider()

