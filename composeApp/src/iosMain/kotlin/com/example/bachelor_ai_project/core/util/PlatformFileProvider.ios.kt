package com.example.bachelor_ai_project.core.util

import platform.Foundation.NSTemporaryDirectory

class IosPlatformFileProvider : PlatformFileProvider {
    override fun recordingFilePath(): String = NSTemporaryDirectory() + "recording.m4a"
}

actual fun createPlatformFileProvider(): PlatformFileProvider = IosPlatformFileProvider()

