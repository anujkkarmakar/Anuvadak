package com.example.anuvadak.ui.gesture

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.anuvadak.ui.theme.AnuvadakTheme
import kotlinx.coroutines.delay
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GestureRecognitionViewModelFactory(private val context: android.content.Context) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GestureRecognitionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GestureRecognitionViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Composable
fun GestureRecognitionScreen(
    viewModel: GestureRecognitionViewModel = viewModel(
        factory = GestureRecognitionViewModelFactory(LocalContext.current)
    )
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var showPermissionRationale by remember { mutableStateOf(false) }
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    var previewView: PreviewView? by remember { mutableStateOf(null) }
    var showGestureList by remember { mutableStateOf(false) }
    var isCameraReady by remember { mutableStateOf(false) }

    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            showPermissionRationale = true
        }
    }

    // Auto-start detection when system is ready
    LaunchedEffect(state.isInitialized, hasCameraPermission, isCameraReady) {
        Log.d("GestureDebug", "LaunchedEffect triggered - initialized:${state.isInitialized}, camera:$hasCameraPermission, ready:$isCameraReady, detecting:${state.isDetecting}")

        if (state.isInitialized && hasCameraPermission && isCameraReady && !state.isDetecting) {
            Log.d("GestureDebug", "All conditions met, starting detection in 1 second")
            delay(1000) // Give camera time to stabilize
            viewModel.startDetection()
        }
    }

    // Monitor state changes for debugging
    LaunchedEffect(state.isDetecting) {
        Log.d("GestureDebug", "Detection state changed: ${state.isDetecting}")
    }

    // Permission rationale dialog
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("Camera Permission Required") },
            text = {
                Text("Camera permission is required for gesture recognition. Please grant permission to continue.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionRationale = false
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Gesture list dialog
    if (showGestureList) {
        GestureListDialog(
            onDismiss = { showGestureList = false },
            detectionHistory = state.detectionHistory
        )
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header with title and controls
        GestureHeader(
            onShowGestureList = { showGestureList = true },
            onClearHistory = { viewModel.clearHistory() },
            isDetecting = state.isDetecting,
            onToggleDetection = {
                if (state.isDetecting) {
                    viewModel.stopDetection()
                } else {
                    viewModel.startDetection()
                }
            },
            state = state
        )

        when {
            !hasCameraPermission -> {
                // Permission request UI
                CameraPermissionContent(
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
            }

            !state.isInitialized -> {
                // Initialization loading
                InitializationContent()
            }

            state.error != null -> {
                // Error state
                ErrorContent(
                    error = state.error!!,
                    onRetry = {
                        viewModel.clearError()
                    }
                )
            }

            else -> {
                // Main camera and gesture recognition UI
                Box(modifier = Modifier.weight(1f)) {
                    // Camera preview
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onPreviewReady = { preview ->
                            previewView = preview
                            startCamera(
                                context = context,
                                lifecycleOwner = lifecycleOwner,
                                previewView = preview,
                                cameraExecutor = cameraExecutor,
                                onImageAnalysis = { imageProxy, timestamp ->
                                    Log.d("GestureDebug", "Frame received for analysis")
                                    viewModel.processImageProxy(imageProxy, timestamp)
                                },
                                onCameraReady = {
                                    Log.d("GestureDebug", "Camera is ready")
                                    isCameraReady = true
                                }
                            )
                        }
                    )

                    // Gesture overlay
                    GestureOverlay(
                        state = state,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Bottom status panel
                GestureStatusPanel(
                    state = state,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
}

@Composable
private fun GestureHeader(
    onShowGestureList: () -> Unit,
    onClearHistory: () -> Unit,
    isDetecting: Boolean,
    onToggleDetection: () -> Unit,
    state: GestureState
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🤟 Gesture Recognition",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isDetecting) "Detecting gestures..." else "Show hand gestures to camera",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Row {
                    IconButton(onClick = onShowGestureList) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = "Show Gestures",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onClearHistory) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear History",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }

                    IconButton(onClick = onToggleDetection) {
                        Icon(
                            if (isDetecting) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isDetecting) "Pause Detection" else "Start Detection",
                            tint = if (isDetecting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Debug info
            if (state.frameCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Frames: ${state.frameCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Uploads: ${state.totalUploadedImages}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionContent(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Camera,
            contentDescription = "Camera",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Camera Permission Required",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "To use gesture recognition, we need access to your camera to analyze hand movements in real-time.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                Icons.Default.Camera,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text("Grant Camera Permission")
        }
    }
}

@Composable
private fun InitializationContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Initializing Gesture Recognition...",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Please wait while we set up the hand detection model.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ErrorContent(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = "Error",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Gesture Recognition Error",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text("Retry")
        }
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    onPreviewReady: (PreviewView) -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                Log.d("GestureDebug", "PreviewView created")
                onPreviewReady(this)
            }
        }
    )
}

@Composable
private fun GestureOverlay(
    state: GestureState,
    modifier: Modifier = Modifier
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (state.confidence > 0.5f) 1.1f else 1.0f,
        animationSpec = tween(300),
        label = "gesture_scale"
    )

    Canvas(modifier = modifier) {
        // Draw hand landmarks if available
        if (state.landmarks.isNotEmpty()) {
            drawLandmarks(state.landmarks, state.confidence)
        }
    }

    // Gesture display overlay
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter
    ) {
        if (state.currentGesture != "Unknown" &&
            state.currentGesture != "No Hand Detected" &&
            state.currentGesture != "Ready") {
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .scale(animatedScale),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.confidence > 0.5f)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.currentGesture.replace("_", " "),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (state.confidence > 0.5f)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "${(state.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.confidence > 0.5f)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawLandmarks(landmarks: List<LandmarkPoint>, confidence: Float) {
    val landmarkColor = if (confidence > 0.5f) Color.Green else Color.Yellow
    val connectionColor = if (confidence > 0.5f) Color.Green.copy(alpha = 0.6f) else Color.Yellow.copy(alpha = 0.6f)

    // Draw landmark points
    landmarks.forEach { landmark ->
        drawCircle(
            color = landmarkColor,
            radius = 8.dp.toPx(),
            center = Offset(
                landmark.x * size.width,
                landmark.y * size.height
            )
        )
    }

    // Draw connections between landmarks (simplified hand skeleton)
    if (landmarks.size >= 21) {
        drawHandConnections(landmarks, connectionColor)
    }
}

private fun DrawScope.drawHandConnections(landmarks: List<LandmarkPoint>, color: Color) {
    val connections = listOf(
        // Thumb
        listOf(0, 1, 2, 3, 4),
        // Index finger
        listOf(0, 5, 6, 7, 8),
        // Middle finger
        listOf(0, 9, 10, 11, 12),
        // Ring finger
        listOf(0, 13, 14, 15, 16),
        // Pinky
        listOf(0, 17, 18, 19, 20),
        // Palm connections
        listOf(0, 5, 9, 13, 17, 0)
    )

    connections.forEach { connection ->
        for (i in 0 until connection.size - 1) {
            if (connection[i] < landmarks.size && connection[i + 1] < landmarks.size) {
                val start = landmarks[connection[i]]
                val end = landmarks[connection[i + 1]]

                drawLine(
                    color = color,
                    start = Offset(start.x * size.width, start.y * size.height),
                    end = Offset(end.x * size.width, end.y * size.height),
                    strokeWidth = 4.dp.toPx()
                )
            }
        }
    }
}

@Composable
private fun GestureStatusPanel(
    state: GestureState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusItem(
                    label = "Current",
                    value = state.currentGesture.replace("_", " "),
                    color = MaterialTheme.colorScheme.primary
                )

                StatusItem(
                    label = "Confidence",
                    value = "${(state.confidence * 100).toInt()}%",
                    color = if (state.confidence > 0.5f)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.secondary
                )

                StatusItem(
                    label = "Detections",
                    value = state.totalDetections.toString(),
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (state.stableGesture.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = "Last Stable: ${state.stableGesture.replace("_", " ")}",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusItem(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun GestureListDialog(
    onDismiss: () -> Unit,
    detectionHistory: List<String>
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Supported Gestures & History")
        },
        text = {
            LazyColumn {
                item {
                    Text(
                        text = "Supported Gestures:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(listOf(
                    "🖐️ Open Palm",
                    "✊ Fist",
                    "👍 Thumbs Up",
                    "✌️ Peace Sign",
                    "👉 Pointing",
                    "🤘 Rock On",
                    "🤙 Call Me",
                    "👌 OK Sign"
                )) { gesture ->
                    Text(
                        text = gesture,
                        modifier = Modifier.padding(vertical = 2.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (detectionHistory.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Recent Detections:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(detectionHistory.takeLast(10).reversed()) { detection ->
                        Text(
                            text = "• ${detection.replace("_", " ")}",
                            modifier = Modifier.padding(vertical = 2.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private fun startCamera(
    context: android.content.Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    cameraExecutor: ExecutorService,
    onImageAnalysis: (ImageProxy, Long) -> Unit,
    onCameraReady: () -> Unit
) {
    Log.d("GestureDebug", "Starting camera setup")
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    cameraProviderFuture.addListener({
        try {
            val cameraProvider = cameraProviderFuture.get()
            Log.d("GestureDebug", "Camera provider obtained")

            // Create Preview use case
            val preview = androidx.camera.core.Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // Create ImageAnalysis use case
            // In your startCamera function, update the ImageAnalysis configuration:
            val imageAnalyzer = androidx.camera.core.ImageAnalysis.Builder()
                .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(android.util.Size(640, 480))
                .setOutputImageFormat(androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888) // Specify format
                .build()
                .also { analyzer ->
                    analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                        Log.d("GestureDebug", "ImageAnalyzer callback triggered - Format: ${imageProxy.format}")
                        onImageAnalysis(imageProxy, System.currentTimeMillis())
                    }
                }
            // Select front camera as default (better for gesture recognition)
            val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )

                Log.d("GestureDebug", "Camera bound successfully")
                onCameraReady()

            } catch (exc: Exception) {
                Log.e("GestureDebug", "Use case binding failed", exc)
            }

        } catch (exc: Exception) {
            Log.e("GestureDebug", "Camera provider initialization failed", exc)
        }

    }, ContextCompat.getMainExecutor(context))
}

@Preview(showBackground = true, name = "Gesture Recognition Preview")
@Preview(
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
    name = "Gesture Recognition Preview - Dark"
)
@Composable
fun GestureRecognitionScreenPreview() {
    AnuvadakTheme {
        Surface {
            // Preview placeholder
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "🤟 Gesture Recognition",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera preview and gesture detection will appear here",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}