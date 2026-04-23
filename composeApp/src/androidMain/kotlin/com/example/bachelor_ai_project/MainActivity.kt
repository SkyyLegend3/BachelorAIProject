package com.example.bachelor_ai_project

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.bachelor_ai_project.app.App
import com.example.bachelor_ai_project.core.util.ActivityResultCallbackHolder
import com.example.bachelor_ai_project.features.recording.domain.AppContextHolder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AppContextHolder.init(this, this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }

    @Deprecated("Nutze stattdessen registerForActivityResult mit ActivityResultContract")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        // Leite ActivityResult-Callbacks an den ActivityResultCallbackHolder weiter
        if (ActivityResultCallbackHolder.hasCallback(requestCode)) {
            ActivityResultCallbackHolder.handleActivityResult(requestCode, resultCode, data)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}