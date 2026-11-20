package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.data.configureAppCheck
import com.example.ui.ATutorRootApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure Firebase App Check before any Firebase AI or Compose content initializes
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            ATutorRootApp()
        }
    }
}
