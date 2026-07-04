package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.MainAppContainer
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MusicViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                // Main Container Surface
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: MusicViewModel = viewModel()
                    
                    // Unified Permission Request launcher for local device files
                    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_AUDIO
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        if (isGranted) {
                            Toast.makeText(this, "Storage permission granted! Scanning local audio.", Toast.LENGTH_SHORT).show()
                            viewModel.onSearchQueryChanged("") // Refreshes catalog with local files
                        } else {
                            Toast.makeText(this, "Storage permission denied. Showing cloud-only tracks.", Toast.LENGTH_LONG).show()
                        }
                    }

                    LaunchedEffect(Unit) {
                        // Check and launch permission dynamically at start
                        val checkPermission = ContextCompat.checkSelfPermission(this@MainActivity, permissionToRequest)
                        if (checkPermission != PackageManager.PERMISSION_GRANTED) {
                            permissionLauncher.launch(permissionToRequest)
                        }
                    }

                    MainAppContainer(viewModel = viewModel)
                }
            }
        }
    }
}
