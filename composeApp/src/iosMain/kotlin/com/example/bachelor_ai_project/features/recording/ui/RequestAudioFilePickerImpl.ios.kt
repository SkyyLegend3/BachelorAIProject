package com.example.bachelor_ai_project.features.recording.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.UniformTypeIdentifiers.UTTypeAudio
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/**
 * iOS-spezifisches Composable für Audio-Datei-Picker.
 * Öffnet einen nativen UIDocumentPicker und gibt die ausgewählte Datei als URL-String zurück.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun RequestAudioFilePickerImpl(
    shouldPickFile: Boolean,
    onFileSelected: (filePath: String) -> Unit,
    onError: (errorMessage: String) -> Unit,
) {
    // Delegate muss stark referenziert bleiben, solange der Picker angezeigt wird.
    val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(
            controller: UIDocumentPickerViewController,
            didPickDocumentsAtURLs: List<*>,
        ) {
            val selectedUrl = didPickDocumentsAtURLs.firstOrNull() as? NSURL
            // Fuer die Verarbeitung auf iOS brauchen wir einen lokalen Dateipfad,
            // keinen URL-String mit file:// und escaped Zeichen.
            val resolved = selectedUrl?.path ?: selectedUrl?.absoluteString
            if (resolved.isNullOrBlank()) {
                onError("Keine Datei ausgewaehlt")
            } else {
                onFileSelected(resolved)
            }
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            onError("Dateiauswahl abgebrochen")
        }
    }

    LaunchedEffect(shouldPickFile) {
        if (shouldPickFile) {
            try {
                val picker = UIDocumentPickerViewController(
                    forOpeningContentTypes = listOf(UTTypeAudio),
                    asCopy = true,
                )
                picker.delegate = delegate
                picker.allowsMultipleSelection = false

                val app = UIApplication.sharedApplication
                val root = app.keyWindow?.rootViewController
                val presenter = topMostViewController(root)

                if (presenter == null) {
                    onError("Datei-Picker konnte nicht geoeffnet werden")
                } else {
                    presenter.presentViewController(picker, animated = true, completion = null)
                }
            } catch (e: Exception) {
                onError("Fehler beim Öffnen des Datei-Pickers: ${e.message}")
            }
        }
    }
}

private fun topMostViewController(root: UIViewController?): UIViewController? {
    var current = root
    while (current?.presentedViewController != null) {
        current = current.presentedViewController
    }
    return current
}

