package com.example.bachelor_ai_project.features.transcription.domain

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile

@OptIn(ExperimentalForeignApi::class)
actual fun readFileBytes(path: String): ByteArray {
    val data = NSData.dataWithContentsOfFile(path)
        ?: error("Datei konnte nicht gelesen werden: $path")
    return data.bytes!!.readBytes(data.length.toInt())
}

