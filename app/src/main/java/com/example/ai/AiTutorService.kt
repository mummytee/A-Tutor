@file:OptIn(com.google.firebase.ai.type.PublicPreviewAPI::class)

package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.BoardElement
import com.example.data.BoardType
import com.example.data.ChatMessage
import com.example.data.FlashcardItem
import com.example.data.Part3DParams
import com.example.data.Part3DSpec
import com.example.data.QuizQuestion
import com.example.data.Scene3DSpec
import com.example.data.ThreeDPresets
import com.example.data.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.LiveGenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import java.util.UUID

private const val TAG = "AiTutorService"

data class TutorTurnEvent(
    val partialText: String = "",
    val finalText: String? = null,
    val groundingSources: List<String> = emptyList(),
    val updatedBoardType: BoardType? = null,
    val updatedBoardElements: List<BoardElement>? = null,
    val updatedScene3dJson: String? = null,
    val addedMemoryNote: String? = null
)

data class GeneratedQuizPayload(
    val title: String,
    val questions: List<QuizQuestion>
)

data class GeneratedDeckPayload(
    val title: String,
    val cards: List<FlashcardItem>
)

data class GeneratedLessonPayload(
    val overview: String,
    val sections: List<Pair<String, String>>,
    val chalkboardTitle: String,
    val boardType: BoardType,
    val boardElements: List<BoardElement>
)

data class TtsAudioResult(
    val bytes: ByteArray,
    val mimeType: String
)

class AiTutorService {

    companion object {
        const val MODEL_DEFAULT = "gemini-3.8-flash"
        const val MODEL_FAST = "gemini-3.5-flash-lite"
        const val MODEL_REASONING = "gemini-4-argon"
        const val MODEL_HYDROGEN = "gemini-4-hydrogen"
        const val MODEL_TTS = "gemini-3.1-flash-tts-preview"
        const val MODEL_TTS_FALLBACK = "gemini-3.8-flash-tts"
        const val MODEL_TRANSCRIBE = "gemini-3.5-transcribe"
        const val MODEL_LIVE = "gemini-3.1-flash-live-preview"
        const val MODEL_LIVE_FALLBACK = "gemini-2.5-flash-native-audio-preview-12-2025"
        const val MODEL_IMAGE = "gemini-nano-banana-2.1"

        val AVAILABLE_TUTOR_MODELS = listOf(
            "gemini-3.8-flash" to "Gemini 3.8 Flash (Balanced)",
            "gemini-4-hydrogen" to "Gemini 4 Hydrogen (Grounded)",
            "gemini-4-argon" to "Gemini 4 Argon (Deep STEM)",
            "gemini-3.5-flash-lite" to "Gemini 3.5 Flash-Lite (Ultra Fast)"
        )
    }

    private val chalkboardToolDeclaration = FunctionDeclaration(
        name = "update_chalkboard",
        description = "Draws, updates, highlights, or reorganizes visual teaching elements on the student's interactive chalkboard to explain concepts, formulas, diagrams, graphs, or mind maps.",
        parameters = mapOf(
            "boardType" to Schema.enumeration(
                values = listOf("GENERAL", "FREEHAND", "MATH", "SCIENCE", "MINDMAP", "GRAPH"),
                description = "Board template mode matching the lesson visual."
            ),
            "replaceExisting" to Schema.boolean(
                description = "True to replace current board items with a clean layout; false to append to existing items."
            ),
            "elements" to Schema.array(
                description = "Visual elements to draw on the chalkboard using normalized 0.0 to 1.0 coordinates.",
                items = Schema.obj(
                    properties = mapOf(
                        "kind" to Schema.enumeration(
                            values = listOf("FORMULA", "LABEL", "NODE", "RECT", "CIRCLE", "ARROW", "LINE", "GRAPH_CURVE"),
                            description = "Type of element. Use GRAPH_CURVE with text like 'x^2' or 'sin(x)' on GRAPH boards."
                        ),
                        "text" to Schema.string(description = "Equation, label, node text, or curve formula."),
                        "x" to Schema.double(description = "Normalized X coordinate 0.05..0.95."),
                        "y" to Schema.double(description = "Normalized Y coordinate 0.05..0.95."),
                        "x2" to Schema.double(description = "End X coordinate or width coordinate 0.05..0.95."),
                        "y2" to Schema.double(description = "End Y coordinate or height coordinate 0.05..0.95."),
                        "colorHex" to Schema.string(description = "Chalk hex: #F4F3EE (white), #F4B860 (gold), #5CE0D2 (mint), #FF8A75 (coral), #C5B4FF (lavender)."),
                        "highlighted" to Schema.boolean(description = "Whether to emphasize with a glowing highlight.")
                    ),
                    optionalProperties = listOf("x2", "y2", "colorHex", "highlighted")
                )
            )
        ),
        optionalParameters = listOf("replaceExisting")
    )

