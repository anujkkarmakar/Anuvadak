package com.example.anuvadak.ui.gesture

import android.content.Context
import android.graphics.*
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker.HandLandmarkerOptions
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class GestureRecognitionViewModel(private val context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(GestureState())
    val uiState: StateFlow<GestureState> = _uiState.asStateFlow()

    private val firebaseStorage = FirebaseStorage.getInstance()
    private val firebaseAuth = FirebaseAuth.getInstance()

    private var handLandmarker: HandLandmarker? = null
    private var tfliteInterpreter: Interpreter? = null

    private val gestureLabels = mapOf(
        0 to "All_Done", 1 to "Yes", 2 to "No", 3 to "Help", 4 to "More",
        5 to "Thank_You", 6 to "Please", 7 to "Stop", 8 to "Hello",
        9 to "Deaf", 10 to "Love", 11 to "Enjoying", 12 to "Washroom",
        13 to "Boring", 14 to "I_Do_Not_Know", 15 to "I_Want_To_Talk"
    )

    private var lastPrediction = ""
    private var predictionCount = 0
    private val detectionHistory = mutableListOf<String>()

    private var lastCaptureTime = 0L
    private val captureIntervalMs = 3000L // Reduced frequency to 3 seconds
    private var captureCount = 0

    // Processing control
    private var isProcessing = false
    private var frameSkipCounter = 0
    private val frameSkipInterval = 3 // Process every 3rd frame

    init {
        initializeComponents()
    }

    private fun initializeComponents() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    currentGesture = "Initializing...",
                    confidence = 0f
                )

                withContext(Dispatchers.IO) {
                    initializeHandLandmarker()
                    initializeTensorFlowLite()
                }

                _uiState.value = _uiState.value.copy(
                    isInitialized = true,
                    currentGesture = "Ready",
                    error = null
                )

                Log.d("GestureRecognition", "Initialization completed successfully")

            } catch (e: Exception) {
                Log.e("GestureRecognition", "Initialization failed", e)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to initialize: ${e.localizedMessage}",
                    isInitialized = false,
                    currentGesture = "Error"
                )
            }
        }
    }

    private fun initializeHandLandmarker() {
        try {
            Log.d("GestureDebug", "Initializing HandLandmarker...")

            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()
            Log.d("GestureDebug", "BaseOptions created")

            val options = HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinHandDetectionConfidence(0.5f) // Lowered for testing
                .setMinHandPresenceConfidence(0.5f)  // Lowered for testing
                .setMinTrackingConfidence(0.3f)      // Lowered for testing
                .setNumHands(2)
                .setRunningMode(RunningMode.IMAGE)
                .build()
            Log.d("GestureDebug", "HandLandmarkerOptions created")

            handLandmarker = HandLandmarker.createFromOptions(context, options)
            Log.d("GestureDebug", "MediaPipe HandLandmarker initialized successfully")

        } catch (e: Exception) {
            Log.e("GestureDebug", "Failed to initialize HandLandmarker", e)
            _uiState.value = _uiState.value.copy(
                error = "HandLandmarker init failed: ${e.message}",
                isInitialized = false
            )
        }
    }

    private fun initializeTensorFlowLite() {
        try {
            val modelBuffer = loadModelFile("gesture_model.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(1) // Single thread for stability
                setUseNNAPI(false) // Disable NNAPI for compatibility
            }
            tfliteInterpreter = Interpreter(modelBuffer, options)
            Log.d("GestureRecognition", "TensorFlow Lite model loaded successfully")
        } catch (e: Exception) {
            Log.w("GestureRecognition", "TensorFlow Lite failed, using rule-based approach", e)
            tfliteInterpreter = null
        }
    }

    private fun loadModelFile(fileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(fileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun processImageProxy(imageProxy: ImageProxy, timestamp: Long) {
        if (!_uiState.value.isInitialized || !_uiState.value.isDetecting || isProcessing) {
            imageProxy.close()
            return
        }

        // Skip frames to reduce processing load
        frameSkipCounter++
        if (frameSkipCounter < frameSkipInterval) {
            imageProxy.close()
            return
        }
        frameSkipCounter = 0

        isProcessing = true

        viewModelScope.launch {
            try {
                val bitmap = convertImageProxyToBitmap(imageProxy)

                // Capture for Firebase (less frequent)
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastCaptureTime >= captureIntervalMs) {
                    lastCaptureTime = currentTime
                    captureAndUploadImage(bitmap, currentTime)
                }

                // Process gesture detection
                processGestureDetection(bitmap)

            } catch (e: Exception) {
                Log.e("GestureRecognition", "Error processing frame", e)
            } finally {
                imageProxy.close()
                isProcessing = false
            }
        }
    }

    private suspend fun processGestureDetection(bitmap: Bitmap) = withContext(Dispatchers.Default) {
        try {
            Log.d("GestureDebug", "Processing bitmap: ${bitmap.width}x${bitmap.height}")

            val mpImage = BitmapImageBuilder(bitmap).build()
            Log.d("GestureDebug", "MediaPipe image created successfully")

            val result = handLandmarker?.detect(mpImage)
            Log.d("GestureDebug", "HandLandmarker result: ${result != null}")

            if (result != null) {
                Log.d("GestureDebug", "Landmarks found: ${result.landmarks().size}")
                Log.d("GestureDebug", "Handedness: ${result.handedness().size}")

                if (result.landmarks().isNotEmpty()) {
                    val landmarks = result.landmarks()[0]
                    Log.d("GestureDebug", "First hand landmarks count: ${landmarks.size}")

                    val features = extractLandmarkFeatures(result)
                    Log.d("GestureDebug", "Features extracted: ${features.size}")

                    val (gesture, confidence) = classifyGesture(features, result)
                    Log.d("GestureDebug", "Classification: $gesture (${confidence})")

                    val landmarkPoints = convertLandmarksToPoints(result)

                    withContext(Dispatchers.Main) {
                        updateGestureState(gesture, confidence, landmarkPoints)
                        handleStability(gesture, confidence)
                    }
                } else {
                    Log.d("GestureDebug", "No landmarks detected")
                    withContext(Dispatchers.Main) {
                        updateGestureState("No Hand Detected", 0f, emptyList())
                    }
                }
            } else {
                Log.e("GestureDebug", "HandLandmarker returned null result")
                withContext(Dispatchers.Main) {
                    updateGestureState("Detection Failed", 0f, emptyList())
                }
            }
        } catch (e: Exception) {
            Log.e("GestureDebug", "Gesture detection error", e)
            withContext(Dispatchers.Main) {
                updateGestureState("Detection Error: ${e.message}", 0f, emptyList())
            }
        }
    }

    private fun extractLandmarkFeatures(result: HandLandmarkerResult): FloatArray {
        val features = mutableListOf<Float>()

        val landmarks = result.landmarks()

        // Process up to 2 hands
        for (handIndex in 0 until minOf(2, landmarks.size)) {
            val handLandmarks = landmarks[handIndex]
            val x = handLandmarks.map { it.x() }
            val y = handLandmarks.map { it.y() }
            val minX = x.minOrNull() ?: 0f
            val minY = y.minOrNull() ?: 0f

            // Normalize landmarks
            for (landmark in handLandmarks) {
                features.add(landmark.x() - minX)
                features.add(landmark.y() - minY)
            }
        }

        // Pad with zeros if needed
        when (landmarks.size) {
            1 -> repeat(42) { features.add(0f) }
            0 -> repeat(84) { features.add(0f) }
        }

        return features.toFloatArray()
    }

    private fun classifyGesture(features: FloatArray, result: HandLandmarkerResult): Pair<String, Float> {
        // Try TensorFlow Lite first
        if (tfliteInterpreter != null && features.size == 84) {
            try {
                val prediction = runTensorFlowInference(features)
                if (prediction.second > 0.6f) { // Higher confidence threshold
                    return prediction
                }
            } catch (e: Exception) {
                Log.w("GestureRecognition", "TensorFlow inference failed, using rules", e)
            }
        }

        // Fallback to enhanced rule-based classification
        return classifyWithEnhancedRules(result)
    }

    private fun runTensorFlowInference(features: FloatArray): Pair<String, Float> {
        val inputBuffer = ByteBuffer.allocateDirect(84 * 4).apply {
            order(ByteOrder.nativeOrder())
            features.forEach { putFloat(it) }
        }

        val outputBuffer = ByteBuffer.allocateDirect(16 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        tfliteInterpreter!!.run(inputBuffer, outputBuffer)

        outputBuffer.rewind()
        val outputs = FloatArray(16)
        outputBuffer.asFloatBuffer().get(outputs)

        val maxIndex = outputs.indices.maxByOrNull { outputs[it] } ?: 0
        val confidence = outputs[maxIndex]
        val gesture = gestureLabels[maxIndex] ?: "Unknown"

        return gesture to confidence
    }

    private fun classifyWithEnhancedRules(result: HandLandmarkerResult): Pair<String, Float> {
        if (result.landmarks().isEmpty()) {
            Log.d("GestureDebug", "No landmarks for classification")
            return "No Hand Detected" to 0f
        }

        val handLandmarks = result.landmarks()[0]
        if (handLandmarks.size < 21) {
            Log.d("GestureDebug", "Insufficient landmarks: ${handLandmarks.size}")
            return "Insufficient Data" to 0f
        }

        Log.d("GestureDebug", "Classifying with ${handLandmarks.size} landmarks")

        // For debugging, just return a simple thumbs up detection
        val thumbTip = handLandmarks[4]
        val wrist = handLandmarks[0]
        val indexTip = handLandmarks[8]

        // Simple thumbs up: thumb higher than wrist, index lower than wrist
        if (thumbTip.y() < wrist.y() && indexTip.y() > wrist.y()) {
            Log.d("GestureDebug", "Detected thumbs up gesture")
            return "Yes" to 0.9f
        }

        Log.d("GestureDebug", "Default classification - hand detected")
        return "Hand Detected" to 0.5f
    }

    private fun calculateDistance(
        point1: NormalizedLandmark?,
        point2: NormalizedLandmark?
    ): Float {
        val dx = point1!!.x() - point2!!.x()
        val dy = point1.y() - point2.y()
        return sqrt(dx * dx + dy * dy)
    }

    private fun convertLandmarksToPoints(result: HandLandmarkerResult): List<LandmarkPoint> {
        val points = mutableListOf<LandmarkPoint>()
        result.landmarks().forEach { handLandmarks ->
            handLandmarks.forEach { landmark ->
                points.add(LandmarkPoint(landmark.x(), landmark.y()))
            }
        }
        return points
    }

    private fun updateGestureState(gesture: String, confidence: Float, landmarks: List<LandmarkPoint>) {
        _uiState.value = _uiState.value.copy(
            currentGesture = gesture,
            confidence = confidence,
            landmarks = landmarks,
            lastDetectionTime = System.currentTimeMillis(),
            frameCount = _uiState.value.frameCount + 1
        )

        // Log significant detections
        if (confidence > 0.7f && gesture !in listOf("No Hand Detected", "Unknown", "Detection Error")) {
            Log.d("GestureRecognition", "Detected: $gesture (${String.format("%.2f", confidence)})")
        }
    }

    private fun handleStability(gesture: String, confidence: Float) {
        val validGestures = listOf("Hello", "Stop", "Yes", "Peace", "Help", "All_Done", "Love", "Please")

        if (gesture == lastPrediction && gesture in validGestures) {
            predictionCount++
        } else {
            predictionCount = 1
            lastPrediction = gesture
        }

        // Require more stability for final detection
        if (predictionCount > 8 && confidence > 0.7f && gesture in validGestures) {
            detectionHistory.add(gesture)
            _uiState.value = _uiState.value.copy(
                stableGesture = gesture,
                detectionHistory = detectionHistory.takeLast(10), // Keep last 10 detections
                totalDetections = detectionHistory.size
            )
            predictionCount = 0

            Log.d("GestureRecognition", "Stable gesture: $gesture (${String.format("%.2f", confidence)})")
        }
    }

    private fun convertImageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        Log.d("GestureDebug", "Converting ImageProxy: ${imageProxy.width}x${imageProxy.height}, format: ${imageProxy.format}")

        return when (imageProxy.format) {
            ImageFormat.YUV_420_888 -> {
                // Most common camera format
                convertYuv420ToBitmap(imageProxy)
            }
            ImageFormat.NV21 -> {
                // Legacy format
                convertNv21ToBitmap(imageProxy)
            }
            else -> {
                Log.w("GestureDebug", "Unsupported format: ${imageProxy.format}, trying YUV conversion")
                // Fallback to YUV conversion
                convertYuv420ToBitmap(imageProxy)
            }
        }
    }

    private fun convertYuv420ToBitmap(imageProxy: ImageProxy): Bitmap {
        val yBuffer = imageProxy.planes[0].buffer // Y
        val uBuffer = imageProxy.planes[1].buffer // U
        val vBuffer = imageProxy.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 80, out)
        val imageBytes = out.toByteArray()

        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        // Handle rotation and resize for better processing
        return processRotationAndResize(bitmap, imageProxy.imageInfo.rotationDegrees)
    }

    private fun convertNv21ToBitmap(imageProxy: ImageProxy): Bitmap {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val yuvImage = YuvImage(bytes, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 80, out)
        val imageBytes = out.toByteArray()

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun processRotationAndResize(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        // Handle rotation
        val rotatedBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply {
                postRotate(rotationDegrees.toFloat())
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }

        // Resize if too large (for better performance)
        return if (rotatedBitmap.width > 640 || rotatedBitmap.height > 640) {
            val scale = 640f / maxOf(rotatedBitmap.width, rotatedBitmap.height)
            val newWidth = (rotatedBitmap.width * scale).toInt()
            val newHeight = (rotatedBitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(rotatedBitmap, newWidth, newHeight, true)
        } else {
            rotatedBitmap
        }
    }

    private fun captureAndUploadImage(bitmap: Bitmap, timestamp: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val user = firebaseAuth.currentUser ?: return@launch
                val email = user.email ?: return@launch

                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                val timestampStr = dateFormat.format(Date(timestamp))
                val filename = "gesture_${_uiState.value.currentGesture}_$timestampStr.jpg"

                val emailFolder = email.replace(".", "_").replace("@", "_at_")
                val folderPath = "gestures/$emailFolder/$filename"

                val byteArray = bitmapToByteArray(bitmap)
                uploadToFirebase(byteArray, folderPath, filename)

                captureCount++
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        totalCapturedImages = captureCount,
                        lastCaptureTime = timestamp
                    )
                }

            } catch (e: Exception) {
                Log.e("GestureRecognition", "Capture failed", e)
            }
        }
    }

    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return outputStream.toByteArray()
    }

    private fun uploadToFirebase(byteArray: ByteArray, path: String, filename: String) {
        val storageRef: StorageReference = firebaseStorage.reference.child(path)
        storageRef.putBytes(byteArray)
            .addOnSuccessListener {
                _uiState.value = _uiState.value.copy(
                    totalUploadedImages = _uiState.value.totalUploadedImages + 1,
                    lastUploadStatus = "✓ $filename"
                )
                Log.d("GestureRecognition", "Upload successful: $filename")
            }
            .addOnFailureListener { e ->
                _uiState.value = _uiState.value.copy(
                    lastUploadStatus = "✗ $filename"
                )
                Log.e("GestureRecognition", "Upload failed: $filename", e)
            }
    }

    fun startDetection() {
        _uiState.value = _uiState.value.copy(
            isDetecting = true,
            error = null,
            currentGesture = "Detecting...",
            frameCount = 0
        )
        lastCaptureTime = 0L
        captureCount = 0
        frameSkipCounter = 0
        Log.d("GestureRecognition", "Detection started")
    }

    fun stopDetection() {
        _uiState.value = _uiState.value.copy(
            isDetecting = false,
            currentGesture = "Stopped"
        )
        Log.d("GestureRecognition", "Detection stopped")
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearHistory() {
        detectionHistory.clear()
        _uiState.value = _uiState.value.copy(
            detectionHistory = emptyList(),
            totalDetections = 0,
            stableGesture = ""
        )
        Log.d("GestureRecognition", "History cleared")
    }

    override fun onCleared() {
        super.onCleared()
        handLandmarker?.close()
        tfliteInterpreter?.close()
        Log.d("GestureRecognition", "ViewModel cleared")
    }
}

