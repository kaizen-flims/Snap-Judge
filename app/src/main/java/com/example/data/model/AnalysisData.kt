package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MetricDetail(
    val name: String,
    val score: Int,
    val explanation: String,
    val advice: String,
    val examples: String? = null,
    val suggestions: String? = null
)

@JsonClass(generateAdapter = true)
data class ExpertOpinion(
    val role: String, // "Professional Photographer", "Cinematographer", "Colorist", etc.
    val review: String
)

@JsonClass(generateAdapter = true)
data class HeatmapPoint(
    val x: Float, // 0.0 to 1.0 (relative coordinate)
    val y: Float, // 0.0 to 1.0 (relative coordinate)
    val radius: Float,
    val intensity: Float, // 0.0 to 1.0
    val label: String,
    val explanation: String
)

@JsonClass(generateAdapter = true)
data class CropSuggestion(
    val ratioName: String, // e.g., "Instagram (1:1)", "Rule of Thirds"
    val xOffset: Float, // 0.0 to 1.0
    val yOffset: Float,
    val width: Float,
    val height: Float,
    val reason: String
)

@JsonClass(generateAdapter = true)
data class EditSetting(
    val key: String, // "Exposure", "Contrast", etc.
    val value: Float,
    val formattedValue: String,
    val explanation: String
)

@JsonClass(generateAdapter = true)
data class EmotionFactor(
    val name: String, // "Mystery", "Peace", etc.
    val value: Int, // 0 to 100
    val reason: String
)

@JsonClass(generateAdapter = true)
data class MovieMatch(
    val title: String,
    val confidence: Int,
    val reason: String
)

@JsonClass(generateAdapter = true)
data class PhotoStyleMatch(
    val style: String,
    val confidence: Int,
    val reason: String
)

@JsonClass(generateAdapter = true)
data class StoryOutput(
    val creativeTitle: String,
    val instagramCaption: String,
    val professionalCaption: String,
    val shortStory: String,
    val travelJournal: String,
    val poem: String,
    val movieNarration: String,
    val hashtags: List<String>
)

@JsonClass(generateAdapter = true)
data class AnalysisResult(
    val overallScore: Int,
    val verdict: String,
    val experts: List<ExpertOpinion>,
    val metrics: List<MetricDetail>,
    val heatmap: List<HeatmapPoint>,
    val crops: List<CropSuggestion>,
    val edits: List<EditSetting>,
    val filmEmulation: String,
    val lutSuggestion: String,
    val emotions: List<EmotionFactor>,
    val movies: List<MovieMatch>,
    val styles: List<PhotoStyleMatch>,
    val stories: StoryOutput
)
