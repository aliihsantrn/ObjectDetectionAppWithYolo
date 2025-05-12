package com.example.objectdetectionwithyolo

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.objectdetectionwithyolo.presentation.screens.ObjectDetectionScreen
import com.example.objectdetectionwithyolo.presentation.screens.ResultScreen
import com.example.objectdetectionwithyolo.presentation.ui.theme.ObjectDetectionWithYoloTheme
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("Camera", "Camera permission granted")
        } else {
            Log.d("Camera", "Camera permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kamera izni kontrolü
        if (!hasCameraPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                0
            )

        }

        setContent {
            ObjectDetectionWithYoloTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "object_detection_screen"
                ) {

                    composable("object_detection_screen") {
                        ObjectDetectionScreen(navController = navController)
                    }

                    composable(
                        "result_screen/{resultList}",
                        arguments = listOf(navArgument("resultList") {
                            type = NavType.StringType
                        })
                    ) { backStackEntry ->
                        val json = backStackEntry.arguments?.getString("resultList") ?: "[]"
                        val addedClasses: List<String> = Json.decodeFromString(json)

                        ResultScreen(resultList = addedClasses)
                    }
                }
            }
        }
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}