// Enhanced data classes
data class GestureState(
    val isInitialized: Boolean = false,
    val isDetecting: Boolean = false,
    val currentGesture: String = "Ready",
    val stableGesture: String = "",
    val confidence: Float = 0f,
    val landmarks: List<LandmarkPoint> = emptyList(),
    val lastDetectionTime: Long = 0L,
    val frameCount: Int = 0,
    val totalDetections: Int = 0,
    val detectionHistory: List<String> = emptyList(),
    val error: String? = null,
    val totalCapturedImages: Int = 0,
    val totalUploadedImages: Int = 0,
    val lastCaptureTime: Long = 0L,
    val lastUploadStatus: String? = null
)

data class LandmarkPoint(
    val x: Float,
    val y: Float
)

// Additional data classes for enhanced gesture recognition

data class GestureClassificationResult(
    val gesture: String,
    val confidence: Float,
    val processingTimeMs: Long,
    val landmarks: List<LandmarkPoint> = emptyList(),
    val handedness: String? = null, // "Left" or "Right"
    val boundingBox: RectF? = null
)

data class HandMetrics(
    val handSize: Float,
    val palmCenter: LandmarkPoint,
    val fingerExtensions: Map<String, Boolean>,
    val handOrientation: Float,
    val stability: Float
)

