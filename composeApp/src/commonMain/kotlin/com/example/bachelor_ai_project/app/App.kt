package com.example.bachelor_ai_project.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bachelor_ai_project.features.recording.ui.RecordingScreen
import com.example.bachelor_ai_project.features.form.ui.FormScreen

@Composable
fun App() {
    MaterialTheme {
        // viewModel() nutzt ViewModelProvider – der AppViewModel überlebt
        // Screen-Rotation auf Android korrekt.
        val appViewModel: AppViewModel = viewModel(factory = AppViewModelFactory())

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RecordingScreen(viewModel = appViewModel.recordingViewModel)

            Spacer(Modifier.height(16.dp))

            //TranscriptionScreen(viewModel = appViewModel.transcriptionViewModel)

            Spacer(Modifier.height(16.dp))

            FormScreen(
                viewModel = appViewModel.formViewModel,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}
