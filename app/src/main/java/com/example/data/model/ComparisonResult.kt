package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ComparisonResult(
    val winnerIndex: Int, // 1 or 2
    val overallVerdict: String,
    val compositionScore1: Int,
    val compositionScore2: Int,
    val compositionComparison: String,
    val lightingScore1: Int,
    val lightingScore2: Int,
    val lightingComparison: String,
    val emotionScore1: Int,
    val emotionScore2: Int,
    val emotionComparison: String,
    val storytellingScore1: Int,
    val storytellingScore2: Int,
    val storytellingComparison: String,
    val editingScore1: Int,
    val editingScore2: Int,
    val editingComparison: String,
    val commercialScore1: Int,
    val commercialScore2: Int,
    val commercialComparison: String
)
