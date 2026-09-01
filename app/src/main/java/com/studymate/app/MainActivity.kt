package com.studymate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.studymate.app.ui.StudyMateApp

/**
 * Single-activity host for StudyMate. Fully Jetpack Compose with edge-to-edge styling.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
        } catch (_: Exception) {
            // Edge-to-edge is cosmetic; safe fallback on legacy themes
        }
        try {
            setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides this) {
                    StudyMateApp()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("StudyMate/MainActivity", "Compose content failed", e)
            showError(e)
        }
    }

    private fun showError(e: Throwable) {
        try {
            setContent { ErrorScreen(e) }
        } catch (_: Exception) {
            // Safe fallback
        }
    }

    @Composable
    private fun ErrorScreen(e: Throwable) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Application Error",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = e.message ?: "Unknown startup failure",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
