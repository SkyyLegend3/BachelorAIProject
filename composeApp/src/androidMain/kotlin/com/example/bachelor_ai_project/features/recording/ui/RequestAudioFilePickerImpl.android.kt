package com.example.bachelor_ai_project.features.recording.ui

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer

/**
 * Android-spezifisches Composable für Audio-Datei-Picker mit ActivityResult-API.
 * Nutzt den nativen Android OpenDocument-Intent für Audio-Dateien.
 */
@Composable
actual fun RequestAudioFilePickerImpl(
    shouldPickFile: Boolean,
    onFileSelected: (filePath: String) -> Unit,
    onError: (errorMessage: String) -> Unit,
) {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val localPath = copyUriToCacheFile(context, uri)
                val preparedPath = prepareForCloudUploadIfNeeded(context, localPath)
                onFileSelected(preparedPath)
            } catch (e: Exception) {
                onError("Fehler beim Zugriff auf Datei: ${e.message}")
            }
        } else {
            onError("Dateiauswahl abgebrochen")
        }
    }

    LaunchedEffect(shouldPickFile) {
        if (shouldPickFile) {
            try {
                launcher.launch(arrayOf("audio/*"))
            } catch (e: Exception) {
                onError("Fehler beim Öffnen des Datei-Pickers: ${e.message}")
            }
        }
    }
}

private fun copyUriToCacheFile(context: Context, uri: Uri): String {
    val resolver = context.contentResolver

    val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    val extensionFromName = displayName
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() }

    val extensionFromMime = resolver.getType(uri)
        ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        ?.takeIf { it.isNotBlank() }

    val extension = extensionFromName ?: extensionFromMime ?: "m4a"
    val targetName = "upload_${System.currentTimeMillis()}.$extension"
    val targetFile = File(context.cacheDir, targetName)

    resolver.openInputStream(uri)?.use { input ->
        targetFile.outputStream().use { output -> input.copyTo(output) }
    } ?: error("Konnte InputStream fuer Datei nicht oeffnen")

    return targetFile.absolutePath
}

private fun prepareForCloudUploadIfNeeded(context: Context, localPath: String): String {
    val inputFile = File(localPath)
    if (!inputFile.exists()) return localPath

    if (!hasFtyp3gp4(inputFile)) {
        return localPath
    }

    val remuxed = File(context.cacheDir, "upload_${System.currentTimeMillis()}_remux.m4a")
    val success = remuxAudioTrackToMp4(inputFile, remuxed)
    if (!success) return localPath

    return remuxed.absolutePath
}

private fun hasFtyp3gp4(file: File): Boolean {
    if (!file.exists() || file.length() < 12) return false

    val header = ByteArray(12)
    FileInputStream(file).use { input ->
        val read = input.read(header)
        if (read < 12) return false
    }

    val hasFtyp = header[4].toInt().toChar() == 'f' &&
        header[5].toInt().toChar() == 't' &&
        header[6].toInt().toChar() == 'y' &&
        header[7].toInt().toChar() == 'p'
    if (!hasFtyp) return false

    val brand = charArrayOf(
        header[8].toInt().toChar(),
        header[9].toInt().toChar(),
        header[10].toInt().toChar(),
        header[11].toInt().toChar(),
    ).concatToString().lowercase()

    return brand == "3gp4"
}

private fun remuxAudioTrackToMp4(input: File, output: File): Boolean {
    val extractor = MediaExtractor()
    var muxer: MediaMuxer? = null

    return try {
        extractor.setDataSource(input.absolutePath)

        var audioTrackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                break
            }
        }
        if (audioTrackIndex == -1) return false

        extractor.selectTrack(audioTrackIndex)
        val inputFormat = extractor.getTrackFormat(audioTrackIndex)

        muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val muxerTrack = muxer.addTrack(inputFormat)
        muxer.start()

        val bufferInfo = MediaCodec.BufferInfo()
        val buffer = ByteBuffer.allocate(256 * 1024)

        while (true) {
            buffer.clear()
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = extractor.sampleTime
            bufferInfo.flags = extractor.sampleFlags

            muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
            extractor.advance()
        }

        true
    } catch (_: Exception) {
        false
    } finally {
        runCatching { extractor.release() }
        runCatching {
            muxer?.stop()
            muxer?.release()
        }
    }
}


