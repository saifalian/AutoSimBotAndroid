package com.example.autosim.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.Intent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.autosim.overlay.ExecutionOverlayService

@Composable
fun OverlayControlsScreen() {
    val context = LocalContext.current
    val isServiceRunning by ExecutionOverlayService.isRunning.collectAsState()

    Scaffold(
        topBar = {
            Text(
                text = "Overlay Controls",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isServiceRunning) "Overlay is Running" else "Start Bot Overlay",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isServiceRunning) 
                    "Tap the button below to stop the floating overlay bubble."
                else
                    "Tap the button below to start the floating overlay bubble. The bubble will appear on top of other apps and allow you to control the automation sequence.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    if (isServiceRunning) {
                        val serviceIntent = Intent(context, ExecutionOverlayService::class.java)
                        context.stopService(serviceIntent)
                    } else {
                        val serviceIntent = Intent(context, ExecutionOverlayService::class.java)
                        ContextCompat.startForegroundService(context, serviceIntent)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isServiceRunning) "Stop Bot Overlay" else "Start Bot Overlay")
            }
        }
    }
}
