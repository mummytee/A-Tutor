package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class BoardType(val label: String, val description: String) {
    GENERAL("Whiteboard", "Clean teaching canvas for notes & sketches"),
    FREEHAND("Freehand Chalk", "Classic dark emerald chalkboard for free sketching"),
    MATH("Math Working", "Step-by-step formulas, derivations & proofs"),
    SCIENCE("Scientific Diagram", "Annotated system diagrams, circuits & molecules"),
    MINDMAP("Mind Map", "Connected concept nodes, flowcharts & links"),
    GRAPH("Coordinate Graph", "XY Cartesian axes, functions & vectors");

    companion object {
        fun fromRaw(raw: String?): BoardType =
            entries.firstOrNull { it.name == raw } ?: GENERAL
    }
}

enum class ResourceType(val label: String) {
    STUDY_GUIDE("Study Guide"),
    PROGRESS_REPORT("AI Progress Report"),
    LESSON_NOTES("Lesson Notes"),
    CONCEPT_MAP("Concept Map"),
    INFOGRAPHIC("Visual Summary");

    companion object {
        fun fromRaw(raw: String?): ResourceType =
            entries.firstOrNull { it.name == raw } ?: STUDY_GUIDE
    }
}

enum class ProgressStatus(val label: String) {
    NOT_STARTED("Not Started"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed");

    companion object {
        fun fromRaw(raw: String?): ProgressStatus =
            entries.firstOrNull { it.name == raw } ?: NOT_STARTED
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Student",
    val email: String? = null,
    val photoUrl: String? = null,
    val learningLevel: String = "Intermediate",
    val learningStyle: String = "Visual",
    val dailyGoalMinutes: Long = 30L,
    val preferredModel: String = "gemini-3.8-flash",
    val memoryNotes: List<String> = emptyList(),
    val focusSubjects: List<String> = listOf("Mathematics", "Physics", "Computer Science"),
    val streakDays: Long = 1L,
    val totalMinutesStudied: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BoardElement(
    val id: String = "",
    val kind: String = "LABEL", // FORMULA, LABEL, RECT, CIRCLE, ARROW, LINE, NODE, GRAPH_CURVE, STROKE
    val text: String = "",
    val x: Double = 0.5,        // Normalized 0f..1f
    val y: Double = 0.5,        // Normalized 0f..1f
    val x2: Double = 0.7,       // End X for ARROW / LINE / RECT width
    val y2: Double = 0.6,       // End Y for ARROW / LINE / RECT height
    val colorHex: String = "#F4F3EE",
    val highlighted: Boolean = false,
    val points: List<Map<String, Double>> = emptyList() // For freehand strokes [{x:..., y:...}]
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "id" to id,
        "kind" to kind,
        "text" to text.take(400),
        "x" to x.coerceIn(0.0, 1.0),
        "y" to y.coerceIn(0.0, 1.0),
        "x2" to x2.coerceIn(0.0, 1.0),
        "y2" to y2.coerceIn(0.0, 1.0),
        "colorHex" to colorHex,
        "highlighted" to highlighted,
        "points" to points.take(120)
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): BoardElement {
            val rawPoints = (map["points"] as? List<*>)
                ?.mapNotNull { item ->
                    val pt = item as? Map<*, *> ?: return@mapNotNull null
                    val px = (pt["x"] as? Number)?.toDouble() ?: return@mapNotNull null
                    val py = (pt["y"] as? Number)?.toDouble() ?: return@mapNotNull null
                    mapOf("x" to px, "y" to py)
                } ?: emptyList()

            return BoardElement(
                id = map["id"] as? String ?: "",
                kind = map["kind"] as? String ?: "LABEL",
                text = map["text"] as? String ?: "",
                x = (map["x"] as? Number)?.toDouble() ?: 0.5,
                y = (map["y"] as? Number)?.toDouble() ?: 0.5,
                x2 = (map["x2"] as? Number)?.toDouble() ?: 0.7,
                y2 = (map["y2"] as? Number)?.toDouble() ?: 0.6,
                colorHex = map["colorHex"] as? String ?: "#F4F3EE",
                highlighted = map["highlighted"] as? Boolean ?: false,
                points = rawPoints
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val title: String = "",
    val subject: String = "Mathematics",
    val topic: String = "",
    val boardType: String = BoardType.GENERAL.name,
    val boardElements: List<Map<String, Any?>> = emptyList(),
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    val starred: Boolean = false,
    val archived: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedBoardType(): BoardType = BoardType.fromRaw(boardType)
    fun parsedBoardElements(): List<BoardElement> =
        boardElements.map { BoardElement.fromMap(it) }
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val role: String = "user",
    val text: String = "",
    val groundingSources: List<String> = emptyList(),
    val hasBoardAction: Boolean = false,
    val hasImageAttachment: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class QuizQuestion(
    val prompt: String = "",
    val type: String = "MCQ", // MCQ or SHORT_ANSWER
    val options: List<String> = emptyList(),
    val correctIndex: Long = 0L,
    val expectedAnswer: String = "",
    val explanation: String = ""
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "prompt" to prompt.take(500),
        "type" to type,
        "options" to options.take(6),
        "correctIndex" to correctIndex,
        "expectedAnswer" to expectedAnswer.take(300),
        "explanation" to explanation.take(600)
    )
}

@Keep
@IgnoreExtraProperties
data class QuizRecord(
    @DocumentId val id: String = "",
    val title: String = "",
    val subject: String = "",
    val topic: String = "",
    val difficulty: String = "Medium",
    val questions: List<Map<String, Any?>> = emptyList(),
    val completed: Boolean = false,
    val score: Long = 0L,
    val correctCount: Long = 0L,
    val totalCount: Long = 1L,
    val conversationId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @Suppress("UNCHECKED_CAST")
    fun parsedQuestions(): List<QuizQuestion> = questions.map { q ->
        val rawOptions = (q["options"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        QuizQuestion(
            prompt = q["prompt"] as? String ?: "",
            type = q["type"] as? String ?: "MCQ",
            options = rawOptions,
            correctIndex = (q["correctIndex"] as? Number)?.toLong() ?: 0L,
            expectedAnswer = q["expectedAnswer"] as? String ?: "",
            explanation = q["explanation"] as? String ?: ""
        )
    }
}

@Keep
@IgnoreExtraProperties
data class FlashcardItem(
    val id: String = "",
    val front: String = "",
    val back: String = "",
    val hint: String = "",
    val mastered: Boolean = false
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "id" to id,
        "front" to front.take(300),
        "back" to back.take(600),
        "hint" to hint.take(200),
        "mastered" to mastered
    )
}

@Keep
@IgnoreExtraProperties
data class FlashcardDeck(
    @DocumentId val id: String = "",
    val title: String = "",
    val subject: String = "",
    val topic: String = "",
    val cards: List<Map<String, Any?>> = emptyList(),
    val masteredCount: Long = 0L,
    val conversationId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedCards(): List<FlashcardItem> = cards.map { c ->
        FlashcardItem(
            id = c["id"] as? String ?: "",
            front = c["front"] as? String ?: "",
            back = c["back"] as? String ?: "",
            hint = c["hint"] as? String ?: "",
            mastered = c["mastered"] as? Boolean ?: false
        )
    }
}

@Keep
@IgnoreExtraProperties
data class StudyResource(
    @DocumentId val id: String = "",
    val title: String = "",
    val subject: String = "",
    val resourceType: String = ResourceType.STUDY_GUIDE.name,
    val content: String = "",
    val summary: String = "",
    val conversationId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedType(): ResourceType = ResourceType.fromRaw(resourceType)
}

@Keep
@IgnoreExtraProperties
data class TopicProgress(
    @DocumentId val id: String = "",
    val subject: String = "",
    val topicId: String = "",
    val topicTitle: String = "",
    val masteryPercent: Long = 0L,
    val minutesSpent: Long = 0L,
    val status: String = ProgressStatus.NOT_STARTED.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedStatus(): ProgressStatus = ProgressStatus.fromRaw(status)
}