data class GesturePerformanceMetrics(
    val averageProcessingTime: Float = 0f,
    val totalFramesProcessed: Int = 0,
    val successfulDetections: Int = 0,
    val averageConfidence: Float = 0f,
    val detectionRate: Float = 0f,
    val lastMetricUpdate: Long = System.currentTimeMillis()
)

data class FirebaseUploadResult(
    val success: Boolean,
    val filename: String,
    val downloadUrl: String? = null,
    val errorMessage: String? = null,
    val uploadTimeMs: Long = 0L
)

// Configuration data class
data class GestureConfig(
    val minDetectionConfidence: Float = 0.7f,
    val minTrackingConfidence: Float = 0.5f,
    val stabilityThreshold: Int = 8,
    val maxHands: Int = 2,
    val captureInterval: Long = 3000L,
    val frameSkipInterval: Int = 3,
    val enableFirebaseUpload: Boolean = true,
    val enableTensorFlowLite: Boolean = true,
    val imageCompressionQuality: Int = 80
)

// Enhanced landmark processing utilities
object LandmarkUtils {

    fun calculateHandSize(landmarks: List<LandmarkPoint>): Float {
        if (landmarks.size < 21) return 0f

        val wrist = landmarks[0]
        val middleFingerTip = landmarks[12]

        return calculateDistance(wrist, middleFingerTip)
    }

