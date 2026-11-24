@file:OptIn(com.google.firebase.ai.type.PublicPreviewAPI::class)

package com.example.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.ai.AiTutorService
import com.example.ai.GeneratedLessonPayload
import com.example.audio.AudioController
import com.example.data.AppCheckDiagnostics
import com.example.data.BoardElement
import com.example.data.BoardType
import com.example.data.ChatMessage
import com.example.data.Conversation
import com.example.data.FlashcardDeck
import com.example.data.FlashcardItem
import com.example.data.ProgressStatus
import com.example.data.QuizRecord
import com.example.data.ResourceType
import com.example.data.StudyResource
import com.example.data.SubjectCatalog
import com.example.data.TopicProgress
import com.example.data.TutorRepository
import com.example.data.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.ai.type.LiveSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "ATutorVM"
private const val STOP_TIMEOUT_MILLIS = 5000L

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class LiveTranscriptLine(
    val speaker: String,
    val text: String
)

sealed interface LiveVoiceUiState {
    data object Idle : LiveVoiceUiState
    data object Connecting : LiveVoiceUiState
    data class Active(
        val subject: String,
        val topic: String,
        val liveUserText: String = "",
        val liveTutorText: String = "",
        val transcriptHistory: List<LiveTranscriptLine> = emptyList()
    ) : LiveVoiceUiState
    data class Error(val message: String) : LiveVoiceUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class ATutorViewModel(
    private val repository: TutorRepository,
    private val aiService: AiTutorService,
    val audioController: AudioController,
    private val currentUserId: String
) : ViewModel() {

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .toUiState("Failed to load student profile")

    val conversationsState: StateFlow<UiState<List<Conversation>>> =
        repository.observeConversations(currentUserId)
            .toUiState("Failed to load conversations")

    val quizzesState: StateFlow<UiState<List<QuizRecord>>> =
        repository.observeQuizzes(currentUserId)
            .toUiState("Failed to load quizzes")

    val decksState: StateFlow<UiState<List<FlashcardDeck>>> =
        repository.observeFlashcardDecks(currentUserId)
            .toUiState("Failed to load flashcards")

    val resourcesState: StateFlow<UiState<List<StudyResource>>> =
        repository.observeResources(currentUserId)
            .toUiState("Failed to load study resources")

    val progressState: StateFlow<UiState<List<TopicProgress>>> =
        repository.observeProgress(currentUserId)
            .toUiState("Failed to load learning progress")

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

    val activeConversation: StateFlow<Conversation?> = _activeChatId
        .flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) flowOf(null)
            else repository.observeConversation(currentUserId, chatId)
                .catch { error ->
                    unwrapFirestoreOrThrow(error)
                    emit(null)
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val activeMessages: StateFlow<List<ChatMessage>> = _activeChatId
        .flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) flowOf(emptyList())
            else repository.observeMessages(currentUserId, chatId)
                .catch { error ->
                    unwrapFirestoreOrThrow(error)
                    emit(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val _streamingReply = MutableStateFlow<String?>(null)
    val streamingReply: StateFlow<String?> = _streamingReply.asStateFlow()

    private val _isTutorThinking = MutableStateFlow(false)
    val isTutorThinking: StateFlow<Boolean> = _isTutorThinking.asStateFlow()

    private val _tutorError = MutableStateFlow<String?>(null)
    val tutorError: StateFlow<String?> = _tutorError.asStateFlow()

    private val _groundingEnabled = MutableStateFlow(false)
    val groundingEnabled: StateFlow<Boolean> = _groundingEnabled.asStateFlow()

    private val _quickHints = MutableStateFlow<List<String>>(emptyList())
    val quickHints: StateFlow<List<String>> = _quickHints.asStateFlow()

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    private val _transcribedDraft = MutableStateFlow<String?>(null)
    val transcribedDraft: StateFlow<String?> = _transcribedDraft.asStateFlow()

    private val _actionStatusMessage = MutableStateFlow<String?>(null)
    val actionStatusMessage: StateFlow<String?> = _actionStatusMessage.asStateFlow()

    private val _lessonState = MutableStateFlow<UiState<GeneratedLessonPayload>?>(null)
    val lessonState: StateFlow<UiState<GeneratedLessonPayload>?> = _lessonState.asStateFlow()

    private val _generatedDiagramState = MutableStateFlow<UiState<Bitmap>?>(null)
    val generatedDiagramState: StateFlow<UiState<Bitmap>?> = _generatedDiagramState.asStateFlow()

    private val _isGeneratingTool = MutableStateFlow(false)
    val isGeneratingTool: StateFlow<Boolean> = _isGeneratingTool.asStateFlow()

    private val _liveVoiceState = MutableStateFlow<LiveVoiceUiState>(LiveVoiceUiState.Idle)
    val liveVoiceState: StateFlow<LiveVoiceUiState> = _liveVoiceState.asStateFlow()

    private var liveSession: LiveSession? = null
    private var liveVoiceJob: Job? = null
    private var recordingJob: Job? = null
    private var ttsJob: Job? = null

    init {
        val user = Firebase.auth.currentUser
        viewModelScope.launch {
            repository.ensureUserProfile(
                uid = currentUserId,
                displayName = user?.displayName ?: "Student",
                email = user?.email,
                photoUrl = user?.photoUrl?.toString()
            )
        }
    }

    fun clearStatusMessage() {
        _actionStatusMessage.value = null
    }

    fun clearTutorError() {
        _tutorError.value = null
    }

    fun clearTranscribedDraft() {
        _transcribedDraft.value = null
    }

    fun clearGeneratedDiagram() {
        _generatedDiagramState.value = null
    }

    fun toggleGrounding() {
        _groundingEnabled.value = !_groundingEnabled.value
    }

    fun selectConversation(chatId: String) {
        _activeChatId.value = chatId
        _tutorError.value = null
        val conv = (conversationsState.value as? UiState.Success)?.data?.firstOrNull { it.id == chatId }
        if (conv != null) {
            refreshQuickHints(conv.subject, conv.topic)
        }
    }

    suspend fun createNewConversation(
        title: String,
        subject: String,
        topic: String,
        boardType: BoardType = BoardType.GENERAL,
        initialBoardElements: List<BoardElement> = emptyList(),
        initialScene3dJson: String = ""
    ): String? {
        val result = repository.createConversation(
            uid = currentUserId,
            title = title,
            subject = subject,
            topic = topic,
            boardType = boardType,
            initialBoardElements = initialBoardElements,
            initialScene3dJson = initialScene3dJson
        )
        return result.fold(
            onSuccess = { id ->
                _activeChatId.value = id
                refreshQuickHints(subject, topic)
                id
            },
            onFailure = { e ->
                _actionStatusMessage.value = e.localizedMessage ?: "Failed to create conversation."
                null
            }
        )
    }

    fun renameConversation(chatId: String, title: String, subject: String, topic: String) {
        viewModelScope.launch {
            repository.renameConversation(currentUserId, chatId, title, subject, topic)
                .onFailure { _actionStatusMessage.value = it.localizedMessage ?: "Rename failed." }
        }
    }

    fun toggleConversationStar(chat: Conversation) {
        viewModelScope.launch {
            repository.toggleConversationStar(currentUserId, chat.id, !chat.starred, chat.archived)
        }
    }

    fun deleteConversation(chatId: String) {
        viewModelScope.launch {
            if (_activeChatId.value == chatId) _activeChatId.value = null
            repository.deleteConversation(currentUserId, chatId)
                .onSuccess { _actionStatusMessage.value = "Conversation deleted." }
                .onFailure { _actionStatusMessage.value = it.localizedMessage ?: "Delete failed." }
        }
    }

    fun updateChalkboard(boardType: BoardType, elements: List<BoardElement>) {
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            repository.updateChalkboard(currentUserId, chatId, boardType, elements)
                .onFailure { _actionStatusMessage.value = it.localizedMessage ?: "Failed to save board." }
        }
    }

    fun updateScene3D(scene3dJson: String) {
        val chatId = _activeChatId.value
        if (chatId.isNullOrBlank()) {
            viewModelScope.launch {
                createNewConversation(
                    title = "3D Model Lab",
                    subject = "Science",
                    topic = "Interactive 3D Exploration",
                    boardType = BoardType.SCIENCE,
                    initialScene3dJson = scene3dJson
                )
            }
            return
        }
        viewModelScope.launch {
            repository.updateScene3D(currentUserId, chatId, scene3dJson)
                .onFailure { _actionStatusMessage.value = it.localizedMessage ?: "Failed to save 3D model." }
        }
    }

    fun refreshQuickHints(subject: String, topic: String) {
        viewModelScope.launch {
            val hints = aiService.generateQuickHints(subject, topic.ifBlank { subject })
            if (hints.isNotEmpty()) _quickHints.value = hints
        }
    }

    fun sendTutorMessage(
        prompt: String,
        attachedImage: Bitmap? = null,
        fallbackSubject: String = "Mathematics"
    ) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() && attachedImage == null) return
        val effectivePrompt = if (trimmed.isEmpty()) "Please analyze this image and explain it step by step." else trimmed

