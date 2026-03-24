package com.example.bachelor_ai_project.features.transcription.domain

import android.net.Uri
import com.example.bachelor_ai_project.features.recording.domain.AppContextHolder
import java.io.File
import androidx.core.net.toUri

actual fun readFileBytes(path: String): ByteArray {
	val normalized = path.trim()

	// Dateiupload auf Android liefert oft content://-URIs statt lokaler Dateipfade.
	val uri = runCatching { normalized.toUri() }.getOrNull()
	if (uri != null && uri.scheme.equals("content", ignoreCase = true)) {
		val resolver = AppContextHolder.applicationContext.contentResolver
		resolver.openInputStream(uri)?.use { input ->
			return input.readBytes()
		}
		error("Konnte content-URI nicht lesen: $normalized")
	}

	return File(normalized).readBytes()
}