    fun calculatePalmCenter(landmarks: List<LandmarkPoint>): LandmarkPoint? {
        if (landmarks.size < 21) return null

        val palmLandmarks = listOf(0, 1, 5, 9, 13, 17).map { landmarks[it] }
        val avgX = palmLandmarks.map { it.x }.average().toFloat()
        val avgY = palmLandmarks.map { it.y }.average().toFloat()

        return LandmarkPoint(avgX, avgY)
    }

    fun getFingerExtensions(landmarks: List<LandmarkPoint>): Map<String, Boolean> {
        if (landmarks.size < 21) return emptyMap()

        return mapOf(
            "thumb" to isFingerExtended(landmarks, listOf(1, 2, 3, 4)),
            "index" to isFingerExtended(landmarks, listOf(5, 6, 7, 8)),
            "middle" to isFingerExtended(landmarks, listOf(9, 10, 11, 12)),
            "ring" to isFingerExtended(landmarks, listOf(13, 14, 15, 16)),
            "pinky" to isFingerExtended(landmarks, listOf(17, 18, 19, 20))
        )
    }

    private fun isFingerExtended(landmarks: List<LandmarkPoint>, fingerIndices: List<Int>): Boolean {
        if (fingerIndices.size < 2) return false

        val tip = landmarks[fingerIndices.last()]
        val middle = landmarks[fingerIndices[fingerIndices.size - 2]]
        val wrist = landmarks[0]

        val tipToWrist = calculateDistance(tip, wrist)
        val middleToWrist = calculateDistance(middle, wrist)

        return tipToWrist > middleToWrist
    }