    private val scene3dToolDeclaration = FunctionDeclaration(
        name = "update_3d_scene",
        description = "Builds or updates an interactive 3D educational model (molecules, atoms, cells, planetary orreries, mechanical assemblies, 3D geometry, anatomical/engineering models) in the student's 3D Model Lab.",
        parameters = mapOf(
            "title" to Schema.string(description = "Concise title of the 3D model."),
            "subtitle" to Schema.string(description = "Short educational subtitle explaining what is shown."),
            "backgroundHex" to Schema.string(description = "Dark studio background hex color, e.g. #0E221F or #0D1E22."),
            "floating" to Schema.boolean(description = "True for zero-gravity/molecular/astronomical scenes; false for tabletop grounded models."),
            "parts" to Schema.array(
                description = "List of 3D mesh parts composing the educational model (usually 4 to 16 parts).",
                items = Schema.obj(
                    properties = mapOf(
                        "type" to Schema.enumeration(
                            values = listOf("sphere", "box", "bevel_box", "cylinder", "cone", "torus", "ring", "icosahedron", "lathe", "tube", "helix"),
                            description = "3D primitive or procedural geometry type."
                        ),
                        "label" to Schema.string(description = "Interactive tap-to-inspect part label."),
                        "description" to Schema.string(description = "Educational note shown when the student taps this 3D part."),
                        "colorHex" to Schema.string(description = "Base material hex color, e.g. #5CE0D2, #F4B860, #FF6B5B, #C5B4FF."),
                        "roughness" to Schema.double(description = "PBR roughness 0.05..0.95."),
                        "metalness" to Schema.double(description = "PBR metalness 0.0..1.0."),
                        "opacity" to Schema.double(description = "Opacity 0.1..1.0 (use < 1.0 for membranes or glass housings)."),
                        "emissiveHex" to Schema.string(description = "Optional self-glow hex color."),
                        "emissiveIntensity" to Schema.double(description = "Glow intensity 0.0..1.5."),
                        "position" to Schema.array(items = Schema.double(), description = "[x, y, z] world position around origin (~ -2.5..2.5)."),
                        "rotation" to Schema.array(items = Schema.double(), description = "[rx, ry, rz] Euler radians."),
                        "scale" to Schema.array(items = Schema.double(), description = "[sx, sy, sz] scale multipliers."),
                        "animation" to Schema.enumeration(values = listOf("none", "spinY", "orbitY", "bob", "pulse")),
                        "orbitSpeed" to Schema.double(description = "Angular speed multiplier for orbitY."),
                        "params" to Schema.obj(
                            properties = mapOf(
                                "radius" to Schema.double(),
                                "radiusTop" to Schema.double(),
                                "radiusBottom" to Schema.double(),
                                "width" to Schema.double(),
                                "height" to Schema.double(),
                                "depth" to Schema.double(),
                                "tube" to Schema.double(),
                                "turns" to Schema.double(),
                                "phase" to Schema.double(),
                                "detail" to Schema.integer()
                            ),
                            optionalProperties = listOf("radius", "radiusTop", "radiusBottom", "width", "height", "depth", "tube", "turns", "phase", "detail")
                        ),
                        "points" to Schema.array(items = Schema.array(items = Schema.double()), description = "3D polyline control points [[x,y,z],...] for tube type."),
                        "profile" to Schema.array(items = Schema.array(items = Schema.double()), description = "2D lathe profile [[radius,y],...] for lathe surfaces of revolution.")
                    ),
                    optionalProperties = listOf(
                        "description", "roughness", "metalness", "opacity", "emissiveHex",
                        "emissiveIntensity", "position", "rotation", "scale", "animation",
                        "orbitSpeed", "params", "points", "profile"
                    )
                )
            )
        ),
        optionalParameters = listOf("subtitle", "backgroundHex", "floating")
    )

