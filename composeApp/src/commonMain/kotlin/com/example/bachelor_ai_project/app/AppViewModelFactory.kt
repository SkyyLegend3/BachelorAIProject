package com.example.bachelor_ai_project.app

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.bachelor_ai_project.core.config.AppConfig
import com.example.bachelor_ai_project.core.network.HttpClientProvider
import com.example.bachelor_ai_project.core.util.createPlatformFileProvider
import com.example.bachelor_ai_project.features.recording.domain.createAudioRecorderFactory

/**
 * Factory für [AppViewModel].
 *
 * Ermöglicht die korrekte Instanziierung über [ViewModelProvider],
 * sodass der ViewModel den Screen-Rotation-Lifecycle auf Android überlebt.
 */
class AppViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(
        modelClass: kotlin.reflect.KClass<T>,
        extras: CreationExtras,
    ): T {
        return AppViewModel(
            recorderFactory = createAudioRecorderFactory(),
            platformFileProvider = createPlatformFileProvider(),
            httpClient = HttpClientProvider.create(),
            openAiApiKey = AppConfig.openAiApiKey,
        ) as T
    }
}