    fun calculateHandOrientation(landmarks: List<LandmarkPoint>): Float {
        if (landmarks.size < 21) return 0f

        val wrist = landmarks[0]
        val middleFingerMCP = landmarks[9]

        val deltaX = middleFingerMCP.x - wrist.x
        val deltaY = middleFingerMCP.y - wrist.y

        return atan2(deltaY, deltaX) * (180f / PI.toFloat())
    }

    fun calculateStability(currentLandmarks: List<LandmarkPoint>,
                           previousLandmarks: List<LandmarkPoint>?): Float {
        if (previousLandmarks == null || currentLandmarks.size != previousLandmarks.size) {
            return 0f
        }

        val totalDistance = currentLandmarks.zip(previousLandmarks) { current, previous ->
            calculateDistance(current, previous)
        }.sum()

        return 1f - (totalDistance / currentLandmarks.size).coerceAtMost(1f)
    }

    fun getBoundingBox(landmarks: List<LandmarkPoint>): RectF? {
        if (landmarks.isEmpty()) return null

        val xCoords = landmarks.map { it.x }
        val yCoords = landmarks.map { it.y }

        return RectF(
            xCoords.minOrNull() ?: 0f,
            yCoords.minOrNull() ?: 0f,
            xCoords.maxOrNull() ?: 0f,
            yCoords.maxOrNull() ?: 0f
        )
    }