        viewModelScope.launch {
            _tutorError.value = null
            _isTutorThinking.value = true
            _streamingReply.value = ""

            try {
                var chatId = _activeChatId.value
                if (chatId.isNullOrBlank()) {
                    val subject = fallbackSubject
                    val newTitle = effectivePrompt.take(48).ifBlank { "$subject Lesson" }
                    chatId = createNewConversation(
                        title = newTitle,
                        subject = subject,
                        topic = effectivePrompt.take(80),
                        boardType = SubjectCatalog.findSubject(subject).defaultBoardType
                    ) ?: throw IllegalStateException("Could not initialize conversation.")
                }

                val currentConv = activeConversation.value
                val subject = currentConv?.subject ?: fallbackSubject
                val topic = currentConv?.topic ?: ""
                val boardType = currentConv?.parsedBoardType() ?: BoardType.GENERAL
                val boardElements = currentConv?.parsedBoardElements() ?: emptyList()
                val profile = (userProfileState.value as? UiState.Success)?.data
                val historySnapshot = activeMessages.value

                repository.appendMessageAndBoard(
                    uid = currentUserId,
                    chatId = chatId,
                    role = "user",
                    text = effectivePrompt,
                    groundingSources = emptyList(),
                    hasBoardAction = false,
                    hasImageAttachment = attachedImage != null
                )

                var finalEventText = ""
                var sources = emptyList<String>()
                var updatedType: BoardType? = null
                var updatedElements: List<BoardElement>? = null
                var updatedScene3d: String? = null
                var newMemory: String? = null

                aiService.sendTutorMessageStream(
                    modelName = profile?.preferredModel ?: AiTutorService.MODEL_DEFAULT,
                    useSearchAndMapsGrounding = _groundingEnabled.value,
                    profile = profile,
                    subject = subject,
                    topic = topic,
                    history = historySnapshot,
                    userPrompt = effectivePrompt,
                    attachedImage = attachedImage,
                    currentBoardType = boardType,
                    currentBoard = boardElements
                ).collect { event ->
                    _streamingReply.value = event.partialText
                    if (event.finalText != null) {
                        finalEventText = event.finalText
                        sources = event.groundingSources
                        updatedType = event.updatedBoardType
                        updatedElements = event.updatedBoardElements
                        updatedScene3d = event.updatedScene3dJson
                        newMemory = event.addedMemoryNote
                    }
                }

                repository.appendMessageAndBoard(
                    uid = currentUserId,
                    chatId = chatId,
                    role = "model",
                    text = finalEventText,
                    groundingSources = sources,
                    hasBoardAction = updatedElements != null || !updatedScene3d.isNullOrBlank(),
                    hasImageAttachment = false,
                    newBoardType = updatedType,
                    newBoardElements = updatedElements,
                    newScene3dJson = updatedScene3d
                )

                if (!newMemory.isNullOrBlank()) {
                    val existingNotes = profile?.memoryNotes ?: emptyList()
                    repository.updateMemoryNotes(currentUserId, (existingNotes + newMemory!!).distinct())
                }

                repository.logStudyMinutes(
                    uid = currentUserId,
                    additionalMinutes = 2L,
                    streakDays = (profile?.streakDays ?: 1L).coerceAtLeast(1L)
                )

                refreshQuickHints(subject, topic)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Tutor response timed out", e)
                _tutorError.value = "A-Tutor timed out after 60s. Please retry."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Tutor message failed", e)
                _tutorError.value = formatAiErrorMessage(e, "Failed to reach AI Tutor.")
            } finally {
                _streamingReply.value = null
                _isTutorThinking.value = false
            }
        }
    }

    fun explainChalkboardWithAi(boardBitmap: Bitmap, question: String = "") {
        val chatId = _activeChatId.value ?: return
        val conv = activeConversation.value ?: return
        viewModelScope.launch {
            _tutorError.value = null
            _isTutorThinking.value = true
            try {
                val userLabel = if (question.isBlank()) "Review my chalkboard work" else question
                repository.appendMessageAndBoard(
                    uid = currentUserId,
                    chatId = chatId,
                    role = "user",
                    text = userLabel,
                    groundingSources = emptyList(),
                    hasBoardAction = false,
                    hasImageAttachment = true
                )
                val reply = aiService.explainChalkboardSnapshot(boardBitmap, conv.subject, question)
                repository.appendMessageAndBoard(
                    uid = currentUserId,
                    chatId = chatId,
                    role = "model",
                    text = reply,
                    groundingSources = emptyList(),
                    hasBoardAction = false,
                    hasImageAttachment = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chalkboard review failed", e)
                _tutorError.value = formatAiErrorMessage(e, "Failed to analyze chalkboard.")
            } finally {
                _isTutorThinking.value = false
            }
        }
    }

    // --- TTS & Microphone Transcription ---

    fun speakMessage(messageId: String, text: String) {
        if (_speakingMessageId.value == messageId) {
            ttsJob?.cancel()
            audioController.stopPlayback()
            _speakingMessageId.value = null
            return
        }
        ttsJob?.cancel()
        audioController.stopPlayback()
        ttsJob = viewModelScope.launch {
            _speakingMessageId.value = messageId
            try {
                val audio = aiService.synthesizeSpeech(text)
                audioController.playAudioBytes(audio.bytes, audio.mimeType)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "TTS failed", e)
                _actionStatusMessage.value = e.localizedMessage ?: "Text-to-speech failed."
            } finally {
                _speakingMessageId.value = null
            }
        }
    }

    fun startVoiceRecording() {
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            val started = audioController.startRecording()
            if (!started) {
                _actionStatusMessage.value = "Unable to access microphone."
            }
        }
    }

    fun stopVoiceRecordingAndTranscribe() {
        viewModelScope.launch {
            val wavBytes = audioController.stopRecordingAndGetWav()
            recordingJob?.cancel()
            if (wavBytes.size <= 44) {
                _actionStatusMessage.value = "Recording was empty."
                return@launch
            }
            _isTranscribing.value = true
            try {
                val transcript = aiService.transcribeAudio(wavBytes)
                _transcribedDraft.value = transcript
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Audio transcription failed", e)
                _actionStatusMessage.value = e.localizedMessage ?: "Transcription failed."
            } finally {
                _isTranscribing.value = false
            }
        }
    }

    // --- Live Voice Learning (gemini-3.1-flash-live-preview + fallback) ---

    fun startLiveVoiceSession(subject: String, topic: String) {
        if (_liveVoiceState.value is LiveVoiceUiState.Active ||
            _liveVoiceState.value is LiveVoiceUiState.Connecting
        ) return

        liveVoiceJob?.cancel()
        liveVoiceJob = viewModelScope.launch {
            _liveVoiceState.value = LiveVoiceUiState.Connecting
            val profile = (userProfileState.value as? UiState.Success)?.data
            try {
                connectAndRunLiveSession(profile, subject, topic, useFallback = false)
            } catch (e: CancellationException) {
                throw e
            } catch (primaryErr: Exception) {
                Log.w(TAG, "Primary Live model failed (${primaryErr.message}), retrying fallback", primaryErr)
                runCatching { liveSession?.stopAudioConversation() }
                runCatching { liveSession?.close() }
                liveSession = null
                try {
                    connectAndRunLiveSession(profile, subject, topic, useFallback = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (fallbackErr: Exception) {
                    Log.e(TAG, "Live Voice session error", fallbackErr)
                    _liveVoiceState.value = LiveVoiceUiState.Error(
                        formatAiErrorMessage(fallbackErr, primaryErr.localizedMessage ?: "Unable to connect Live Voice session.")
                    )
                }
            }
        }
    }

    private suspend fun connectAndRunLiveSession(
        profile: UserProfile?,
        subject: String,
        topic: String,
        useFallback: Boolean
    ) {
        val model = aiService.createLiveTutorModel(profile, subject, topic, useFallback = useFallback)
        val session = model.connect()
        liveSession = session
        _liveVoiceState.value = LiveVoiceUiState.Active(subject = subject, topic = topic)

        session.startAudioConversation(
            functionCallHandler = { call ->
                com.google.firebase.ai.type.FunctionResponsePart(
                    name = call.name,
                    response = kotlinx.serialization.json.JsonObject(emptyMap())
                )
            },
            transcriptHandler = { inputT, outputT ->
                val cur = _liveVoiceState.value as? LiveVoiceUiState.Active ?: return@startAudioConversation
                val newUser = inputT?.text?.trim().orEmpty()
                val newTutor = outputT?.text?.trim().orEmpty()
                val updatedHistory = cur.transcriptHistory.toMutableList()
                if (newUser.isNotBlank() && newUser != cur.liveUserText) {
                    updatedHistory.add(LiveTranscriptLine("You", newUser))
                }
                if (newTutor.isNotBlank() && newTutor != cur.liveTutorText) {
                    updatedHistory.add(LiveTranscriptLine("A-Tutor", newTutor))
                }
                _liveVoiceState.value = cur.copy(
                    liveUserText = newUser.ifBlank { cur.liveUserText },
                    liveTutorText = newTutor.ifBlank { cur.liveTutorText },
                    transcriptHistory = updatedHistory.takeLast(30)
                )
            }
        )
    }

    fun stopLiveVoiceSession(saveSummaryToChat: Boolean = true) {
        val activeState = _liveVoiceState.value as? LiveVoiceUiState.Active
        viewModelScope.launch {
            try {
                liveSession?.stopAudioConversation()
                liveSession?.close()
            } catch (_: Exception) {
            }
            liveSession = null
            liveVoiceJob?.cancel()
            _liveVoiceState.value = LiveVoiceUiState.Idle

            if (saveSummaryToChat && activeState != null && activeState.transcriptHistory.isNotEmpty()) {
                val chatId = _activeChatId.value ?: createNewConversation(
                    title = "Voice Lesson: ${activeState.subject}",
                    subject = activeState.subject,
                    topic = activeState.topic
                )
                if (chatId != null) {
                    val summary = activeState.transcriptHistory.joinToString("\n") {
                        "**${it.speaker}:** ${it.text}"
                    }
                    repository.appendMessageAndBoard(
                        uid = currentUserId,
                        chatId = chatId,
                        role = "model",
                        text = "Live Voice Session Summary:\n\n$summary",
                        groundingSources = emptyList(),
                        hasBoardAction = false,
                        hasImageAttachment = false
                    )
                }
            }
        }
    }

    // --- Lessons, Quizzes, Flashcards, Resources, Reports & Images ---

    fun loadOrGenerateLesson(subject: String, lessonTitle: String) {
        viewModelScope.launch {
            _lessonState.value = UiState.Loading
            try {
                val profile = (userProfileState.value as? UiState.Success)?.data
                val payload = aiService.generateLessonPackage(
                    subject = subject,
                    lessonTitle = lessonTitle,
                    learningLevel = profile?.learningLevel ?: "Intermediate",
                    learningStyle = profile?.learningStyle ?: "Visual"
                )
                _lessonState.value = UiState.Success(payload)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Lesson generation failed", e)
                _lessonState.value = UiState.Error(e.localizedMessage ?: "Failed to generate lesson.")
            }
        }
    }

    fun markLessonCompleted(subject: String, topicId: String, topicTitle: String, minutes: Long) {
        viewModelScope.launch {
            repository.upsertTopicProgress(
                uid = currentUserId,
                subject = subject,
                topicId = topicId,
                topicTitle = topicTitle,
                masteryPercent = 100L,
                minutesSpent = minutes,
                status = ProgressStatus.COMPLETED
            ).onSuccess {
                _actionStatusMessage.value = "Lesson marked completed!"
            }
        }
    }

    fun generateNewQuiz(subject: String, topic: String, difficulty: String, count: Int, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _isGeneratingTool.value = true
            try {
                val profile = (userProfileState.value as? UiState.Success)?.data
                val payload = aiService.generateQuiz(
                    subject = subject,
                    topic = topic,
                    difficulty = difficulty,
                    questionCount = count,
                    learningLevel = profile?.learningLevel ?: "Intermediate"
                )
                repository.createQuiz(
                    uid = currentUserId,
                    title = payload.title,
                    subject = subject,
                    topic = topic,
                    difficulty = difficulty,
                    questions = payload.questions,
                    conversationId = _activeChatId.value
                ).onSuccess { quizId ->
                    _actionStatusMessage.value = "Quiz ready!"
                    onCreated(quizId)
                }.onFailure {
                    _actionStatusMessage.value = it.localizedMessage ?: "Failed to save quiz."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Quiz generation failed", e)
                _actionStatusMessage.value = e.localizedMessage ?: "Quiz generation failed."
            } finally {
                _isGeneratingTool.value = false
            }
        }
    }

    fun submitQuizScore(quiz: QuizRecord, correctCount: Int) {
        viewModelScope.launch {
            repository.submitQuizAttempt(
                uid = currentUserId,
                quizId = quiz.id,
                correctCount = correctCount.toLong(),
                totalCount = quiz.totalCount
            ).onSuccess {
                val pct = ((correctCount.toLong() * 100L) / quiz.totalCount.coerceAtLeast(1L)).coerceIn(0L, 100L)
                repository.upsertTopicProgress(
                    uid = currentUserId,
                    subject = quiz.subject,
                    topicId = "${quiz.subject}_${quiz.topic}".lowercase(),
                    topicTitle = quiz.topic.ifBlank { quiz.title },
                    masteryPercent = pct,
                    minutesSpent = 10L,
                    status = if (pct >= 75L) ProgressStatus.COMPLETED else ProgressStatus.IN_PROGRESS
                )
            }
        }
    }

    fun deleteQuiz(quizId: String) {
        viewModelScope.launch { repository.deleteQuiz(currentUserId, quizId) }
    }

    fun generateFlashcardDeck(subject: String, topic: String, cardCount: Int, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _isGeneratingTool.value = true
            try {
                val profile = (userProfileState.value as? UiState.Success)?.data
                val payload = aiService.generateFlashcards(
                    subject = subject,
                    topic = topic,
                    cardCount = cardCount,
                    learningLevel = profile?.learningLevel ?: "Intermediate"
                )
                repository.createFlashcardDeck(
                    uid = currentUserId,
                    title = payload.title,
                    subject = subject,
                    topic = topic,
                    cards = payload.cards,
                    conversationId = _activeChatId.value
                ).onSuccess { deckId ->
                    _actionStatusMessage.value = "Flashcard deck created!"
                    onCreated(deckId)
                }.onFailure {
                    _actionStatusMessage.value = it.localizedMessage ?: "Failed to save flashcards."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Flashcards failed", e)
                _actionStatusMessage.value = e.localizedMessage ?: "Flashcard generation failed."
            } finally {
                _isGeneratingTool.value = false
            }
        }
    }

    fun toggleCardMastered(deck: FlashcardDeck, cardId: String) {
        viewModelScope.launch {
            val updated = deck.parsedCards().map {
                if (it.id == cardId) it.copy(mastered = !it.mastered) else it
            }
            repository.updateFlashcards(currentUserId, deck.id, updated)
        }
    }

    fun deleteDeck(deckId: String) {
        viewModelScope.launch { repository.deleteFlashcardDeck(currentUserId, deckId) }
    }

    fun generateAndSaveStudyResource(
        title: String,
        subject: String,
        type: ResourceType,
        prompt: String
    ) {
        viewModelScope.launch {
            _isGeneratingTool.value = true
            try {
                val (summary, content) = aiService.generateStudyGuideOrReport(title, subject, prompt)
                repository.createResource(
                    uid = currentUserId,
                    title = title,
                    subject = subject,
                    type = type,
                    content = content,
                    summary = summary,
                    conversationId = _activeChatId.value
                ).onSuccess {
                    _actionStatusMessage.value = "${type.label} saved to Library!"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Resource generation failed", e)
                _actionStatusMessage.value = e.localizedMessage ?: "Failed to generate resource."
            } finally {
                _isGeneratingTool.value = false
            }
        }
    }

    fun saveDirectNoteResource(title: String, subject: String, type: ResourceType, content: String) {
        viewModelScope.launch {
            repository.createResource(
                uid = currentUserId,
                title = title,
                subject = subject,
                type = type,
                content = content,
                summary = content.take(180),
                conversationId = _activeChatId.value
            ).onSuccess {
                _actionStatusMessage.value = "Saved to Study Library!"
            }
        }
    }

    fun generateAiProgressReport() {
        val quizzes = (quizzesState.value as? UiState.Success)?.data ?: emptyList()
        val decks = (decksState.value as? UiState.Success)?.data ?: emptyList()
        val progress = (progressState.value as? UiState.Success)?.data ?: emptyList()
        val profile = (userProfileState.value as? UiState.Success)?.data

        val statsContext = buildString {
            appendLine("Level: ${profile?.learningLevel ?: "Intermediate"}, Style: ${profile?.learningStyle ?: "Visual"}")
            appendLine("Study Streak: ${profile?.streakDays ?: 1} days, Total Minutes: ${profile?.totalMinutesStudied ?: 0}")
            appendLine("Memory notes: ${profile?.memoryNotes?.joinToString("; ") ?: "None"}")
            appendLine("Completed Quizzes (${quizzes.count { it.completed }}): " +
                quizzes.filter { it.completed }.joinToString { "${it.subject}:${it.title}=${it.score}%" })
            appendLine("Flashcards: " + decks.joinToString { "${it.title} (${it.masteredCount}/${it.cards.size} mastered)" })
            appendLine("Topic Progress: " + progress.joinToString { "${it.subject}:${it.topicTitle}=${it.masteryPercent}%" })
        }

        generateAndSaveStudyResource(
            title = "AI Learning Progress Report",
            subject = "Analytics",
            type = ResourceType.PROGRESS_REPORT,
            prompt = "Analyze this student's learning data. Highlight strengths, identify specific knowledge gaps, praise consistency, and give a prioritized 3-step next study plan:\n$statsContext"
        )
    }

    fun deleteResource(resourceId: String) {
        viewModelScope.launch { repository.deleteResource(currentUserId, resourceId) }
    }

    fun generateEducationalDiagram(prompt: String) {
        viewModelScope.launch {
            _generatedDiagramState.value = UiState.Loading
            try {
                val bmp = aiService.generateEducationalImage(prompt)
                _generatedDiagramState.value = UiState.Success(bmp)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation failed", e)
                _generatedDiagramState.value = UiState.Error(
                    e.localizedMessage ?: "Educational diagram generation failed."
                )
            }
        }
    }

    fun savePreferences(
        displayName: String,
        learningLevel: String,
        learningStyle: String,
        dailyGoalMinutes: Long,
        preferredModel: String,
        focusSubjects: List<String>
    ) {
        viewModelScope.launch {
            repository.updatePreferences(
                uid = currentUserId,
                displayName = displayName,
                learningLevel = learningLevel,
                learningStyle = learningStyle,
                dailyGoalMinutes = dailyGoalMinutes,
                preferredModel = preferredModel,
                focusSubjects = focusSubjects
            ).onSuccess {
                _actionStatusMessage.value = "Learning preferences saved!"
            }.onFailure {
                _actionStatusMessage.value = it.localizedMessage ?: "Could not save preferences."
            }
        }
    }

    fun updateMemoryNotes(notes: List<String>) {
        viewModelScope.launch {
            repository.updateMemoryNotes(currentUserId, notes)
                .onSuccess { _actionStatusMessage.value = "AI Memory updated!" }
        }
    }

    fun cleanupBeforeSignOut() {
        ttsJob?.cancel()
        recordingJob?.cancel()
        liveVoiceJob?.cancel()
        audioController.stopPlayback()
        try {
            liveSession?.stopAudioConversation()
        } catch (_: Exception) {
        }
        liveSession = null
    }

    override fun onCleared() {
        super.onCleared()
        cleanupBeforeSignOut()
    }

    private fun <T> Flow<T>.toUiState(fallbackError: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreOrThrow(error)
                Log.w(TAG, fallbackError, root)
                emit(UiState.Error(root.message ?: fallbackError))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private suspend fun formatAiErrorMessage(error: Exception, fallback: String): String {
        val raw = error.localizedMessage ?: fallback
        return if (raw.contains("app check", ignoreCase = true) || raw.contains("403")) {
            val diag = AppCheckDiagnostics.verifyToken(forceRefresh = true)
            "$raw (${diag.providerMode}: ${diag.initError ?: diag.tokenCheckSummary})"
        } else {
            raw
        }
    }

    private fun unwrapFirestoreOrThrow(error: Throwable): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        return firestoreError ?: error
    }

    companion object {
        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ATutorViewModel(
                    repository = TutorRepository(db),
                    aiService = AiTutorService(),
                    audioController = AudioController(),
                    currentUserId = currentUserId
                )
            }
        }
    }
}
