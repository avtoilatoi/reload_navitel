package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.data.AppSettings
import com.example.service.NetworkMonitorService
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val settings = AppSettings.getInstance(this)
    if (settings.serviceEnabled) {
      NetworkMonitorService.start(this)
    }

    setContent {
      MyApplicationTheme {
        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
          ) { /* callback handled */ }

          LaunchedEffect(Unit) {
            val isGranted = ContextCompat.checkSelfPermission(
              this@MainActivity,
              Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
              permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
          }
        }

        Surface(modifier = Modifier.fillMaxSize()) {
          MainScreen()
        }
      }
    }
  }
}
