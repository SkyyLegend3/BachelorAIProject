package com.example.bachelor_ai_project.core.result

/**
 * Generisches Ergebnis-Wrapper für alle Use-Cases und Repository-Operationen.
 * Ersetzt bare Exception-Propagation und macht Fehlerbehandlung explizit.
 */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : AppResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
}

/** Führt [block] aus und wrapped das Ergebnis sicher in [AppResult]. */
inline fun <T> runCatchingResult(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    // CancellationException nie verschlucken – Coroutine-Mechanismus muss funktionieren
    throw e
} catch (e: Throwable) {
    AppResult.Error(e.message ?: "Unbekannter Fehler", e)
}

