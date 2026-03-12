package com.example.bachelor_ai_project.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Stellt eine einzige, konfigurierte [HttpClient]-Instanz bereit.
 * Wird als Singleton übergeben, damit nicht mehrere Clients geöffnet werden.
 */
object HttpClientProvider {

    fun create(): HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
            )
        }
    }
}

