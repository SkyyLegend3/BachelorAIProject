package com.example.bachelor_ai_project.features.transcription.domain

import java.io.File

actual fun readFileBytes(path: String): ByteArray = File(path).readBytes()

