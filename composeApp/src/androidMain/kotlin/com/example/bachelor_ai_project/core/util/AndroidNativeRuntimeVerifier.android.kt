package com.example.bachelor_ai_project.core.util

import android.os.Build

/**
 * Verifiziert, dass lokale On-Device-Pfade auf einem echten Android-Device
 * und mit nativen (nicht-Stub) Bibliotheken laufen.
 */
object AndroidNativeRuntimeVerifier {

    fun ensureRealRuntime(
        componentTag: String,
        runtimeClassName: String,
    ): Boolean {
        if (isLikelyEmulator()) {
            println("DEBUG $componentTag: Emulator erkannt, On-Device-Pfad deaktiviert")
            return false
        }

        val runtimeClass = runCatching { Class.forName(runtimeClassName) }
            .onFailure { error ->
                println("DEBUG $componentTag: Runtime-Klasse nicht gefunden ($runtimeClassName): ${error.message}")
            }
            .getOrNull() ?: return false

        val codeSource = runCatching {
            runtimeClass.protectionDomain?.codeSource?.location?.toString().orEmpty()
        }.getOrDefault("")

        if (codeSource.contains("stub", ignoreCase = true)) {
            println("DEBUG $componentTag: Stub-Bibliothek erkannt via CodeSource=$codeSource")
            return false
        }

        val isStub = runCatching {
            runtimeClass.getDeclaredField("IS_STUB").getBoolean(null)
        }.getOrElse { error ->
            if (error is NoSuchFieldException) {
                println("DEBUG $componentTag: Kein IS_STUB-Feld, native Runtime wird angenommen")
            } else {
                println("DEBUG $componentTag: IS_STUB-Check fehlgeschlagen: ${error.message}")
            }
            false
        }

        if (isStub) {
            println("DEBUG $componentTag: IS_STUB=true, On-Device-Pfad deaktiviert")
            return false
        }

        return true
    }

    private fun isLikelyEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.orEmpty().lowercase()
        val model = Build.MODEL.orEmpty().lowercase()
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        val device = Build.DEVICE.orEmpty().lowercase()
        val product = Build.PRODUCT.orEmpty().lowercase()

        return fingerprint.contains("generic") ||
            fingerprint.contains("emulator") ||
            model.contains("sdk") ||
            model.contains("emulator") ||
            manufacturer.contains("genymotion") ||
            brand.startsWith("generic") ||
            device.startsWith("generic") ||
            product.contains("sdk")
    }
}

