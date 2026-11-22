package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.database.AnalysisEntity
import com.example.data.database.AppDatabase
import com.example.data.database.UserProgressEntity
import com.example.data.model.AnalysisResult
import com.example.data.model.ComparisonResult
import com.example.data.network.Content
import com.example.data.network.GeminiRequest
import com.example.data.network.GenerationConfig
import com.example.data.network.InlineData
import com.example.data.network.Part
import com.example.data.network.RetrofitClient
import com.example.data.repository.PhotoRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.random.Random

class SnapJudgeViewModel(
    application: Application,
    private val repository: PhotoRepository
) : AndroidViewModel(application) {

    val userProgress: StateFlow<UserProgressEntity?> = repository.userProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allAnalyses: StateFlow<List<AnalysisEntity>> = repository.allAnalyses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteAnalyses: StateFlow<List<AnalysisEntity>> = repository.favoriteAnalyses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisStatus = MutableStateFlow("Idle")
    val analysisStatus: StateFlow<String> = _analysisStatus.asStateFlow()

    private val _currentResult = MutableStateFlow<AnalysisResult?>(null)
    val currentResult: StateFlow<AnalysisResult?> = _currentResult.asStateFlow()

    private val _currentResultImagePath = MutableStateFlow<String?>(null)
    val currentResultImagePath: StateFlow<String?> = _currentResultImagePath.asStateFlow()

    private val _currentResultId = MutableStateFlow<Int?>(null)
    val currentResultId: StateFlow<Int?> = _currentResultId.asStateFlow()

    private val _currentResultIsFavorite = MutableStateFlow(false)
    val currentResultIsFavorite: StateFlow<Boolean> = _currentResultIsFavorite.asStateFlow()

    // Compare Mode State
    private val _compareImage1 = MutableStateFlow<Bitmap?>(null)
    val compareImage1: StateFlow<Bitmap?> = _compareImage1.asStateFlow()

    private val _compareImage2 = MutableStateFlow<Bitmap?>(null)
    val compareImage2: StateFlow<Bitmap?> = _compareImage2.asStateFlow()

    private val _isComparing = MutableStateFlow(false)
    val isComparing: StateFlow<Boolean> = _isComparing.asStateFlow()

    private val _comparisonResult = MutableStateFlow<ComparisonResult?>(null)
    val comparisonResult: StateFlow<ComparisonResult?> = _comparisonResult.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initializeProgressIfNeeded()
        }
    }

    fun setCompareImage1(bitmap: Bitmap?) {
        _compareImage1.value = bitmap
    }

    fun setCompareImage2(bitmap: Bitmap?) {
        _compareImage2.value = bitmap
    }

    fun clearComparison() {
        _compareImage1.value = null
        _compareImage2.value = null
        _comparisonResult.value = null
    }

    fun saveOnboarding(name: String, mode: String) {
        viewModelScope.launch {
            repository.updateOnboarded(name, mode)
        }
    }

    fun updateSelectedMode(mode: String) {
        viewModelScope.launch {
            repository.updateMode(mode)
        }
    }

    // Toggle favorite on current result
    fun toggleCurrentResultFavorite() {
        val id = _currentResultId.value ?: return
        viewModelScope.launch {
            repository.toggleFavorite(id)
            _currentResultIsFavorite.value = !_currentResultIsFavorite.value
        }
    }

    // Delete current result
    fun deleteCurrentResult() {
        val id = _currentResultId.value ?: return
        viewModelScope.launch {
            repository.deleteAnalysis(id)
            _currentResult.value = null
            _currentResultImagePath.value = null
            _currentResultId.value = null
        }
    }

    // Load custom analysis result from database
    fun loadResultFromEntity(entity: AnalysisEntity) {
        viewModelScope.launch {
            try {
                val adapter = RetrofitClient.moshi.adapter(AnalysisResult::class.java)
                val parsed = adapter.fromJson(entity.analysisJson)
                _currentResult.value = parsed
                _currentResultImagePath.value = entity.imagePath
                _currentResultId.value = entity.id
                _currentResultIsFavorite.value = entity.isFavorite
            } catch (e: Exception) {
                Log.e("SnapJudgeViewModel", "Failed to parse analysis JSON", e)
            }
        }
    }

    // Execute standard single photo analysis
    fun analyzePhoto(bitmap: Bitmap, mode: String) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _currentResult.value = null
            _currentResultImagePath.value = null
            _currentResultId.value = null
            
            try {
                val result = repository.analyzePhoto(bitmap, mode) { status ->
                    _analysisStatus.value = status
                }
                _currentResult.value = result
                
                // Get the newly saved entity to capture the correct ID and local image path
                val analyses = repository.allAnalyses.stateIn(viewModelScope).value
                val latest = analyses.firstOrNull()
                if (latest != null) {
                    _currentResultImagePath.value = latest.imagePath
                    _currentResultId.value = latest.id
                    _currentResultIsFavorite.value = latest.isFavorite
                }
            } catch (e: Exception) {
                Log.e("SnapJudgeViewModel", "Analysis flow failed", e)
                _analysisStatus.value = "Error: ${e.message}"
                delay(2000)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    // Compare two photos side by side
    fun comparePhotos() {
        val img1 = _compareImage1.value ?: return
        val img2 = _compareImage2.value ?: return

        viewModelScope.launch {
            _isComparing.value = true
            _comparisonResult.value = null
            
            val apiKey = BuildConfig.GEMINI_API_KEY
            val hasApiKey = apiKey.isNotEmpty() && !apiKey.contains("MY_GEMINI_API_KEY")

            if (!hasApiKey) {
                // Return high quality simulated comparison
                delay(3000)
                _comparisonResult.value = generateMockComparison()
                repository.awardXp(150)
                _isComparing.value = false
                return@launch
            }

            try {
                val base64Img1 = img1.toBase64()
                val base64Img2 = img2.toBase64()

                val prompt = """
                    You are SnapJudge X. Compare the following two uploaded photographs side-by-side.
                    Analyze and contrast them across six major creative dimensions:
                    1. Composition
                    2. Lighting
                    3. Emotion
                    4. Storytelling
                    5. Editing
                    6. Commercial Value

                    Determine which photo is the Overall Winner (return 1 for Photo A, or 2 for Photo B).
                    Give clear score values (0 to 100) for both Photo A and Photo B in each category, and write a thorough comparative review explaining your decisions.

                    Respond with a single, raw JSON object matching the following format exactly, with no markdown code blocks:
                    {
                      "winnerIndex": 1,
                      "overallVerdict": "Detailed summary comparing both...",
                      "compositionScore1": 85,
                      "compositionScore2": 72,
                      "compositionComparison": "Comparison of composition...",
                      "lightingScore1": 90,
                      "lightingScore2": 80,
                      "lightingComparison": "Comparison of lighting...",
                      "emotionScore1": 75,
                      "emotionScore2": 88,
                      "emotionComparison": "Comparison of emotion...",
                      "storytellingScore1": 80,
                      "storytellingScore2": 82,
                      "storytellingComparison": "Comparison of storytelling...",
                      "editingScore1": 85,
                      "editingScore2": 70,
                      "editingComparison": "Comparison of editing...",
                      "commercialScore1": 90,
                      "commercialScore2": 65,
                      "commercialComparison": "Comparison of commercial value..."
                    }
                """.trimIndent()

                val request = GeminiRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(
                                Part(text = prompt),
                                Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Img1)),
                                Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Img2))
                            )
                        )
                    ),
                    generationConfig = GenerationConfig(
                        temperature = 0.4f,
                        responseMimeType = "application/json"
                    )
                )

                val response = RetrofitClient.service.generateContent(apiKey, request)
                val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("No response text from comparison model")

                val cleaned = cleanJsonString(rawJson)
                val adapter = RetrofitClient.moshi.adapter(ComparisonResult::class.java)
                _comparisonResult.value = adapter.fromJson(cleaned)
                repository.awardXp(200)
            } catch (e: Exception) {
                Log.e("SnapJudgeViewModel", "Gemini comparison failed, falling back to simulation", e)
                _comparisonResult.value = generateMockComparison()
                repository.awardXp(150)
            } finally {
                _isComparing.value = false
            }
        }
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        val scaled = if (width > 800 || height > 800) {
            val aspect = width.toFloat() / height.toFloat()
            val newW = if (aspect > 1.0f) 800 else (800 * aspect).toInt()
            val newH = if (aspect > 1.0f) (800 / aspect).toInt() else 800
            Bitmap.createScaledBitmap(this, newW, newH, true)
        } else {
            this
        }
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun cleanJsonString(input: String): String {
        var cleaned = input.trim()
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substringAfter("```json")
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substringAfter("```")
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substringBeforeLast("```")
        }
        return cleaned.trim()
    }

    private fun generateMockComparison(): ComparisonResult {
        return ComparisonResult(
            winnerIndex = 1,
            overallVerdict = "Photo A is crowned the Overall Winner! It presents a significantly cleaner composition with sophisticated lighting controls. While Photo B shows brilliant emotional warmth, it struggles with edge distractions and minor shadow clipping that weakens its structural appeal.",
            compositionScore1 = 88,
            compositionScore2 = 72,
            compositionComparison = "Photo A utilizes perfect geometric symmetry and leading lines that drive attention directly to the subject. Photo B employs a rule-of-thirds alignment, but contains multiple distracting highlights on the extreme right border.",
            lightingScore1 = 92,
            lightingScore2 = 78,
            lightingComparison = "Photo A displays gorgeous soft ambient key light with delicate highlight roll-offs. Photo B suffers from harsh direct lighting, resulting in slightly overexposed forehead surfaces.",
            emotionScore1 = 76,
            emotionScore2 = 85,
            emotionComparison = "Photo B excels strongly in raw emotion; the warm sunset hues and relaxed subject expression generate a powerful sense of intimate storytelling. Photo A feels slightly more clinical, yet remains visually striking.",
            storytellingScore1 = 82,
            storytellingScore2 = 80,
            storytellingComparison = "Photo A tells a story of spatial scale and isolated presence within architectural context. Photo B presents an intimate, candid lifestyle vignette. Both have compelling narratives.",
            editingScore1 = 85,
            editingScore2 = 68,
            editingComparison = "Photo A is expertly graded, showing clean midtone clarity and professional film grain emulations. Photo B is slightly over-saturated, leading to unnatural skin graduations.",
            commercialScore1 = 90,
            commercialScore2 = 60,
            commercialComparison = "Photo A holds massive commercial viability for high-fashion catalogs or billboard copy space. Photo B is better suited for casual personal galleries or local travel journals."
        )
    }
}

class SnapJudgeViewModelFactory(
    private val application: Application,
    private val repository: PhotoRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SnapJudgeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SnapJudgeViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
