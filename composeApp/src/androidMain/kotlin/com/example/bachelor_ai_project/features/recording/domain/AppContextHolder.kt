package com.example.bachelor_ai_project.features.recording.domain

import android.content.Context

/**
 * Hält den Application-Context für Stellen, die außerhalb von Composables
 * auf den Android-Context angewiesen sind (z.B. Factory-Funktionen).
 * Wird in MainActivity.onCreate initialisiert.
 */
object AppContextHolder {
    lateinit var applicationContext: Context
        private set

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }
}


