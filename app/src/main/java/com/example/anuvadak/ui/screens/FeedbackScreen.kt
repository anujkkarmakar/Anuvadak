package com.example.anuvadak.ui.screens

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// --- Enhanced ViewModel ---
class FeedbackViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(FeedbackState())
    val uiState: StateFlow<FeedbackState> = _uiState.asStateFlow()

    private val firestore: FirebaseFirestore = Firebase.firestore

    fun updateName(newName: String) {
        _uiState.value = _uiState.value.copy(name = newName, error = null)
    }

    fun updateMobileNumber(newNumber: String) {
        // Filter to allow only digits and limit length to 10 digits
        val filteredNumber = newNumber.filter { it.isDigit() }.take(10)
        _uiState.value = _uiState.value.copy(mobileNumber = filteredNumber, error = null)
    }

    fun updateFeedbackText(newText: String) {
        _uiState.value = _uiState.value.copy(feedbackText = newText, error = null)
    }

    fun updateFeedbackCategory(category: String) {
        _uiState.value = _uiState.value.copy(feedbackCategory = category, error = null)
    }

    fun updateRating(rating: Int) {
        _uiState.value = _uiState.value.copy(rating = rating, error = null)
    }

    fun resetForm() {
        _uiState.value = FeedbackState()
    }

    fun showCancelConfirmation(show: Boolean) {
        _uiState.value = _uiState.value.copy(showCancelConfirmation = show)
    }

    fun submitFeedback() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, submitSuccess = false)

            // Enhanced validation
            if (_uiState.value.name.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    error = "Please enter your name.",
                    isLoading = false
                )
                return@launch
            }

            if (_uiState.value.name.length < 2) {
                _uiState.value = _uiState.value.copy(
                    error = "Name must be at least 2 characters long.",
                    isLoading = false
                )
                return@launch
            }

            if (_uiState.value.mobileNumber.length != 10) {
                _uiState.value = _uiState.value.copy(
                    error = "Please enter a valid 10-digit mobile number.",
                    isLoading = false
                )
                return@launch
            }

            if (_uiState.value.feedbackText.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    error = "Please share your feedback with us.",
                    isLoading = false
                )
                return@launch
            }

            if (_uiState.value.feedbackText.length < 10) {
                _uiState.value = _uiState.value.copy(
                    error = "Please provide more detailed feedback (at least 10 characters).",
                    isLoading = false
                )
                return@launch
            }

            try {
                val feedbackData = hashMapOf(
                    "name" to _uiState.value.name.trim(),
                    "mobile" to "+91${_uiState.value.mobileNumber}",
                    "feedback" to _uiState.value.feedbackText.trim(),
                    "category" to _uiState.value.feedbackCategory,
                    "rating" to _uiState.value.rating,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "appVersion" to "1.0.0", // You can get this dynamically
                    "platform" to "Android"
                )

                firestore.collection("user_feedback")
                    .add(feedbackData)
                    .await()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    submitSuccess = true,
                    error = null
                )

                // Show success message for 3 seconds then reset
                delay(3000)
                resetForm()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    submitSuccess = false,
                    error = "Failed to submit feedback. Please check your internet connection and try again."
                )
            }
        }
    }
}

// --- Enhanced State ---
data class FeedbackState(
    val name: String = "",
    val mobileNumber: String = "",
    val feedbackText: String = "",
    val feedbackCategory: String = "General Feedback",
    val rating: Int = 0,
    val isLoading: Boolean = false,
    val submitSuccess: Boolean = false,
    val error: String? = null,
    val showCancelConfirmation: Boolean = false
)

// --- Enhanced UI Components ---
@Composable
fun RatingBar(
    rating: Int,
    onRatingChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (i in 1..5) {
            IconButton(
                onClick = { onRatingChange(i) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Rate $i star${if (i > 1) "s" else ""}",
                    tint = if (i <= rating) Color(0xFFFF9933) else Color.Gray, // Saffron color for filled stars
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun CategorySelector(
    selectedCategory: String,
    onCategoryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val categories = listOf(
        "General Feedback" to Icons.Default.Feedback,
        "Bug Report" to Icons.Default.BugReport,
        "Feature Request" to Icons.Default.Lightbulb,
        "Translation Quality" to Icons.Default.Translate,
        "User Interface" to Icons.Default.Palette,
        "Performance" to Icons.Default.Speed,
        "Accessibility" to Icons.Default.Accessibility,
        "Other" to Icons.Default.MoreHoriz
    )

    Box(modifier = modifier) {
        OutlinedTextField(
            value = selectedCategory,
            onValueChange = { },
            readOnly = true,
            label = { Text("Feedback Category", color = Color.Black) },
            leadingIcon = {
                val selectedIcon = categories.find { it.first == selectedCategory }?.second ?: Icons.Default.Feedback
                Icon(selectedIcon, contentDescription = "Category Icon", tint = Color.Black)
            },
            trailingIcon = {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Select Category",
                    tint = Color.Black
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                focusedBorderColor = Color(0xFFFF9933),
                unfocusedBorderColor = Color.Gray
            )
        )

        // Invisible clickable area to trigger dropdown
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            categories.forEach { (category, icon) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            category,
                            color = Color.Black
                        )
                    },
                    onClick = {
                        onCategoryChange(category)
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (category == selectedCategory) Color(0xFFFF9933) else Color.Gray
                        )
                    },
                    trailingIcon = {
                        if (category == selectedCategory) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFFFF9933)
                            )
                        }
                    }
                )
            }
        }
    }
}

