package com.example.bachelor_ai_project.core.util

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import com.example.bachelor_ai_project.features.recording.domain.AppContextHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Android-Implementierung des Audio-Datei-Pickers (Fallback).
 * Die primäre Implementierung nutzt Composable mit ActivityResult.
 * Diese Klasse ist ein Fallback für Legacy-Code.
 */
class AndroidAudioFilePicker : AudioFilePicker {

    override suspend fun pickAudioFile(
        onFileSelected: (filePath: String) -> Unit,
        onError: (errorMessage: String) -> Unit,
    ) = withContext(Dispatchers.Main) {
        try {
            val activity = AppContextHolder.activity
            if (activity == null) {
                onError("Activity-Kontext nicht verfügbar")
                return@withContext
            }

            suspendCancellableCoroutine { continuation ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "audio/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }

                val requestCode = 2001
                val callback: (resultCode: Int, data: Intent?) -> Unit = { resultCode, data ->
                    if (resultCode == Activity.RESULT_OK && data != null) {
                        try {
                            val uri = data.data
                            if (uri != null) {
                                onFileSelected(uri.toString())
                            } else {
                                onError("Keine Datei ausgewählt")
                            }
                        } catch (e: Exception) {
                            onError("Fehler beim Zugriff auf Datei: ${e.message}")
                        }
                    } else {
                        onError("Dateiauswahl abgebrochen")
                    }
                    continuation.resume(Unit)
                }

                ActivityResultCallbackHolder.registerCallback(requestCode, callback)

                try {
                    activity.startActivityForResult(intent, requestCode)
                } catch (e: Exception) {
                    onError("Fehler beim Öffnen des Datei-Browsers: ${e.message}")
                    continuation.resume(Unit)
                }
            }
        } catch (e: Exception) {
            onError("Unerwarteter Fehler: ${e.message}")
        }
    }
}

/**
 * Globaler Callback-Holder für ActivityResult-Callbacks.
 * Wird von MainActivity.onActivityResult aufgerufen.
 */
object ActivityResultCallbackHolder {
    private val callbacks = mutableMapOf<Int, (resultCode: Int, data: Intent?) -> Unit>()

    fun registerCallback(requestCode: Int, callback: (resultCode: Int, data: Intent?) -> Unit) {
        callbacks[requestCode] = callback
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        callbacks[requestCode]?.invoke(resultCode, data)
        callbacks.remove(requestCode)
    }

    fun hasCallback(requestCode: Int): Boolean = requestCode in callbacks
}

actual fun createAudioFilePicker(): AudioFilePicker = AndroidAudioFilePicker()



