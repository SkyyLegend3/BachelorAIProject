package com.example.bachelor_ai_project.features.transcription.data

import com.example.bachelor_ai_project.core.config.AppConfig
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionRepository
import com.example.bachelor_ai_project.features.transcription.data.OnDeviceWhisperTranscriptionRepository

/**
 * Swift-seitige Bridge fuer lokale whisper-Inferenz auf iOS.
 */
interface IosWhisperBridge {
    fun transcribe(
        modelPath: String,
        audioFilePath: String,
        language: String,
    ): String

    fun lastErrorMessage(): String
}

/**
 * Registry, damit Swift die Bridge beim App-Start registrieren kann.
 */
object IosWhisperBridgeRegistry {
    private var bridge: IosWhisperBridge? = null

    @Suppress("unused") // Wird aus Swift aufgerufen.
    fun register(bridge: IosWhisperBridge?) {
        this.bridge = bridge
    }

    fun current(): IosWhisperBridge? = bridge
}

/**
 * Lazy-Proxy: laedt die Swift-Bridge erst zur Laufzeit, damit die Repository-Erzeugung
 * nicht von der Initialisierungsreihenfolge (KMP vs. Swift App-Start) abhaengt.
 */
private object DeferredIosWhisperBridge : IosWhisperBridge {
    override fun transcribe(modelPath: String, audioFilePath: String, language: String): String {
        val activeBridge = IosWhisperBridgeRegistry.current()
        if (activeBridge == null) {
            println("DEBUG OnDeviceWhisperProvider(iOS): Swift-Whisper-Bridge nicht registriert (noch nicht verfuegbar).")
            return ""
        }

        return activeBridge.transcribe(
            modelPath = modelPath,
            audioFilePath = audioFilePath,
            language = language,
        )
    }

    override fun lastErrorMessage(): String {
        val activeBridge = IosWhisperBridgeRegistry.current() ?: return "Swift-Whisper-Bridge nicht registriert"
        return activeBridge.lastErrorMessage()
    }
}

fun createDefaultOnDeviceTranscriptionRepository(): TranscriptionRepository? {
    val modelPath = AppConfig.whisperModelPath.trim()
    if (modelPath.isBlank()) {
        println(
            "DEBUG OnDeviceWhisperProvider(iOS): WHISPER_MODEL_PATH ist leer, " +
                "Swift-Bridge versucht Bundle/Documents-Fallbacks."
        )
    }

    return OnDeviceWhisperTranscriptionRepository(
        modelPath = modelPath,
        bridge = DeferredIosWhisperBridge,
    )
}


