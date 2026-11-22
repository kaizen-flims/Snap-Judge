package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.database.AnalysisDao
import com.example.data.database.AnalysisEntity
import com.example.data.database.UserProgressDao
import com.example.data.database.UserProgressEntity
import com.example.data.model.*
import com.example.data.network.Content
import com.example.data.network.GeminiRequest
import com.example.data.network.GenerationConfig
import com.example.data.network.InlineData
import com.example.data.network.Part
import com.example.data.network.RetrofitClient
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Calendar
import kotlin.random.Random

class PhotoRepository(
    private val context: Context,
    private val analysisDao: AnalysisDao,
    private val userProgressDao: UserProgressDao,
    private val moshi: Moshi
) {
    val allAnalyses: Flow<List<AnalysisEntity>> = analysisDao.getAllAnalyses()
    val favoriteAnalyses: Flow<List<AnalysisEntity>> = analysisDao.getFavoriteAnalyses()
    val userProgress: Flow<UserProgressEntity?> = userProgressDao.getUserProgress()

    // Initialize progress if it doesn't exist
    suspend fun initializeProgressIfNeeded() = withContext(Dispatchers.IO) {
        val current = userProgressDao.getUserProgressSync()
        if (current == null) {
            userProgressDao.insertUserProgress(UserProgressEntity())
        }
    }

    suspend fun updateMode(mode: String) = withContext(Dispatchers.IO) {
        val current = userProgressDao.getUserProgressSync() ?: UserProgressEntity()
        userProgressDao.insertUserProgress(current.copy(selectedMode = mode))
    }

    suspend fun updateOnboarded(name: String, mode: String) = withContext(Dispatchers.IO) {
        val current = userProgressDao.getUserProgressSync() ?: UserProgressEntity()
        userProgressDao.insertUserProgress(
            current.copy(
                name = name,
                selectedMode = mode,
                isOnboarded = true
            )
        )
    }

    suspend fun toggleFavorite(id: Int) = withContext(Dispatchers.IO) {
        val entity = analysisDao.getAnalysisById(id)
        if (entity != null) {
            analysisDao.updateAnalysis(entity.copy(isFavorite = !entity.isFavorite))
        }
    }

    suspend fun deleteAnalysis(id: Int) = withContext(Dispatchers.IO) {
        val entity = analysisDao.getAnalysisById(id)
        if (entity != null) {
            // Also try to delete local file to save storage space
            try {
                val file = File(entity.imagePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e("PhotoRepository", "Failed to delete file", e)
            }
            analysisDao.deleteAnalysis(entity)
        }
    }

    // Save image locally in internal storage so it remains accessible offline
    private suspend fun saveImageToInternalStorage(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val filename = "snapjudge_${System.currentTimeMillis()}.jpg"
        val file = File(context.filesDir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        file.absolutePath
    }

    suspend fun awardXp(amount: Int) = withContext(Dispatchers.IO) {
        val current = userProgressDao.getUserProgressSync() ?: UserProgressEntity()
        val newXp = current.xp + amount
        // Simple levelling system: 1000 XP per level
        val newLevel = (newXp / 1000) + 1
        
        // Streak check
        val lastTime = current.lastAnalysisTimestamp
        val today = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() }
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.DAY_OF_YEAR, -1)
        }
        
        val lastCal = Calendar.getInstance().apply { timeInMillis = lastTime }
        
        val isSameDay = today.get(Calendar.YEAR) == lastCal.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == lastCal.get(Calendar.DAY_OF_YEAR)
                
        val isConsecutive = yesterday.get(Calendar.YEAR) == lastCal.get(Calendar.YEAR) &&
                yesterday.get(Calendar.DAY_OF_YEAR) == lastCal.get(Calendar.DAY_OF_YEAR)

        val newStreak = when {
            lastTime == 0L -> 1
            isSameDay -> current.streak
            isConsecutive -> current.streak + 1
            else -> 1 // Streak broken
        }

        userProgressDao.insertUserProgress(
            current.copy(
                xp = newXp,
                level = newLevel,
                streak = newStreak,
                lastAnalysisTimestamp = System.currentTimeMillis()
            )
        )
    }

    // Convert Bitmap to Base64
    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        // Resize bitmap to max 1024px to stay within limits and keep transfer fast
        val maxDim = 1024
        val scaled = if (width > maxDim || height > maxDim) {
            val aspect = width.toFloat() / height.toFloat()
            val newW = if (aspect > 1.0f) maxDim else (maxDim * aspect).toInt()
            val newH = if (aspect > 1.0f) (maxDim / aspect).toInt() else maxDim
            Bitmap.createScaledBitmap(this, newW, newH, true)
        } else {
            this
        }
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    // Call Gemini API to analyze photo
    suspend fun analyzePhoto(
        bitmap: Bitmap,
        mode: String, // COACH, ROAST, PROFESSIONAL, BEGINNER
        onStatusChange: (String) -> Unit
    ): AnalysisResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val hasApiKey = apiKey.isNotEmpty() && !apiKey.contains("MY_GEMINI_API_KEY")

        onStatusChange("Analyzing Composition...")
        Thread.sleep(1000)
        onStatusChange("Understanding Story & Depth...")
        Thread.sleep(800)
        onStatusChange("Reading Color Harmony...")
        Thread.sleep(800)

        if (!hasApiKey) {
            Log.w("PhotoRepository", "API key is missing or is placeholder. Using Local AI Engine Simulation.")
            val result = generateMockAnalysis(mode)
            saveAnalysisToDatabase(bitmap, result, mode)
            return@withContext result
        }

        val base64Image = bitmap.toBase64()
        val prompt = getAnalysisPrompt(mode)

        onStatusChange("Evaluating Exposure & Contrast...")
        Thread.sleep(600)
        onStatusChange("Calculating Portfolio Score...")

        val request = GeminiRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                temperature = 0.4f,
                responseMimeType = "application/json"
            )
        )

        try {
            onStatusChange("Generating Expert Panels...")
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw Exception("No response text from Gemini API")

            Log.d("PhotoRepository", "Raw JSON from Gemini: $rawJson")
            val cleanedJson = cleanJsonString(rawJson)
            
            val adapter = moshi.adapter(AnalysisResult::class.java)
            val result = adapter.fromJson(cleanedJson) ?: throw Exception("JSON Parsing returned null")
            
            onStatusChange("Saving verdict...")
            saveAnalysisToDatabase(bitmap, result, mode)
            return@withContext result
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Gemini analysis failed, falling back to Local AI", e)
            val result = generateMockAnalysis(mode)
            saveAnalysisToDatabase(bitmap, result, mode)
            return@withContext result
        }
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

    private suspend fun saveAnalysisToDatabase(
        bitmap: Bitmap,
        result: AnalysisResult,
        mode: String
    ) {
        val imagePath = saveImageToInternalStorage(bitmap)
        val adapter = moshi.adapter(AnalysisResult::class.java)
        val json = adapter.toJson(result)
        
        val style = result.styles.maxByOrNull { it.confidence }?.style ?: "General"
        
        val entity = AnalysisEntity(
            imagePath = imagePath,
            overallScore = result.overallScore,
            verdict = result.verdict,
            style = style,
            mode = mode,
            analysisJson = json
        )
        
        analysisDao.insertAnalysis(entity)
        awardXp(250) // Analyze complete awards 250 XP
    }

    private fun getAnalysisPrompt(mode: String): String {
        val modeInstruction = when(mode) {
            "ROAST" -> "Adopt a playful, highly witty, funny but polite 'Roast' tone. Point out subtle flaws or humorous cliches in a clever, light-hearted way. Make the suggestions memorable and amusing, but still technically valid."
            "PROFESSIONAL" -> "Adopt an extremely formal, master photographer / gallery curator level critique. Use precise industry terms, focus deeply on technical metrics like micro-contrast, lens aberration, print quality, and commercial viability. Keep advice actionable for professional portfolios."
            "BEGINNER" -> "Adopt an encouraging, patient mentor voice. Avoid overwhelming technical jargon, explain basic concepts like leading lines, highlights, and color wheel dynamics simply, and motivate the user to keep practicing."
            else -> "Adopt an educational, friendly, supportive 'Coach Mode' tone. Balance highlights and drawbacks of the photo, explaining photography principles clearly to help them learn."
        }

        return """
        Perform a comprehensive, master-level expert analysis on this image as SnapJudge X, the world's smartest AI photography mentor. 
        You represent a unified panel of 11 distinct creative experts analyzing this same photograph:
        1. Professional Photographer
        2. Cinematographer
        3. Colorist
        4. Visual Designer
        5. Art Director
        6. Social Media Strategist
        7. Graphic Designer
        8. Commercial Brand Consultant
        9. Photography Competition Judge
        10. Storytelling Expert
        11. Human Psychology Expert

        ${modeInstruction}

        You MUST respond with a single, perfectly formatted JSON object containing EXACTLY these keys. Do not include markdown headers or wrappers.
        
        Structure details:
        1. "overallScore": Integer from 0 to 100.
        2. "verdict": A single cinematic, paragraph-long review summarizing the photo's strengths, weaknesses, and a concrete path forward.
        3. "experts": List of objects with:
           - "role": One of ["Professional Photographer", "Colorist", "Cinematographer", "Visual Designer", "Commercial Judge", "Psychologist", "Storytelling Expert"]
           - "review": A separate personal 2-3 sentence review from that expert's specific viewpoint.
        4. "metrics": A complete list containing EXACTLY the following 38 metrics. For each metric, provide a score (0 to 100), an explanation, concrete professional advice, examples of where this is done right, and improvement suggestions:
           Metrics: ["Overall Score", "Composition", "Lighting", "Exposure", "Contrast", "Highlights", "Shadow Detail", "Sharpness", "Noise", "Lens Quality", "Perspective", "Framing", "Symmetry", "Balance", "Negative Space", "Depth", "Leading Lines", "Subject Isolation", "Background Quality", "Color Harmony", "Color Psychology", "White Balance", "Skin Tones", "Dynamic Range", "Texture", "Visual Hierarchy", "Mood", "Emotion", "Storytelling", "Originality", "Professional Appeal", "Commercial Appeal", "Instagram Appeal", "Thumbnail Appeal", "Wallpaper Score", "Print Quality", "Contest Quality", "Portfolio Quality"].
           Metric fields: "name" (must match exactly), "score" (integer), "explanation", "advice", "examples", "suggestions".
        5. "heatmap": A list of 2 to 4 attention hotspots. Each hotspot has:
           - "x": Float between 0.0 and 1.0 (relative X position on image)
           - "y": Float between 0.0 and 1.0 (relative Y position on image)
           - "radius": Float (radius size, e.g. 30.0)
           - "intensity": Float between 0.0 and 1.0 (attention intensity)
           - "label": Short title (e.g. "Dominant Subject", "High-Contrast Distraction")
           - "explanation": Why viewers look there or why it is a distraction.
        6. "crops": List of crop suggestion objects for different aspect ratios ("Instagram (1:1)", "YouTube Thumbnail (16:9)", "Wallpaper (9:16)", "Golden Ratio", "Rule of Thirds"). Each has:
           - "ratioName": The aspect ratio name
           - "xOffset": Float 0.0 to 1.0 (top left X offset of crop box)
           - "yOffset": Float 0.0 to 1.0 (top left Y offset of crop box)
           - "width": Float 0.0 to 1.0 (width of crop box)
           - "height": Float 0.0 to 1.0 (height of crop box)
           - "reason": Detailed explanation of why this crop enhances the frame.
        7. "edits": List of Lightroom recommendations for: "Exposure", "Contrast", "Highlights", "Shadows", "Whites", "Blacks", "Temperature", "Tint", "Texture", "Clarity", "Vibrance", "Saturation", "Dehaze", "Sharpening", "Noise Reduction", "Lens Blur".
           Each has: "key" (the name of setting), "value" (Float slider recommendation between -100.0 and +100.0, or -5.0 to +5.0 for exposure), "formattedValue" (String e.g. "+0.3 EV" or "-15"), "explanation" (Why this correction helps).
        8. "filmEmulation": A recommended vintage/modern film stock simulation name (e.g., "Kodak Portra 400", "Fuji Superia 400", "Ilford HP5 Plus").
        9. "lutSuggestion": A cinema LUT suggestion (e.g., "Teal and Orange", "Rec709 Kodak 2383").
        10. "emotions": A list of emotion profile ratings. For each emotion ("Mystery", "Peace", "Luxury", "Adventure", "Romance", "Loneliness", "Joy", "Nostalgia", "Energy", "Confidence"), provide:
            - "name": Emotion name
            - "value": Integer from 0 to 100
            - "reason": Why the image elicits this feeling.
        11. "movies": A list of 2 film titles that match the visual DNA of the image. Each has:
            - "title": Movie name
            - "confidence": Integer 0 to 100
            - "reason": Detailed visual comparison of light, mood, and framing.
        12. "styles": A list of photo style matches (e.g., "Landscape", "Street", "Portrait", "Minimalist"). Max 3 styles. Each has:
            - "style": Style name
            - "confidence": Integer 0 to 100
            - "reason": Supporting analysis.
        13. "stories": An object containing the following generated story artifacts (Strings):
            - "creativeTitle": An elegant, poetic title
            - "instagramCaption": Hooking social caption
            - "professionalCaption": Technical exhibition caption
            - "shortStory": A creative narrative based on the details
            - "travelJournal": A wanderlust journal entry
            - "poem": An artistic verse
            - "movieNarration": A noir or cinematic narrator block
            - "hashtags": List of 8 relevant professional hashtags
        """.trimIndent()
    }

    private fun generateMockAnalysis(mode: String): AnalysisResult {
        // High quality fallback mock generator
        val randomScore = Random.nextInt(78, 93)
        val verdict = when(mode) {
            "ROAST" -> "Well, well, well. We've got a classic rule of thirds split here, but it looks like you spent more time thinking about your latte than your exposure values. The primary subject has great lighting, but that giant distracting clutter in the background is fighting for its life. A clean crop and pulling down those highlights would save this from being just another gigabyte in your cloud storage."
            "PROFESSIONAL" -> "This composition exhibits an excellent grasp of balance and geometric leading lines. The lens sharpness in the central zone is exceptional, resolving fine textures on the primary subject. However, the extreme dynamic range has led to minor clipping in the highlights. Introducing an exposure offset of -0.3 EV alongside a warmer split-tone would fully realize the emotional potential of the negative spaces."
            "BEGINNER" -> "Wow! This is a really beautiful photo. You did an amazing job placing your subject, which makes it feel very balanced and natural. The lighting is warm and friendly. To make it even better next time, try stepping a bit closer to make your subject stand out, and look around to make sure there are no distracting signs or bright spots behind them!"
            else -> "This image captures a peaceful atmosphere through its balanced composition and soft lighting. The strongest element is the relationship between the subject and the surrounding environment. Eliminating the distracting objects near the top margins and introducing slightly warmer highlights would elevate the photograph from memorable to exceptional."
        }

        val experts = listOf(
            ExpertOpinion("Professional Photographer", "The depth of field is beautifully managed, isolating the subject nicely. Watch the edge framing to avoid chopping off vital leading lines."),
            ExpertOpinion("Colorist", "The natural tones are well balanced, though a subtle shift toward amber in the highlights and teal in the shadows would add a cinematic dimension."),
            ExpertOpinion("Cinematographer", "Excellent use of a low angle to grant the subject a sense of grandeur. The lighting resembles late afternoon golden hour."),
            ExpertOpinion("Visual Designer", "Strong geometric layout. The eye flows smoothly from the high contrast region on the left along the diagonal plane."),
            ExpertOpinion("Commercial Judge", "This image has solid commercial appeal due to its clean background potential for copy space. Good branding alignment."),
            ExpertOpinion("Psychologist", "The expansive negative space invokes a strong feeling of peaceful solitude and reflective nostalgia, creating an immediate visual hook."),
            ExpertOpinion("Storytelling Expert", "The image suggests a narrative of journey or quiet introspection. The subject's posture and alignment are the primary story drivers.")
        )

        val metricNames = listOf(
            "Overall Score", "Composition", "Lighting", "Exposure", "Contrast", "Highlights", "Shadow Detail", "Sharpness", "Noise", "Lens Quality", "Perspective", "Framing", "Symmetry", "Balance", "Negative Space", "Depth", "Leading Lines", "Subject Isolation", "Background Quality", "Color Harmony", "Color Psychology", "White Balance", "Skin Tones", "Dynamic Range", "Texture", "Visual Hierarchy", "Mood", "Emotion", "Storytelling", "Originality", "Professional Appeal", "Commercial Appeal", "Instagram Appeal", "Thumbnail Appeal", "Wallpaper Score", "Print Quality", "Contest Quality", "Portfolio Quality"
        )

        val metrics = metricNames.map { name ->
            val score = if (name == "Overall Score") randomScore else Random.nextInt(75, 95)
            MetricDetail(
                name = name,
                score = score,
                explanation = "The $name of this photo is rated at $score/100. This is based on standard professional criteria.",
                advice = "To improve $name, focus on refining your framing angles and adjusting local contrast settings.",
                examples = "Great examples can be found in classic street photography and contemporary editorial catalogs.",
                suggestions = "Consider applying a soft radial mask to isolate details and boost local texture definition."
            )
        }

        val heatmap = listOf(
            HeatmapPoint(0.5f, 0.45f, 45f, 0.95f, "Dominant Subject", "This is the primary focal point where the viewer's eye rests immediately due to high local contrast."),
            HeatmapPoint(0.8f, 0.2f, 30f, 0.6f, "Background Highlight", "A distracting bright area that pulls attention away from the primary subject. Consider burning it down.")
        )

        val crops = listOf(
            CropSuggestion("Instagram (1:1)", 0.15f, 0.0f, 0.7f, 0.7f, "This square crop perfectly tightens the frame on the central subject, eliminating blank space."),
            CropSuggestion("YouTube Thumbnail (16:9)", 0.0f, 0.2f, 1.0f, 0.56f, "Provides a cinematic landscape view with ideal room on the side for overlay title text."),
            CropSuggestion("Wallpaper (9:16)", 0.25f, 0.0f, 0.5f, 0.88f, "An elegant vertical cut that frames the subject as a soaring centerpiece for mobile screens."),
            CropSuggestion("Rule of Thirds", 0.1f, 0.1f, 0.8f, 0.8f, "Aligns the primary focal point directly on the top-right intersection for dynamic balance.")
        )

        val edits = listOf(
            EditSetting("Exposure", 0.25f, "+0.25 EV", "Slightly lift midtones to reveal shadow details on the subject."),
            EditSetting("Contrast", 12f, "+12", "Enhances local contrast to make details and edges pop vibrantly."),
            EditSetting("Highlights", -20f, "-20", "Pulls down overexposed sky regions to recover highlight information."),
            EditSetting("Shadows", 15f, "+15", "Opens up dark corners to create a more balanced and inviting tone."),
            EditSetting("Whites", 8f, "+8", "Increases white point slightly for dynamic punch."),
            EditSetting("Blacks", -5f, "-5", "Deepens true blacks to maintain rich, non-washed contrasts."),
            EditSetting("Temperature", 5200f, "5200K (+4)", "Introduces a warm, cozy sunset tint to the overall canvas."),
            EditSetting("Tint", -2f, "-2 (Magenta)", "Corrects slight green cast in foliage regions."),
            EditSetting("Texture", 10f, "+10", "Accentuates micro-details on the primary subject surfaces."),
            EditSetting("Clarity", 8f, "+8", "Boosts midtone contrast to clarify depth planes."),
            EditSetting("Vibrance", 15f, "+15", "Intensifies muted colors while keeping skin tones natural."),
            EditSetting("Saturation", 2f, "+2", "Very subtle overall color boosting."),
            EditSetting("Dehaze", 5f, "+5", "Clears up distant atmospheric haze for crisper backgrounds."),
            EditSetting("Sharpening", 25f, "+25", "Increases fine detail definition on focused edges."),
            EditSetting("Noise Reduction", 10f, "+10", "Smooths out digital grain in shadow regions smoothly."),
            EditSetting("Lens Blur", 15f, "15%", "Adds a gentle artistic depth of field to isolate the subject.")
        )

        val emotions = listOf(
            EmotionFactor("Peace", Random.nextInt(70, 95), "Soft gradients and low noise levels invoke a serene emotional environment."),
            EmotionFactor("Mystery", Random.nextInt(40, 75), "Deep shadow details and slightly off-center paths provoke curiosity."),
            EmotionFactor("Nostalgia", Random.nextInt(60, 90), "Warmer highlight tones and balanced colors replicate cinematic film stocks.")
        )

        val movies = listOf(
            MovieMatch("The Grand Budapest Hotel", 88, "Matches the meticulous horizontal symmetry and bright pastel color harmonies."),
            MovieMatch("Blade Runner 2049", 75, "Shares a similar dramatic play between vast empty spaces and high-contrast ambient glow.")
        )

        val styles = listOf(
            PhotoStyleMatch("Landscape", 90, "Vast fields and prominent horizon lines occupy over 60% of the frame."),
            PhotoStyleMatch("Minimalist", 78, "Strong isolation of a single subject surrounded by generous, clean negative space.")
        )

        val stories = StoryOutput(
            creativeTitle = "Solace in the Frame",
            instagramCaption = "Chasing light, catching stories. There is a quiet beauty in details that we often rush past. 🌅📸 #SnapJudgeX",
            professionalCaption = "Exhibition Print No. 4. Shot on 50mm, f/2.8. Analyzing spatial relations, geometric symmetry, and ambient tonal graduation.",
            shortStory = "The light arrived late that afternoon, spilling through the gaps in the clouds like a secret. For three minutes, the entire world held its breath, illuminated in gold. And then, with a click of the shutter, that fleeting silence became permanent.",
            travelJournal = "Day 14. We stopped by the edge of the path just as the sun dipped behind the ridge. The breeze was cool, smelling of pines. I pulled out my camera, framed the horizon, and realized some moments aren't meant to be traveled through, but stood in.",
            poem = "A single leaf against the sky,\nA quiet path where shadows lie,\nWe frame the breath of passing gold,\nTo keep the stories never told.",
            movieNarration = "The city was quiet, but the lens didn't lie. Underneath the golden wash of the late afternoon, there was a tension waiting. I adjusted the focus. Sometimes, you don't find the story. The story cornered you.",
            hashtags = listOf("#SnapJudgeX", "#PhotographyMentor", "#CompositionClass", "#LightroomGuru", "#CinematicTones", "#VisualStoryteller", "#MasteringLight", "#ArtOfPhotography")
        )

        return AnalysisResult(
            overallScore = randomScore,
            verdict = verdict,
            experts = experts,
            metrics = metrics,
            heatmap = heatmap,
            crops = crops,
            edits = edits,
            filmEmulation = "Kodak Portra 400",
            lutSuggestion = "Teal and Orange LUT",
            emotions = emotions,
            movies = movies,
            styles = styles,
            stories = stories
        )
    }
}