// --- Enhanced Main Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    viewModel: FeedbackViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // Cancel confirmation dialog
    if (state.showCancelConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.showCancelConfirmation(false) },
            title = { Text("Discard Feedback?", color = Color.Black) },
            text = { Text("Are you sure you want to discard your feedback? All entered information will be lost.", color = Color.Black) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetForm()
                        viewModel.showCancelConfirmation(false)
                    }
                ) {
                    Text("Discard", color = Color(0xFFDC3545))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showCancelConfirmation(false) }) {
                    Text("Keep Editing", color = Color(0xFFFF9933))
                }
            },
            containerColor = Color.White
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Section with Indian flag theme
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFFF4E6) // Light saffron background
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🇮🇳 ✍️",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Share Your Feedback",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFF9933), // Saffron color
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Help us improve Anuvadak for everyone",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Main Form Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Name Field
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { viewModel.updateName(it) },
                    label = { Text("Your Name *", color = Color.Black) },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = "Name Icon", tint = Color.Black)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedBorderColor = Color(0xFFFF9933),
                        unfocusedBorderColor = Color.Gray
                    ),
                    placeholder = { Text("Enter your full name", color = Color.Gray) }
                )

                // Mobile Number Field with enhanced design
                OutlinedTextField(
                    value = state.mobileNumber,
                    onValueChange = { viewModel.updateMobileNumber(it) },
                    label = { Text("Mobile Number *", color = Color.Black) },
                    leadingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Phone Icon", tint = Color.Black)
                            Text("+91", color = Color.Black, fontWeight = FontWeight.Medium)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedBorderColor = Color(0xFFFF9933),
                        unfocusedBorderColor = Color.Gray
                    ),
                    placeholder = { Text("Enter 10-digit number", color = Color.Gray) }
                )

                // Category Selector
                CategorySelector(
                    selectedCategory = state.feedbackCategory,
                    onCategoryChange = { viewModel.updateFeedbackCategory(it) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Rating Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF8F9FA) // Light gray background
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Rate Your Experience",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        RatingBar(
                            rating = state.rating,
                            onRatingChange = { viewModel.updateRating(it) },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Text(
                            text = when (state.rating) {
                                1 -> "Poor - Needs significant improvement"
                                2 -> "Fair - Several issues to address"
                                3 -> "Good - Generally satisfactory"
                                4 -> "Very Good - Minor improvements needed"
                                5 -> "Excellent - Exceeds expectations"
                                else -> "Tap stars to rate your experience"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Feedback Text Area with character counter
                Column {
                    OutlinedTextField(
                        value = state.feedbackText,
                        onValueChange = { viewModel.updateFeedbackText(it) },
                        label = { Text("Your Detailed Feedback *", color = Color.Black) },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = "Feedback Icon", tint = Color.Black)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedBorderColor = Color(0xFFFF9933),
                            unfocusedBorderColor = Color.Gray
                        ),
                        placeholder = {
                            Text(
                                "Tell us about your experience with Anuvadak. What features do you like? What can be improved?",
                                color = Color.Gray
                            )
                        },
                        singleLine = false,
                        maxLines = 6
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Minimum 10 characters required",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Text(
                            text = "${state.feedbackText.length}/500",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.feedbackText.length > 450) Color.Red else Color.Gray
                        )
                    }
                }

                // Error Message with enhanced styling
                state.error?.let { error ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF8D7DA) // Light red background
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = "Error",
                                tint = Color(0xFFDC3545),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = error,
                                color = Color(0xFFDC3545),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Success Message with enhanced styling
                if (state.submitSuccess) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE8F5E8) // Light green background
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = Color(0xFF138808),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Column {
                                Text(
                                    text = "Feedback Submitted Successfully! 🎉",
                                    color = Color(0xFF138808),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Thank you for helping us improve Anuvadak!",
                                    color = Color(0xFF138808),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons with enhanced styling
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel Button
                    OutlinedButton(
                        onClick = {
                            if (state.name.isNotBlank() || state.mobileNumber.isNotBlank() || state.feedbackText.isNotBlank()) {
                                viewModel.showCancelConfirmation(true)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.Gray
                        )
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Cancel",
                            modifier = Modifier.padding(end = 4.dp),
                            tint = Color.Gray
                        )
                        Text("Clear", color = Color.Gray)
                    }

                    // Submit Button
                    Button(
                        onClick = { viewModel.submitFeedback() },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isLoading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9933), // Saffron color
                            contentColor = Color.White
                        )
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Text(
                                "Submitting...",
                                modifier = Modifier.padding(start = 8.dp),
                                color = Color.White
                            )
                        } else {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Submit Feedback",
                                modifier = Modifier.padding(end = 8.dp),
                                tint = Color.White
                            )
                            Text("Submit Feedback", color = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Footer with additional info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFE8F5E8) // Light green background
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.PrivacyTip,
                    contentDescription = "Privacy",
                    tint = Color(0xFF138808),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Your Privacy Matters",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Your feedback is confidential and will only be used to improve our app. We respect your privacy and won't share your information.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF495057),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// --- Preview ---
@Preview(showBackground = true, name = "Enhanced Feedback Screen Preview")
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES, name = "Enhanced Feedback Screen Preview - Dark")
@Composable
fun EnhancedFeedbackScreenPreview() {
    MaterialTheme {
        Surface(color = Color.White) {
            FeedbackScreen()
        }
    }
}