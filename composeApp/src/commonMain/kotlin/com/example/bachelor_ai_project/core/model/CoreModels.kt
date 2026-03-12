package com.example.bachelor_ai_project.core.model

/**
 * Gemeinsame Basis-Modelle, die feature-übergreifend genutzt werden können.
 *
 * Feature-spezifische Modelle verbleiben in ihrem jeweiligen
 * `features/<name>/domain`-Package.
 */

/**
 * Repräsentiert einen Zeitbereich in Sekunden.
 *
 * @param startSeconds  Startzeitpunkt in Sekunden.
 * @param endSeconds    Endzeitpunkt in Sekunden.
 */
data class TimeRange(
    val startSeconds: Double,
    val endSeconds: Double,
) {
    val durationSeconds: Double get() = endSeconds - startSeconds
}