    private fun calculateDistance(point1: LandmarkPoint, point2: LandmarkPoint): Float {
        val dx = point1.x - point2.x
        val dy = point1.y - point2.y
        return sqrt(dx * dx + dy * dy)
    }
}

// Constants for gesture recognition
object GestureConstants {

    // Gesture labels with display names
    val GESTURE_DISPLAY_NAMES = mapOf(
        "All_Done" to "All Done ✅",
        "Yes" to "Yes 👍",
        "No" to "No 👎",
        "Help" to "Help 🆘",
        "More" to "More ➕",
        "Thank_You" to "Thank You 🙏",
        "Please" to "Please 🥺",
        "Stop" to "Stop ✋",
        "Hello" to "Hello 👋",
        "Deaf" to "Deaf 🤟",
        "Love" to "Love ❤️",
        "Enjoying" to "Enjoying 😊",
        "Washroom" to "Washroom 🚽",
        "Boring" to "Boring 😴",
        "I_Do_Not_Know" to "I Don't Know 🤷",
        "I_Want_To_Talk" to "I Want to Talk 💬"
    )

    // Confidence thresholds
    const val MIN_CONFIDENCE_THRESHOLD = 0.3f
    const val GOOD_CONFIDENCE_THRESHOLD = 0.6f
    const val HIGH_CONFIDENCE_THRESHOLD = 0.8f

