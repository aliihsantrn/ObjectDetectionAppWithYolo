package com.example.objectdetectionwithyolo.presentation.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.objectdetectionwithyolo.data.BoundingBox
import com.example.objectdetectionwithyolo.detector.Detector
import com.example.objectdetectionwithyolo.presentation.widget.OverlayView
import com.example.objectdetectionwithyolo.util.Constants.LABELS_PATH
import com.example.objectdetectionwithyolo.util.Constants.MODEL_PATH
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectDetectionScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val overlayView = remember { OverlayView(context, null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val addedClasses = remember { mutableStateListOf<String>() }
    val rejectedClasses = remember { mutableStateListOf<String>() }
    val processedClasses = mutableSetOf<String>()
    var debounceJob: Job? = null

    val detector = remember {
        Detector(context, MODEL_PATH, LABELS_PATH, object : Detector.DetectorListener {
            override fun onEmptyDetect() {
                overlayView.invalidate()
            }

            override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
                overlayView.post {
                    overlayView.setResults(boundingBoxes)
                    overlayView.invalidate()

                    val detected = boundingBoxes.firstOrNull()
                    detected?.let {
                        val className = it.clsName
                        if (processedClasses.contains(className)) {
                            return@let
                        }

                        //Nesne daha önce eklenmediyse ekle
                        processedClasses.add(className)
                        debounceJob?.cancel()
                        debounceJob = coroutineScope.launch {
                            delay(300)

                            val result = snackbarHostState.showSnackbar(
                                "$className nesnesi eklensin mi ?",
                                actionLabel = "NESNEYİ EKLE",
                                duration = SnackbarDuration.Indefinite,
                                withDismissAction = true,
                            )

                            when (result) {
                                SnackbarResult.ActionPerformed -> {
                                    addedClasses.add(className)
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                }
                                SnackbarResult.Dismissed -> {
                                    rejectedClasses.add(className)
                                }
                            }
                            Log.e("Eklenenler", addedClasses.joinToString())
                            Log.e("Reddedilenler", rejectedClasses.joinToString())
                        }
                    }
                }
            }
        }).apply {
            setup()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text("Nesneleriniz Tanıtınız")
                },
                actions = {
                    IconButton(
                        onClick = {
                            val jsonList = Json.encodeToString(addedClasses.toList())
                            val encodedList = Uri.encode(jsonList)
                            navController.navigate("result_screen/$encodedList")
                        }) {
                        Icon(
                            imageVector = Icons.Filled.Done,
                            contentDescription = "Object Detection Completed Button"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->

        // Kamera izni işlemleri aynı kalıyor
        var hasPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            )
        }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasPermission = granted
        }

        LaunchedEffect(true) {
            if (!hasPermission) {
                launcher.launch(Manifest.permission.CAMERA)
            }
        }

        if (hasPermission) {
            AndroidView(factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setTargetRotation(previewView.display.rotation)
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetRotation(previewView.display.rotation)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor) { imageProxy ->
                                val bitmapBuffer =
                                    Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
                                imageProxy.use {
                                    bitmapBuffer.copyPixelsFromBuffer(it.planes[0].buffer)
                                }

                                val matrix = Matrix().apply {
                                    postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                                }

                                val rotatedBitmap = Bitmap.createBitmap(
                                    bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true
                                )

                                detector.detect(rotatedBitmap)
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalyzer
                        )
                    } catch (e: Exception) {
                        Log.e("CameraX", "Binding failed", e)
                    }

                }, ContextCompat.getMainExecutor(ctx))

                FrameLayout(ctx).apply {
                    addView(previewView)
                    addView(overlayView)
                }
            }, modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues))
        } else {
            Text(
                "Kamera izni gerekli",
                color = androidx.compose.ui.graphics.Color.Red,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}



/*
@Composable
fun ObjectDetectionScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val objectDetector = remember { YOLOObjectDetector(context) }
    var detections by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var previewSize by remember { mutableStateOf(Size(0, 0)) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            CameraPreview(
                context = context,
                lifecycleOwner = lifecycleOwner,
                analyzer = { proxy ->
                    val bitmap = proxy.toBitmap()
                    previewSize = Size(bitmap.width, bitmap.height)
                    val results = objectDetector.detect(bitmap)
                    detections = results
                    proxy.close()
                }
            )

            DetectionBoxes(
                detectionResults = detections,
                previewSize = previewSize,
                modifier = Modifier.fillMaxSize()
            )
        }

        Text(
            text = "Tespit edilen nesneler: ${detections.size}",
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            style = TextStyle(fontSize = 18.sp),
            textAlign = TextAlign.Center
        )
    }
}

fun ImageProxy.toBitmap(): Bitmap {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
    val imageBytes = out.toByteArray()

    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

 */