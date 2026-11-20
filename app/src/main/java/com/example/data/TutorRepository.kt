package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class TutorRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    // --- Profile & AI Memory ---

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        val path = "users/$uid"
        emitAll(
            userDoc(uid).snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(
        uid: String,
        displayName: String,
        email: String?,
        photoUrl: String?
    ): Result<UserProfile> {
        val path = "users/$uid"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "Authenticated user mismatch." }
            val ref = userDoc(uid)
            val snap = ref.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (existing != null) return Result.success(existing)
            }
            val cleanName = displayName.ifBlank { "Student" }.take(80)
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "displayName" to cleanName,
                "learningLevel" to "Intermediate",
                "learningStyle" to "Visual",
                "dailyGoalMinutes" to 30L,
                "preferredModel" to "gemini-3.8-flash",
                "memoryNotes" to listOf("Enjoys visual chalkboard breakdowns"),
                "focusSubjects" to listOf("Mathematics", "Physics", "Computer Science"),
                "streakDays" to 1L,
                "totalMinutesStudied" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!email.isNullOrBlank()) payload["email"] = email.take(120)
            if (!photoUrl.isNullOrBlank()) payload["photoUrl"] = photoUrl.take(500)
            ref.set(payload).await()
            val created = ref.get().await().toObject(
                UserProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: UserProfile(id = uid, userId = uid, displayName = cleanName)
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePreferences(
        uid: String,
        displayName: String,
        learningLevel: String,
        learningStyle: String,
        dailyGoalMinutes: Long,
        preferredModel: String,
        focusSubjects: List<String>
    ): Result<Unit> {
        val path = "users/$uid"
        return try {
            requireUserId()
            userDoc(uid).update(
                mapOf(
                    "displayName" to displayName.ifBlank { "Student" }.take(80),
                    "learningLevel" to learningLevel,
                    "learningStyle" to learningStyle,
                    "dailyGoalMinutes" to dailyGoalMinutes.coerceIn(5L, 300L),
                    "preferredModel" to preferredModel.take(80),
                    "focusSubjects" to focusSubjects.take(15),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMemoryNotes(uid: String, notes: List<String>): Result<Unit> {
        val path = "users/$uid"
        return try {
            requireUserId()
            val sanitized = notes.map { it.trim().take(250) }.filter { it.isNotBlank() }.distinct().take(30)
            userDoc(uid).update(
                mapOf(
                    "memoryNotes" to sanitized,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun logStudyMinutes(uid: String, additionalMinutes: Long, streakDays: Long): Result<Unit> {
        val path = "users/$uid"
        return try {
            requireUserId()
            userDoc(uid).update(
                mapOf(
                    "totalMinutesStudied" to FieldValue.increment(additionalMinutes.coerceIn(1L, 300L)),
                    "streakDays" to streakDays.coerceIn(1L, 10000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Conversations & Chalkboard ---

    fun observeConversations(uid: String): Flow<List<Conversation>> = flow {
        val path = "users/$uid/conversations"
        emitAll(
            userDoc(uid).collection("conversations")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Conversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeConversation(uid: String, chatId: String): Flow<Conversation?> = flow {
        val path = "users/$uid/conversations/$chatId"
        emitAll(
            userDoc(uid).collection("conversations").document(chatId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        Conversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    fun observeMessages(uid: String, chatId: String): Flow<List<ChatMessage>> = flow {
        val path = "users/$uid/conversations/$chatId/messages"
        emitAll(
            userDoc(uid).collection("conversations").document(chatId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun createConversation(
        uid: String,
        title: String,
        subject: String,
        topic: String,
        boardType: BoardType = BoardType.GENERAL,
        initialBoardElements: List<BoardElement> = emptyList()
    ): Result<String> {
        val col = userDoc(uid).collection("conversations")
        val docRef = col.document()
        return try {
            requireUserId()
            val payload = mapOf(
                "title" to title.ifBlank { "New Tutoring Session" }.take(120),
                "subject" to subject.ifBlank { "General" }.take(60),
                "topic" to topic.take(120),
                "boardType" to boardType.name,
                "boardElements" to initialBoardElements.take(100).map { it.toFirestoreMap() },
                "lastMessagePreview" to "Session ready — ask anything!",
                "messageCount" to 0L,
                "starred" to false,
                "archived" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun renameConversation(
        uid: String,
        chatId: String,
        title: String,
        subject: String,
        topic: String
    ): Result<Unit> {
        val docRef = userDoc(uid).collection("conversations").document(chatId)
        return try {
            requireUserId()
            docRef.update(
                mapOf(
                    "title" to title.ifBlank { "Tutoring Session" }.take(120),
                    "subject" to subject.ifBlank { "General" }.take(60),
                    "topic" to topic.take(120),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleConversationStar(
        uid: String,
        chatId: String,
        starred: Boolean,
        archived: Boolean
    ): Result<Unit> {
        val docRef = userDoc(uid).collection("conversations").document(chatId)
        return try {
            requireUserId()
            docRef.update(
                mapOf(
                    "starred" to starred,
                    "archived" to archived,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateChalkboard(
        uid: String,
        chatId: String,
        boardType: BoardType,
        elements: List<BoardElement>
    ): Result<Unit> {
        val docRef = userDoc(uid).collection("conversations").document(chatId)
        return try {
            requireUserId()
            docRef.update(
                mapOf(
                    "boardType" to boardType.name,
                    "boardElements" to elements.take(100).map { it.toFirestoreMap() },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun appendMessageAndBoard(
        uid: String,
        chatId: String,
        role: String,
        text: String,
        groundingSources: List<String>,
        hasBoardAction: Boolean,
        hasImageAttachment: Boolean,
        newBoardType: BoardType? = null,
        newBoardElements: List<BoardElement>? = null
    ): Result<Unit> {
        val convRef = userDoc(uid).collection("conversations").document(chatId)
        val msgRef = convRef.collection("messages").document()
        return try {
            requireUserId()
            val msgPayload = mapOf(
                "role" to role,
                "text" to text.take(32000),
                "groundingSources" to groundingSources.take(15),
                "hasBoardAction" to hasBoardAction,
                "hasImageAttachment" to hasImageAttachment,
                "createdAt" to FieldValue.serverTimestamp()
            )
            msgRef.set(msgPayload).await()

            val convUpdate = mutableMapOf<String, Any>(
                "lastMessagePreview" to text.replace("\n", " ").trim().take(240),
                "messageCount" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (newBoardType != null && newBoardElements != null) {
                convUpdate["boardType"] = newBoardType.name
                convUpdate["boardElements"] = newBoardElements.take(100).map { it.toFirestoreMap() }
            }
            convRef.update(convUpdate).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(uid: String, chatId: String): Result<Unit> {
        val convRef = userDoc(uid).collection("conversations").document(chatId)
        return try {
            requireUserId()
            val messages = convRef.collection("messages").get().await()
            if (!messages.isEmpty) {
                val batch = db.batch()
                messages.documents.forEach { batch.delete(it.reference) }
                batch.delete(convRef)
                batch.commit().await()
            } else {
                convRef.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, convRef.path)
            Result.failure(e)
        }
    }

    // --- Quizzes ---

    fun observeQuizzes(uid: String): Flow<List<QuizRecord>> = flow {
        val path = "users/$uid/quizzes"
        emitAll(
            userDoc(uid).collection("quizzes")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        QuizRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun createQuiz(
        uid: String,
        title: String,
        subject: String,
        topic: String,
        difficulty: String,
        questions: List<QuizQuestion>,
        conversationId: String? = null
    ): Result<String> {
        val docRef = userDoc(uid).collection("quizzes").document()
        return try {
            requireUserId()
            require(questions.isNotEmpty()) { "Quiz must have at least one question." }
            val payload = mutableMapOf<String, Any>(
                "title" to title.ifBlank { "$subject Quiz" }.take(120),
                "subject" to subject.ifBlank { "General" }.take(60),
                "topic" to topic.take(120),
                "difficulty" to difficulty,
                "questions" to questions.take(30).map { it.toFirestoreMap() },
                "completed" to false,
                "score" to 0L,
                "correctCount" to 0L,
                "totalCount" to questions.size.toLong(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!conversationId.isNullOrBlank()) {
                payload["conversationId"] = conversationId.take(128)
            }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun submitQuizAttempt(
        uid: String,
        quizId: String,
        correctCount: Long,
        totalCount: Long
    ): Result<Unit> {
        val docRef = userDoc(uid).collection("quizzes").document(quizId)
        return try {
            requireUserId()
            val safeTotal = totalCount.coerceAtLeast(1L)
            val safeCorrect = correctCount.coerceIn(0L, safeTotal)
            val scorePercent = ((safeCorrect * 100L) / safeTotal).coerceIn(0L, 100L)
            docRef.update(
                mapOf(
                    "completed" to true,
                    "score" to scorePercent,
                    "correctCount" to safeCorrect,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteQuiz(uid: String, quizId: String): Result<Unit> {
        val docRef = userDoc(uid).collection("quizzes").document(quizId)
        return try {
            requireUserId()
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Flashcards ---

    fun observeFlashcardDecks(uid: String): Flow<List<FlashcardDeck>> = flow {
        val path = "users/$uid/flashcards"
        emitAll(
            userDoc(uid).collection("flashcards")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FlashcardDeck::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun createFlashcardDeck(
        uid: String,
        title: String,
        subject: String,
        topic: String,
        cards: List<FlashcardItem>,
        conversationId: String? = null
    ): Result<String> {
        val docRef = userDoc(uid).collection("flashcards").document()
        return try {
            requireUserId()
            require(cards.isNotEmpty()) { "Deck must contain at least one flashcard." }
            val payload = mutableMapOf<String, Any>(
                "title" to title.ifBlank { "$subject Flashcards" }.take(120),
                "subject" to subject.ifBlank { "General" }.take(60),
                "topic" to topic.take(120),
                "cards" to cards.take(50).map { it.toFirestoreMap() },
                "masteredCount" to cards.count { it.mastered }.toLong(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!conversationId.isNullOrBlank()) {
                payload["conversationId"] = conversationId.take(128)
            }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateFlashcards(
        uid: String,
        deckId: String,
        cards: List<FlashcardItem>
    ): Result<Unit> {
        val docRef = userDoc(uid).collection("flashcards").document(deckId)
        return try {
            requireUserId()
            docRef.update(
                mapOf(
                    "cards" to cards.take(50).map { it.toFirestoreMap() },
                    "masteredCount" to cards.count { it.mastered }.toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteFlashcardDeck(uid: String, deckId: String): Result<Unit> {
        val docRef = userDoc(uid).collection("flashcards").document(deckId)
        return try {
            requireUserId()
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Resources (Notes, Guides, Concept Maps, Progress Reports) ---

    fun observeResources(uid: String): Flow<List<StudyResource>> = flow {
        val path = "users/$uid/resources"
        emitAll(
            userDoc(uid).collection("resources")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StudyResource::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun createResource(
        uid: String,
        title: String,
        subject: String,
        type: ResourceType,
        content: String,
        summary: String,
        conversationId: String? = null
    ): Result<String> {
        val docRef = userDoc(uid).collection("resources").document()
        return try {
            requireUserId()
            val payload = mutableMapOf<String, Any>(
                "title" to title.ifBlank { "Study Resource" }.take(120),
                "subject" to subject.ifBlank { "General" }.take(60),
                "resourceType" to type.name,
                "content" to content.ifBlank { summary }.ifBlank { "Study resource" }.take(40000),
                "summary" to summary.take(400),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!conversationId.isNullOrBlank()) {
                payload["conversationId"] = conversationId.take(128)
            }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteResource(uid: String, resourceId: String): Result<Unit> {
        val docRef = userDoc(uid).collection("resources").document(resourceId)
        return try {
            requireUserId()
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Topic Progress ---

    fun observeProgress(uid: String): Flow<List<TopicProgress>> = flow {
        val path = "users/$uid/progress"
        emitAll(
            userDoc(uid).collection("progress")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TopicProgress::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun upsertTopicProgress(
        uid: String,
        subject: String,
        topicId: String,
        topicTitle: String,
        masteryPercent: Long,
        minutesSpent: Long,
        status: ProgressStatus
    ): Result<Unit> {
        val safeDocId = topicId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120).ifBlank { "topic" }
        val docRef = userDoc(uid).collection("progress").document(safeDocId)
        return try {
            requireUserId()
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "masteryPercent" to masteryPercent.coerceIn(0L, 100L),
                        "minutesSpent" to minutesSpent.coerceIn(0L, 1000L),
                        "status" to status.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "subject" to subject.ifBlank { "General" }.take(60),
                        "topicId" to safeDocId,
                        "topicTitle" to topicTitle.ifBlank { safeDocId }.take(120),
                        "masteryPercent" to masteryPercent.coerceIn(0L, 100L),
                        "minutesSpent" to minutesSpent.coerceIn(0L, 1000L),
                        "status" to status.name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    private fun logStreamError(error: Throwable, op: OperationType, path: String, uid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }
}