    // Processing constants
    const val MAX_PROCESSING_TIME_MS = 200L
    const val LANDMARK_COUNT = 21
    const val MAX_HANDS = 2
    const val FEATURE_VECTOR_SIZE = 84 // 42 coordinates per hand * 2 hands

    // Firebase storage paths
    const val STORAGE_PATH_PREFIX = "gesture_data"
    const val TRAINING_DATA_PATH = "training"
    const val USER_DATA_PATH = "user_submissions"

    // Performance tracking
    const val METRICS_UPDATE_INTERVAL = 5000L // 5 seconds
    const val MAX_HISTORY_SIZE = 50
}

// Error handling for gesture recognition
sealed class GestureError(val message: String, val cause: Throwable? = null) {
    object MediaPipeInitializationFailed : GestureError("Failed to initialize MediaPipe HandLandmarker")
    object TensorFlowInitializationFailed : GestureError("Failed to initialize TensorFlow Lite model")
    object CameraPermissionDenied : GestureError("Camera permission is required for gesture recognition")
    object ProcessingTimeout : GestureError("Gesture processing timed out")

    data class FirebaseUploadFailed(val filename: String, val error: String) :
        GestureError("Failed to upload $filename: $error")

    data class ModelInferenceFailed(val error: String) :
        GestureError("Model inference failed: $error")

    data class ImageProcessingFailed(val error: String) :
        GestureError("Image processing failed: $error")
}

// Extension functions for enhanced functionality
fun LandmarkPoint.distanceTo(other: LandmarkPoint): Float {
    val dx = this.x - other.x
    val dy = this.y - other.y
    return sqrt(dx * dx + dy * dy)
}

fun List<LandmarkPoint>.center(): LandmarkPoint? {
    if (isEmpty()) return null
    val avgX = map { it.x }.average().toFloat()
    val avgY = map { it.y }.average().toFloat()
    return LandmarkPoint(avgX, avgY)
}

fun GestureState.isHighConfidence(): Boolean = confidence >= GestureConstants.HIGH_CONFIDENCE_THRESHOLD

fun GestureState.getDisplayGestureName(): String =
    GestureConstants.GESTURE_DISPLAY_NAMES[currentGesture] ?: currentGesture.replace("_", " ")

fun GestureClassificationResult.isReliable(): Boolean =
    confidence >= GestureConstants.GOOD_CONFIDENCE_THRESHOLD &&
            processingTimeMs <= GestureConstants.MAX_PROCESSING_TIME_MS

// Performance monitoring utility
class GesturePerformanceMonitor {
    private var totalProcessingTime = 0L
    private var frameCount = 0
    private var successfulDetections = 0
    private var totalConfidence = 0f
    private var lastUpdateTime = System.currentTimeMillis()

    fun recordFrame(processingTime: Long, confidence: Float, successful: Boolean) {
        totalProcessingTime += processingTime
        frameCount++
        totalConfidence += confidence

        if (successful) {
            successfulDetections++
        }
    }

    fun getMetrics(): GesturePerformanceMetrics {
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - lastUpdateTime

        return GesturePerformanceMetrics(
            averageProcessingTime = if (frameCount > 0) totalProcessingTime.toFloat() / frameCount else 0f,
            totalFramesProcessed = frameCount,
            successfulDetections = successfulDetections,
            averageConfidence = if (frameCount > 0) totalConfidence / frameCount else 0f,
            detectionRate = if (frameCount > 0) successfulDetections.toFloat() / frameCount else 0f,
            lastMetricUpdate = currentTime
        )
    }

    fun reset() {
        totalProcessingTime = 0L
        frameCount = 0
        successfulDetections = 0
        totalConfidence = 0f
        lastUpdateTime = System.currentTimeMillis()
    }
}