    private val memoryToolDeclaration = FunctionDeclaration(
        name = "save_memory_note",
        description = "Saves a concise insight about the student's learning pace, strengths, or struggle areas to persistent AI memory.",
        parameters = mapOf(
            "note" to Schema.string(description = "Concise memory note under 180 characters.")
        )
    )

    private fun buildSystemPrompt(
        profile: UserProfile?,
        subject: String,
        topic: String,
        currentBoardType: BoardType,
        currentBoard: List<BoardElement>
    ): String {
        val level = profile?.learningLevel ?: "Intermediate"
        val style = profile?.learningStyle ?: "Visual"
        val memory = profile?.memoryNotes?.joinToString("; ")?.ifBlank { "None recorded yet" } ?: "None"
        val boardSummary = if (currentBoard.isEmpty()) {
            "Empty"
        } else {
            currentBoard.take(12).joinToString(" | ") { "${it.kind}: '${it.text}' @ (${"%.2f".format(it.x)},${"%.2f".format(it.y)})" }
        }
        return """
            You are A-Tutor, an empathetic, deeply knowledgeable personal human teacher.
            Subject: $subject | Topic: ${topic.ifBlank { "General Study" }}
            Student Level: $level | Preferred Style: $style
            Persistent AI Memory: $memory
            Current Chalkboard Mode: ${currentBoardType.name} | Current Board Elements: $boardSummary

            Teaching guidelines:
            - Explain complex ideas in clear, warm, accessible language tailored to $level level and $style style.
            - Break explanations into logical steps with concrete analogies, real-world applications, and check-in questions.
            - Actively use update_chalkboard whenever a 2D diagram, step-by-step equation, flowchart, mind map, or coordinate graph will make the explanation clearer!
            - Actively use update_3d_scene whenever the student asks for a 3D model or when a 3D structure (atoms, molecules, DNA/cells, solids of revolution, planetary orbits, gears/circuits) makes the concept spatial and tactile! Give each part a clear educational label and description so the student can tap parts to inspect them.
            - Use save_memory_note when you discover a meaningful strength, misconception, or preference to remember across sessions.
        """.trimIndent()
    }

