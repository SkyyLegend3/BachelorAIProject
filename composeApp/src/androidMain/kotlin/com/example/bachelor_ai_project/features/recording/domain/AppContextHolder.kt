package com.example.bachelor_ai_project.features.recording.domain

import android.app.Activity
import android.content.Context

/**
 * Hält den Application-Context und Activity-Context für Stellen, die außerhalb von Composables
 * auf den Android-Context angewiesen sind (z.B. Factory-Funktionen, File-Picker).
 * Wird in MainActivity.onCreate initialisiert.
 */
object AppContextHolder {
    lateinit var applicationContext: Context
        private set

    var activity: Activity? = null
        private set

    fun init(context: Context, activity: Activity? = null) {
        applicationContext = context.applicationContext
        this.activity = activity
    }
}