    fun sendTutorMessageStream(
        modelName: String,
        useSearchAndMapsGrounding: Boolean,
        profile: UserProfile?,
        subject: String,
        topic: String,
        history: List<ChatMessage>,
        userPrompt: String,
        attachedImage: Bitmap?,
        currentBoardType: BoardType,
        currentBoard: List<BoardElement>
    ): Flow<TutorTurnEvent> = flow {
        val systemInstruction = content {
            text(buildSystemPrompt(profile, subject, topic, currentBoardType, currentBoard))
        }

        val effectiveModelName = if (useSearchAndMapsGrounding) {
            MODEL_HYDROGEN
        } else {
            modelName.ifBlank { MODEL_DEFAULT }
        }

        val toolsList = if (useSearchAndMapsGrounding) {
            listOf(Tool.googleSearch(), Tool.googleMaps())
        } else {
            listOf(
                Tool.functionDeclarations(
                    listOf(chalkboardToolDeclaration, scene3dToolDeclaration, memoryToolDeclaration)
                )
            )
        }

        val model = try {
            Firebase.ai.generativeModel(
                modelName = effectiveModelName,
                tools = toolsList,
                systemInstruction = systemInstruction
            )
        } catch (e: Exception) {
            Firebase.ai.generativeModel(
                modelName = MODEL_DEFAULT,
                tools = toolsList,
                systemInstruction = systemInstruction
            )
        }

        val sdkHistory = history.takeLast(14).map { msg ->
            content(role = if (msg.role == "model") "model" else "user") {
                text(msg.text.ifBlank { " " })
            }
        }

        val chat = model.startChat(history = sdkHistory)
        val userContent = content("user") {
            if (attachedImage != null) {
                image(attachedImage)
            }
            text(userPrompt)
        }

        var workingBoardType: BoardType? = null
        var workingBoardElements: List<BoardElement>? = null
        var workingScene3dJson: String? = null
        var memoryNote: String? = null
        val accumulated = StringBuilder()
        val groundingLinks = linkedSetOf<String>()

        withTimeout(60_000L) {
            if (useSearchAndMapsGrounding) {
                model.generateContentStream(sdkHistory + userContent).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulated.append(chunkText)
                        emit(TutorTurnEvent(partialText = accumulated.toString()))
                    }
                    chunk.candidates.firstOrNull()?.groundingMetadata?.groundingChunks?.forEach { gc ->
                        gc.web?.let { web ->
                            val title = web.title?.takeIf { it.isNotBlank() }
                                ?: web.domain?.takeIf { it.isNotBlank() }
                                ?: "Web Source"
                            val uri = web.uri.orEmpty()
                            if (uri.isNotBlank()) groundingLinks.add("$title | $uri")
                        }
                        gc.maps?.let { maps ->
                            val title = maps.title?.takeIf { it.isNotBlank() } ?: "Google Maps Location"
                            val uri = maps.uri.orEmpty()
                            if (uri.isNotBlank()) groundingLinks.add("Map: $title | $uri")
                        }
                    }
                }
            } else {
                var response = chat.sendMessage(userContent)
                var toolHops = 0
                while (response.functionCalls.isNotEmpty() && toolHops < 3) {
                    toolHops++
                    val toolResponses = mutableListOf<FunctionResponsePart>()
                    for (call in response.functionCalls) {
                        when (call.name) {
                            "update_chalkboard" -> {
                                val args = call.args
                                val typeStr = (args["boardType"] as? JsonPrimitive)?.content
                                val parsedType = BoardType.fromRaw(typeStr)
                                val replace = (args["replaceExisting"] as? JsonPrimitive)?.booleanOrNull ?: true
                                val parsedElements = parseBoardElements(args["elements"] as? JsonArray)
                                val merged = if (replace) parsedElements else (workingBoardElements ?: currentBoard) + parsedElements
                                workingBoardType = parsedType
                                workingBoardElements = merged.take(80)
                                toolResponses.add(
                                    FunctionResponsePart(
                                        name = call.name,
                                        response = JsonObject(
                                            mapOf(
                                                "status" to JsonPrimitive("applied"),
                                                "elementCount" to JsonPrimitive(merged.size)
                                            )
                                        )
                                    )
                                )
                            }
                            "update_3d_scene" -> {
                                val sceneSpec = parseScene3dCall(call.args)
                                workingScene3dJson = ThreeDPresets.toJson(sceneSpec)
                                toolResponses.add(
                                    FunctionResponsePart(
                                        name = call.name,
                                        response = JsonObject(
                                            mapOf(
                                                "status" to JsonPrimitive("applied"),
                                                "partCount" to JsonPrimitive(sceneSpec.parts.size)
                                            )
                                        )
                                    )
                                )
                            }
                            "save_memory_note" -> {
                                val note = (call.args["note"] as? JsonPrimitive)?.content?.trim()
                                if (!note.isNullOrBlank()) {
                                    memoryNote = note.take(240)
                                }
                                toolResponses.add(
                                    FunctionResponsePart(
                                        name = call.name,
                                        response = JsonObject(mapOf("saved" to JsonPrimitive(true)))
                                    )
                                )
                            }
                        }
                    }
                    if (toolResponses.isNotEmpty()) {
                        val toolContent = Content(
                            role = "function",
                            parts = toolResponses
                        )
                        response = chat.sendMessage(toolContent)
                    } else break
                }

                val text = response.text.orEmpty()
                accumulated.append(text)
            }
        }

        val finalStr = accumulated.toString().ifBlank {
            when {
                workingScene3dJson != null ->
                    "I've built an interactive 3D model in your 3D Model Lab! Drag to orbit, pinch to zoom, and tap any part to inspect its role."
                workingBoardElements != null ->
                    "I've updated your interactive chalkboard to illustrate this step-by-step. Tap any element on the board to explore or ask a follow-up!"
                else ->
                    "Let's work through this together—what part would you like to examine first?"
            }
        }

        emit(
            TutorTurnEvent(
                partialText = finalStr,
                finalText = finalStr,
                groundingSources = groundingLinks.toList(),
                updatedBoardType = workingBoardType,
                updatedBoardElements = workingBoardElements,
                updatedScene3dJson = workingScene3dJson,
                addedMemoryNote = memoryNote
            )
        )
    }

    private fun parseScene3dCall(args: Map<String, JsonElement>): Scene3DSpec {
        val title = (args["title"] as? JsonPrimitive)?.content?.take(120) ?: "Interactive 3D Model"
        val subtitle = (args["subtitle"] as? JsonPrimitive)?.content?.take(200)
            ?: "Drag to orbit • Pinch to zoom • Tap any part"
        val backgroundHex = (args["backgroundHex"] as? JsonPrimitive)?.content ?: "#0E221F"
        val floating = (args["floating"] as? JsonPrimitive)?.booleanOrNull ?: false
        val partsArray = args["parts"] as? JsonArray
        val parts = partsArray?.mapNotNull { item ->
            val obj = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
            val paramsObj = runCatching { obj["params"]?.jsonObject }.getOrNull()
            fun doubleList(key: String, defaultVal: List<Double>): List<Double> {
                val arr = obj[key] as? JsonArray ?: return defaultVal
                val parsed = arr.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }
                return if (parsed.size >= 3) parsed.take(3) else defaultVal
            }
            fun nestedPoints(key: String): List<List<Double>>? {
                val arr = obj[key] as? JsonArray ?: return null
                return arr.mapNotNull { row ->
                    val r = row as? JsonArray ?: return@mapNotNull null
                    r.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }
                }.takeIf { it.isNotEmpty() }
            }
            Part3DSpec(
                type = (obj["type"] as? JsonPrimitive)?.content ?: "sphere",
                label = (obj["label"] as? JsonPrimitive)?.content?.take(60) ?: "Part",
                description = (obj["description"] as? JsonPrimitive)?.content?.take(220) ?: "",
                colorHex = (obj["colorHex"] as? JsonPrimitive)?.content ?: "#5CE0D2",
                roughness = (obj["roughness"] as? JsonPrimitive)?.doubleOrNull?.coerceIn(0.05, 1.0) ?: 0.42,
                metalness = (obj["metalness"] as? JsonPrimitive)?.doubleOrNull?.coerceIn(0.0, 1.0) ?: 0.15,
                opacity = (obj["opacity"] as? JsonPrimitive)?.doubleOrNull?.coerceIn(0.08, 1.0) ?: 1.0,
                emissiveHex = (obj["emissiveHex"] as? JsonPrimitive)?.content,
                emissiveIntensity = (obj["emissiveIntensity"] as? JsonPrimitive)?.doubleOrNull?.coerceIn(0.0, 2.0) ?: 0.0,
                position = doubleList("position", listOf(0.0, 0.0, 0.0)),
                rotation = doubleList("rotation", listOf(0.0, 0.0, 0.0)),
                scale = doubleList("scale", listOf(1.0, 1.0, 1.0)),
                animation = (obj["animation"] as? JsonPrimitive)?.content ?: "none",
                orbitSpeed = (obj["orbitSpeed"] as? JsonPrimitive)?.doubleOrNull ?: 0.55,
                params = Part3DParams(
                    radius = (paramsObj?.get("radius") as? JsonPrimitive)?.doubleOrNull,
                    radiusTop = (paramsObj?.get("radiusTop") as? JsonPrimitive)?.doubleOrNull,
                    radiusBottom = (paramsObj?.get("radiusBottom") as? JsonPrimitive)?.doubleOrNull,
                    width = (paramsObj?.get("width") as? JsonPrimitive)?.doubleOrNull,
                    height = (paramsObj?.get("height") as? JsonPrimitive)?.doubleOrNull,
                    depth = (paramsObj?.get("depth") as? JsonPrimitive)?.doubleOrNull,
                    tube = (paramsObj?.get("tube") as? JsonPrimitive)?.doubleOrNull,
                    turns = (paramsObj?.get("turns") as? JsonPrimitive)?.doubleOrNull,
                    phase = (paramsObj?.get("phase") as? JsonPrimitive)?.doubleOrNull,
                    detail = (paramsObj?.get("detail") as? JsonPrimitive)?.content?.toIntOrNull()
                ),
                points = nestedPoints("points"),
                profile = nestedPoints("profile")
            )
        }?.take(28) ?: emptyList()
        return Scene3DSpec(
            title = title,
            subtitle = subtitle,
            backgroundHex = backgroundHex,
            floating = floating,
            parts = parts
        )
    }

    private fun parseBoardElements(jsonArray: JsonArray?): List<BoardElement> {
        if (jsonArray == null) return emptyList()
        return jsonArray.mapNotNull { el ->
            val obj = el.jsonObject
            val kind = obj["kind"]?.jsonPrimitive?.content ?: "LABEL"
            val text = obj["text"]?.jsonPrimitive?.content ?: ""
            val x = obj["x"]?.jsonPrimitive?.doubleOrNull ?: 0.5
            val y = obj["y"]?.jsonPrimitive?.doubleOrNull ?: 0.5
            val x2 = obj["x2"]?.jsonPrimitive?.doubleOrNull ?: (x + 0.18).coerceAtMost(0.95)
            val y2 = obj["y2"]?.jsonPrimitive?.doubleOrNull ?: (y + 0.12).coerceAtMost(0.95)
            val colorHex = obj["colorHex"]?.jsonPrimitive?.content ?: "#F4F3EE"
            val highlighted = obj["highlighted"]?.jsonPrimitive?.booleanOrNull ?: false
            BoardElement(
                id = UUID.randomUUID().toString().take(8),
                kind = kind,
                text = text,
                x = x,
                y = y,
                x2 = x2,
                y2 = y2,
                colorHex = colorHex,
                highlighted = highlighted
            )
        }
    }

    suspend fun generateQuickHints(subject: String, topic: String): List<String> {
        return try {
            val schema = Schema.obj(
                properties = mapOf(
                    "prompts" to Schema.array(
                        items = Schema.string(),
                        description = "3 short, inspiring student follow-up questions."
                    )
                )
            )
            val model = Firebase.ai.generativeModel(
                modelName = MODEL_FAST,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = schema
                }
            )
            val res = withTimeout(30_000L) {
                model.generateContent(
                    "Suggest 3 short follow-up study prompts (max 8 words each) for a student studying $subject ($topic)."
                )
            }
            val json = JSONObject(res.text ?: "{}")
            val arr = json.optJSONArray("prompts") ?: return emptyList()
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }.take(3)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Quick hints fallback: ${e.message}")
            emptyList()
        }
    }

    suspend fun explainChalkboardSnapshot(
        boardBitmap: Bitmap,
        subject: String,
        studentQuestion: String
    ): String {
        val model = Firebase.ai.generativeModel(modelName = MODEL_DEFAULT)
        val prompt = if (studentQuestion.isBlank()) {
            "Look at this student's $subject chalkboard. Review their work, praise what is right, point out any mistake kindly, and suggest the next step."
        } else {
            "Regarding this $subject chalkboard: $studentQuestion"
        }
        val response = withTimeout(60_000L) {
            model.generateContent(
                content {
                    image(boardBitmap)
                    text(prompt)
                }
            )
        }
        return response.text ?: "I reviewed your chalkboard—try labeling the key variables and let's check the next step!"
    }

    suspend fun generateQuiz(
        subject: String,
        topic: String,
        difficulty: String,
        questionCount: Int,
        learningLevel: String
    ): GeneratedQuizPayload {
        val quizSchema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Quiz title"),
                "questions" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "prompt" to Schema.string(),
                            "type" to Schema.enumeration(listOf("MCQ", "SHORT_ANSWER")),
                            "options" to Schema.array(items = Schema.string()),
                            "correctIndex" to Schema.integer(),
                            "expectedAnswer" to Schema.string(),
                            "explanation" to Schema.string()
                        )
                    )
                )
            )
        )
        val model = Firebase.ai.generativeModel(
            modelName = MODEL_DEFAULT,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = quizSchema
            }
        )
        val response = withTimeout(60_000L) {
            model.generateContent(
                "Create a $difficulty $subject quiz on '$topic' for a $learningLevel student with $questionCount questions. Mix MCQ (4 options, correctIndex 0..3) and SHORT_ANSWER (empty options, clear concise expectedAnswer). Include thorough teaching explanations."
            )
        }
        val json = JSONObject(response.text ?: error("Empty quiz response"))
        val title = json.optString("title", "$subject: $topic Quiz")
        val qArr = json.getJSONArray("questions")
        val questions = (0 until qArr.length()).map { idx ->
            val q = qArr.getJSONObject(idx)
            val optsArr = q.optJSONArray("options")
            val opts = if (optsArr != null) {
                (0 until optsArr.length()).map { optsArr.getString(it) }
            } else emptyList()
            QuizQuestion(
                prompt = q.getString("prompt"),
                type = q.optString("type", "MCQ"),
                options = opts,
                correctIndex = q.optLong("correctIndex", 0L),
                expectedAnswer = q.optString("expectedAnswer", opts.firstOrNull() ?: ""),
                explanation = q.optString("explanation", "")
            )
        }
        return GeneratedQuizPayload(title = title, questions = questions)
    }

    suspend fun generateFlashcards(
        subject: String,
        topic: String,
        cardCount: Int,
        learningLevel: String
    ): GeneratedDeckPayload {
        val schema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string(),
                "cards" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "front" to Schema.string(description = "Question, term, or formula prompt"),
                            "back" to Schema.string(description = "Clear explanation or answer"),
                            "hint" to Schema.string(description = "Helpful memory cue")
                        )
                    )
                )
            )
        )
        val model = Firebase.ai.generativeModel(
            modelName = MODEL_DEFAULT,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
        val response = withTimeout(60_000L) {
            model.generateContent(
                "Generate $cardCount high-impact study flashcards for $subject on '$topic' tailored to a $learningLevel student."
            )
        }
        val json = JSONObject(response.text ?: error("Empty flashcards response"))
        val title = json.optString("title", "$topic Flashcards")
        val arr = json.getJSONArray("cards")
        val cards = (0 until arr.length()).map { idx ->
            val c = arr.getJSONObject(idx)
            FlashcardItem(
                id = "card_${idx + 1}",
                front = c.getString("front"),
                back = c.getString("back"),
                hint = c.optString("hint", ""),
                mastered = false
            )
        }
        return GeneratedDeckPayload(title = title, cards = cards)
    }

    suspend fun generateLessonPackage(
        subject: String,
        lessonTitle: String,
        learningLevel: String,
        learningStyle: String
    ): GeneratedLessonPayload {
        val schema = Schema.obj(
            properties = mapOf(
                "overview" to Schema.string(),
                "sections" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "heading" to Schema.string(),
                            "body" to Schema.string()
                        )
                    )
                ),
                "chalkboardTitle" to Schema.string(),
                "boardType" to Schema.enumeration(listOf("GENERAL", "MATH", "SCIENCE", "MINDMAP", "GRAPH")),
                "boardElements" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "kind" to Schema.enumeration(listOf("FORMULA", "LABEL", "NODE", "ARROW", "GRAPH_CURVE")),
                            "text" to Schema.string(),
                            "x" to Schema.double(),
                            "y" to Schema.double(),
                            "x2" to Schema.double(),
                            "y2" to Schema.double(),
                            "colorHex" to Schema.string(),
                            "highlighted" to Schema.boolean()
                        )
                    )
                )
            )
        )
        val model = Firebase.ai.generativeModel(
            modelName = MODEL_DEFAULT,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
        val response = withTimeout(60_000L) {
            model.generateContent(
                "Teach a complete interactive lesson on '$lessonTitle' in $subject for a $learningLevel student who learns best with a $learningStyle approach. Provide 4 rich structured sections (Core Idea, Visual/Worked Example, Real-World Application, Check Understanding) plus 4-6 clean normalized (0.1..0.9) chalkboard elements."
            )
        }
        val json = JSONObject(response.text ?: error("Empty lesson response"))
        val secArr = json.getJSONArray("sections")
        val sections = (0 until secArr.length()).map { i ->
            val s = secArr.getJSONObject(i)
            s.getString("heading") to s.getString("body")
        }
        val elArr = json.getJSONArray("boardElements")
        val elements = (0 until elArr.length()).map { i ->
            val e = elArr.getJSONObject(i)
            BoardElement(
                id = "lesson_${i + 1}",
                kind = e.optString("kind", "LABEL"),
                text = e.optString("text", ""),
                x = e.optDouble("x", 0.5),
                y = e.optDouble("y", 0.5),
                x2 = e.optDouble("x2", 0.7),
                y2 = e.optDouble("y2", 0.6),
                colorHex = e.optString("colorHex", "#F4B860"),
                highlighted = e.optBoolean("highlighted", false)
            )
        }
        return GeneratedLessonPayload(
            overview = json.getString("overview"),
            sections = sections,
            chalkboardTitle = json.optString("chalkboardTitle", lessonTitle),
            boardType = BoardType.fromRaw(json.optString("boardType", "GENERAL")),
            boardElements = elements
        )
    }

    suspend fun generateStudyGuideOrReport(
        title: String,
        subject: String,
        prompt: String
    ): Pair<String, String> {
        val schema = Schema.obj(
            properties = mapOf(
                "summary" to Schema.string(description = "2-sentence executive summary under 300 chars."),
                "content" to Schema.string(description = "Well-structured markdown-style study guide or analytical report.")
            )
        )
        val model = Firebase.ai.generativeModel(
            modelName = MODEL_DEFAULT,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
        val res = withTimeout(60_000L) {
            model.generateContent("Title: $title | Subject: $subject\n$prompt")
        }
        val json = JSONObject(res.text ?: error("Empty resource response"))
        return json.optString("summary", title).take(380) to json.getString("content")
    }

    suspend fun generateEducationalImage(prompt: String): Bitmap {
        val model = Firebase.ai.generativeModel(
            modelName = MODEL_IMAGE,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
        val response = withTimeout(60_000L) {
            model.generateContent("Clear, clean educational diagram illustration with legible visual structure: $prompt")
        }
        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()
        return imagePart?.image ?: error("No educational image was returned by the model.")
    }

    suspend fun synthesizeSpeech(text: String): TtsAudioResult {
        val cleanText = text.take(1400)
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.AUDIO)
            speechConfig = SpeechConfig(voice = Voice("Kore"))
        }
        val response = try {
            val primary = Firebase.ai.generativeModel(modelName = MODEL_TTS, generationConfig = config)
            withTimeout(45_000L) {
                primary.generateContent("Read this tutoring explanation clearly and naturally:\n$cleanText")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Primary TTS model fallback: ${e.message}")
            val fallback = Firebase.ai.generativeModel(modelName = MODEL_TTS_FALLBACK, generationConfig = config)
            withTimeout(45_000L) {
                fallback.generateContent("Read this tutoring explanation clearly and naturally:\n$cleanText")
            }
        }
        val audioPart = response.inlineDataParts.firstOrNull()
            ?: error("TTS model returned no audio output.")
        return TtsAudioResult(bytes = audioPart.inlineData, mimeType = audioPart.mimeType)
    }

    suspend fun transcribeAudio(wavBytes: ByteArray): String {
        val requestContent = content {
            inlineData(wavBytes, "audio/wav")
            text("Transcribe the student's spoken audio accurately into plain text. Return only the transcription.")
        }
        val response = try {
            val model = Firebase.ai.generativeModel(modelName = MODEL_TRANSCRIBE)
            withTimeout(45_000L) { model.generateContent(requestContent) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Transcribe model fallback: ${e.message}")
            val fallback = Firebase.ai.generativeModel(modelName = MODEL_DEFAULT)
            withTimeout(45_000L) { fallback.generateContent(requestContent) }
        }
        return response.text?.trim().orEmpty().ifBlank {
            error("Could not detect speech in recording.")
        }
    }

    fun createLiveTutorModel(
        profile: UserProfile?,
        subject: String,
        topic: String,
        useFallback: Boolean = false
    ): LiveGenerativeModel {
        val level = profile?.learningLevel ?: "Intermediate"
        val style = profile?.learningStyle ?: "Visual"
        val selectedLiveModel = if (useFallback) MODEL_LIVE_FALLBACK else MODEL_LIVE
        return Firebase.ai.liveModel(
            modelName = selectedLiveModel,
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                speechConfig = SpeechConfig(voice = Voice("Kore"))
                inputAudioTranscription = AudioTranscriptionConfig()
                outputAudioTranscription = AudioTranscriptionConfig()
            },
            systemInstruction = content {
                text(
                    "You are A-Tutor, a warm, encouraging real-time voice teacher for $subject ($topic). " +
                        "Adapt to a $level student who prefers $style explanations. Keep spoken turns conversational, clear, and interactive—pause to ask check-in questions."
                )
            }
        )
    }
}
