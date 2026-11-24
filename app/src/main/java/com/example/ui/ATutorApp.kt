package com.example.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.BoardType
import com.example.data.SubjectCatalog
import com.example.ui.components.UpdatePromptDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FlashcardStudyScreen
import com.example.ui.screens.LessonDetailScreen
import com.example.ui.screens.LibraryAndAnalyticsScreen
import com.example.ui.screens.LiveVoiceScreen
import com.example.ui.screens.PracticeHubScreen
import com.example.ui.screens.QuizPlayScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SubjectDetailScreen
import com.example.ui.screens.SubjectsScreen
import com.example.ui.screens.TutorWorkspaceScreen
import com.example.ui.theme.ATutorTheme
import com.example.update.AppUpdateUiState
import com.example.update.GitHubAppUpdater
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

private val Context.settingsDataStore by preferencesDataStore(name = "atutor_prefs")
private val DARK_THEME_KEY = booleanPreferencesKey("dark_theme_enabled")

@Serializable
object RouteDashboard

@Serializable
data class RouteTutor(val chatId: String? = null)

@Serializable
object RouteSubjects

@Serializable
data class RouteSubjectDetail(val subjectId: String)

@Serializable
data class RouteLessonDetail(val lessonId: String)

@Serializable
object RoutePractice

@Serializable
data class RouteQuizPlay(val quizId: String)

@Serializable
data class RouteFlashcardStudy(val deckId: String)

@Serializable
data class RouteLibrary(val initialTab: Int = 0)

@Serializable
object RouteSettings

@Serializable
data class RouteLiveVoice(val subject: String, val topic: String)

private data class TopLevelNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: Any,
    val testTag: String
)

private val TOP_LEVEL_ITEMS = listOf(
    TopLevelNavItem(
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        route = RouteDashboard,
        testTag = "nav_home"
    ),
    TopLevelNavItem(
        label = "Tutor",
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        route = RouteTutor(null),
        testTag = "nav_tutor"
    ),
    TopLevelNavItem(
        label = "Subjects",
        selectedIcon = Icons.Filled.School,
        unselectedIcon = Icons.Outlined.School,
        route = RouteSubjects,
        testTag = "nav_subjects"
    ),
    TopLevelNavItem(
        label = "Practice",
        selectedIcon = Icons.Filled.Quiz,
        unselectedIcon = Icons.Outlined.Quiz,
        route = RoutePractice,
        testTag = "nav_practice"
    ),
    TopLevelNavItem(
        label = "Library",
        selectedIcon = Icons.AutoMirrored.Filled.MenuBook,
        unselectedIcon = Icons.AutoMirrored.Outlined.MenuBook,
        route = RouteLibrary(0),
        testTag = "nav_library"
    )
)

@Composable
fun ATutorRootApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val systemDark = isSystemInDarkTheme()
    val darkThemeEnabled by remember(context, systemDark) {
        context.settingsDataStore.data.map { prefs -> prefs[DARK_THEME_KEY] ?: systemDark }
    }.collectAsStateWithLifecycle(initialValue = systemDark)

    val appUpdater = remember(context) { GitHubAppUpdater(context.applicationContext) }
    val updateState by appUpdater.state.collectAsStateWithLifecycle()
    val showUpdateDialog by appUpdater.showDialog.collectAsStateWithLifecycle()

    LaunchedEffect(appUpdater) {
        appUpdater.checkForUpdates(manual = false)
    }

    val currentUser by remember {
        Firebase.auth.authStateFlow()
    }.collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    ATutorTheme(darkTheme = darkThemeEnabled) {
        val user = currentUser
        if (user == null) {
            AuthScreen(onAuthSuccess = { /* authStateFlow advances automatically */ })
        } else {
            key(user.uid) {
                AuthenticatedATutorShell(
                    userId = user.uid,
                    darkThemeEnabled = darkThemeEnabled,
                    updateState = updateState,
                    onCheckForUpdates = {
                        scope.launch {
                            val current = appUpdater.state.value
                            if (current is AppUpdateUiState.UpdateAvailable ||
                                current is AppUpdateUiState.NeedsInstallPermission ||
                                current is AppUpdateUiState.ReadyToInstall
                            ) {
                                appUpdater.reopenDialog()
                            } else {
                                appUpdater.checkForUpdates(manual = true)
                            }
                        }
                    },
                    onToggleDarkTheme = { enabled ->
                        scope.launch {
                            context.settingsDataStore.edit { prefs ->
                                prefs[DARK_THEME_KEY] = enabled
                            }
                        }
                    }
                )
            }
        }

        if (showUpdateDialog) {
            UpdatePromptDialog(
                state = updateState,
                onStartUpdate = { update ->
                    scope.launch {
                        appUpdater.downloadAndInstall(update)
                    }
                },
                onRetryInstall = { update, path ->
                    appUpdater.retryInstallWithCachedApk(update, path)
                },
                onOpenInstallPermissionSettings = {
                    appUpdater.openInstallUnknownAppsSettings()
                },
                onDismiss = {
                    appUpdater.dismissDialog()
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedATutorShell(
    userId: String,
    darkThemeEnabled: Boolean,
    updateState: AppUpdateUiState,
    onCheckForUpdates: () -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit
) {
    val viewModel: ATutorViewModel = viewModel(
        key = userId,
        factory = ATutorViewModel.provideFactory(userId)
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val quizzesState by viewModel.quizzesState.collectAsStateWithLifecycle()
    val decksState by viewModel.decksState.collectAsStateWithLifecycle()
    val resourcesState by viewModel.resourcesState.collectAsStateWithLifecycle()
    val progressState by viewModel.progressState.collectAsStateWithLifecycle()

    val activeConversation by viewModel.activeConversation.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val streamingReply by viewModel.streamingReply.collectAsStateWithLifecycle()
    val isTutorThinking by viewModel.isTutorThinking.collectAsStateWithLifecycle()
    val tutorError by viewModel.tutorError.collectAsStateWithLifecycle()
    val groundingEnabled by viewModel.groundingEnabled.collectAsStateWithLifecycle()
    val quickHints by viewModel.quickHints.collectAsStateWithLifecycle()
    val speakingMessageId by viewModel.speakingMessageId.collectAsStateWithLifecycle()
    val isRecordingAudio by viewModel.audioController.isRecording.collectAsStateWithLifecycle()
    val isTranscribing by viewModel.isTranscribing.collectAsStateWithLifecycle()
    val transcribedDraft by viewModel.transcribedDraft.collectAsStateWithLifecycle()
    val generatedDiagramState by viewModel.generatedDiagramState.collectAsStateWithLifecycle()
    val lessonState by viewModel.lessonState.collectAsStateWithLifecycle()
    val isGeneratingTool by viewModel.isGeneratingTool.collectAsStateWithLifecycle()
    val liveVoiceState by viewModel.liveVoiceState.collectAsStateWithLifecycle()
    val actionStatusMessage by viewModel.actionStatusMessage.collectAsStateWithLifecycle()

    LaunchedEffect(actionStatusMessage) {
        val msg = actionStatusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isTopLevelRoute = currentDestination?.let { dest ->
        dest.hasRoute<RouteDashboard>() ||
            dest.hasRoute<RouteTutor>() ||
            dest.hasRoute<RouteSubjects>() ||
            dest.hasRoute<RoutePractice>() ||
            dest.hasRoute<RouteLibrary>()
    } ?: true

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openTutorSession(chatId: String?) {
        if (!chatId.isNullOrBlank()) {
            viewModel.selectConversation(chatId)
        }
        navigateTopLevel(RouteTutor(chatId))
    }

    fun startFreshSubjectSession(subjectName: String) {
        scope.launch {
            val subjectDef = SubjectCatalog.findSubject(subjectName)
            val createdId = viewModel.createNewConversation(
                title = "${subjectDef.name} Tutoring",
                subject = subjectDef.name,
                topic = "Guided Lesson",
                boardType = subjectDef.defaultBoardType
            )
            openTutorSession(createdId)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 820.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (isTopLevelRoute && !useRail) {
                    NavigationBar {
                        TOP_LEVEL_ITEMS.forEach { item ->
                            val selected = when (item.route) {
                                is RouteDashboard -> currentDestination?.hasRoute<RouteDashboard>() == true
                                is RouteTutor -> currentDestination?.hasRoute<RouteTutor>() == true
                                is RouteSubjects -> currentDestination?.hasRoute<RouteSubjects>() == true
                                is RoutePractice -> currentDestination?.hasRoute<RoutePractice>() == true
                                is RouteLibrary -> currentDestination?.hasRoute<RouteLibrary>() == true
                                else -> false
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevelRoute && useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        Spacer(Modifier.weight(0.2f))
                        TOP_LEVEL_ITEMS.forEach { item ->
                            val selected = when (item.route) {
                                is RouteDashboard -> currentDestination?.hasRoute<RouteDashboard>() == true
                                is RouteTutor -> currentDestination?.hasRoute<RouteTutor>() == true
                                is RouteSubjects -> currentDestination?.hasRoute<RouteSubjects>() == true
                                is RoutePractice -> currentDestination?.hasRoute<RoutePractice>() == true
                                is RouteLibrary -> currentDestination?.hasRoute<RouteLibrary>() == true
                                else -> false
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag("${item.testTag}_rail")
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = RouteDashboard,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    composable<RouteDashboard> {
                        DashboardScreen(
                            profileState = profileState,
                            conversationsState = conversationsState,
                            quizzesState = quizzesState,
                            decksState = decksState,
                            progressState = progressState,
                            onOpenTutor = { chatId -> openTutorSession(chatId) },
                            onStartSubjectChat = { subjectName -> startFreshSubjectSession(subjectName) },
                            onOpenLiveVoice = { subject, topic ->
                                navController.navigate(RouteLiveVoice(subject, topic))
                            },
                            onOpenPractice = { navigateTopLevel(RoutePractice) },
                            onOpenAnalytics = { navigateTopLevel(RouteLibrary(initialTab = 1)) },
                            onOpenSettings = { navController.navigate(RouteSettings) }
                        )
                    }

                    composable<RouteTutor> { entry ->
                        val args = entry.toRoute<RouteTutor>()
                        LaunchedEffect(args.chatId) {
                            val chatId = args.chatId
                            if (!chatId.isNullOrBlank() && viewModel.activeChatId.value != chatId) {
                                viewModel.selectConversation(chatId)
                            }
                        }
                        TutorWorkspaceScreen(
                            conversationsState = conversationsState,
                            activeConversation = activeConversation,
                            messages = activeMessages,
                            streamingReply = streamingReply,
                            isThinking = isTutorThinking,
                            tutorError = tutorError,
                            groundingEnabled = groundingEnabled,
                            quickHints = quickHints,
                            speakingMessageId = speakingMessageId,
                            isRecordingAudio = isRecordingAudio,
                            isTranscribing = isTranscribing,
                            transcribedDraft = transcribedDraft,
                            generatedDiagramState = generatedDiagramState,
                            onClearTranscribedDraft = viewModel::clearTranscribedDraft,
                            onSelectConversation = viewModel::selectConversation,
                            onCreateConversation = { title, subject, topic, boardType ->
                                viewModel.createNewConversation(title, subject, topic, boardType)
                            },
                            onRenameConversation = viewModel::renameConversation,
                            onToggleStar = viewModel::toggleConversationStar,
                            onDeleteConversation = viewModel::deleteConversation,
                            onSendMessage = viewModel::sendTutorMessage,
                            onToggleGrounding = viewModel::toggleGrounding,
                            onUpdateBoard = viewModel::updateChalkboard,
                            onUpdateScene3D = viewModel::updateScene3D,
                            onExplainBoard = { bmp -> viewModel.explainChalkboardWithAi(bmp) },
                            onSpeakMessage = viewModel::speakMessage,
                            onStartVoiceRecording = viewModel::startVoiceRecording,
                            onStopVoiceRecording = viewModel::stopVoiceRecordingAndTranscribe,
                            onOpenLiveVoice = { subject, topic ->
                                navController.navigate(RouteLiveVoice(subject, topic))
                            },
                            onSaveMessageToLibrary = viewModel::saveDirectNoteResource,
                            onGenerateQuizFromTopic = { subj, topic ->
                                viewModel.generateNewQuiz(subj, topic, "Medium", 5) { quizId ->
                                    navController.navigate(RouteQuizPlay(quizId))
                                }
                            },
                            onGenerateDeckFromTopic = { subj, topic ->
                                viewModel.generateFlashcardDeck(subj, topic, 6) { deckId ->
                                    navController.navigate(RouteFlashcardStudy(deckId))
                                }
                            },
                            onGenerateDiagram = viewModel::generateEducationalDiagram,
                            onDismissDiagram = viewModel::clearGeneratedDiagram
                        )
                    }

                    composable<RouteSubjects> {
                        SubjectsScreen(
                            progressState = progressState,
                            onSelectSubject = { subjectId ->
                                navController.navigate(RouteSubjectDetail(subjectId))
                            }
                        )
                    }

                    composable<RouteSubjectDetail> { entry ->
                        val args = entry.toRoute<RouteSubjectDetail>()
                        SubjectDetailScreen(
                            subjectId = args.subjectId,
                            progressState = progressState,
                            onBack = { navController.popBackStack() },
                            onOpenLesson = { lessonId ->
                                navController.navigate(RouteLessonDetail(lessonId))
                            },
                            onAskTutorAboutSubject = { subjectName ->
                                startFreshSubjectSession(subjectName)
                            }
                        )
                    }

                    composable<RouteLessonDetail> { entry ->
                        val args = entry.toRoute<RouteLessonDetail>()
                        LessonDetailScreen(
                            lessonId = args.lessonId,
                            lessonState = lessonState,
                            onBack = { navController.popBackStack() },
                            onLoadLesson = viewModel::loadOrGenerateLesson,
                            onMarkCompleted = viewModel::markLessonCompleted,
                            onLaunchTutorWithBoard = { subject, title, boardType, elements ->
                                scope.launch {
                                    val chatId = viewModel.createNewConversation(
                                        title = title,
                                        subject = subject,
                                        topic = title,
                                        boardType = boardType,
                                        initialBoardElements = elements
                                    )
                                    openTutorSession(chatId)
                                }
                            },
                            onGenerateLessonQuiz = { subject, title ->
                                viewModel.generateNewQuiz(subject, title, "Medium", 5) { quizId ->
                                    navController.navigate(RouteQuizPlay(quizId))
                                }
                            },
                            onShowMessage = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }

                    composable<RoutePractice> {
                        PracticeHubScreen(
                            quizzesState = quizzesState,
                            decksState = decksState,
                            isGenerating = isGeneratingTool,
                            onGenerateQuiz = { subj, topic, diff, count ->
                                viewModel.generateNewQuiz(subj, topic, diff, count) { quizId ->
                                    navController.navigate(RouteQuizPlay(quizId))
                                }
                            },
                            onGenerateDeck = { subj, topic, count ->
                                viewModel.generateFlashcardDeck(subj, topic, count) { deckId ->
                                    navController.navigate(RouteFlashcardStudy(deckId))
                                }
                            },
                            onOpenQuiz = { quizId -> navController.navigate(RouteQuizPlay(quizId)) },
                            onOpenDeck = { deckId -> navController.navigate(RouteFlashcardStudy(deckId)) },
                            onDeleteQuiz = viewModel::deleteQuiz,
                            onDeleteDeck = viewModel::deleteDeck
                        )
                    }

                    composable<RouteQuizPlay> { entry ->
                        val args = entry.toRoute<RouteQuizPlay>()
                        val quizList = (quizzesState as? UiState.Success)?.data ?: emptyList()
                        val quiz = quizList.firstOrNull { it.id == args.quizId }
                        QuizPlayScreen(
                            quiz = quiz,
                            onBack = { navController.popBackStack() },
                            onSubmitScore = viewModel::submitQuizScore
                        )
                    }

                    composable<RouteFlashcardStudy> { entry ->
                        val args = entry.toRoute<RouteFlashcardStudy>()
                        val deckList = (decksState as? UiState.Success)?.data ?: emptyList()
                        val deck = deckList.firstOrNull { it.id == args.deckId }
                        FlashcardStudyScreen(
                            deck = deck,
                            onBack = { navController.popBackStack() },
                            onToggleMastered = viewModel::toggleCardMastered
                        )
                    }

                    composable<RouteLibrary> { entry ->
                        val args = entry.toRoute<RouteLibrary>()
                        LibraryAndAnalyticsScreen(
                            initialTab = args.initialTab,
                            resourcesState = resourcesState,
                            quizzesState = quizzesState,
                            decksState = decksState,
                            progressState = progressState,
                            generatedDiagramState = generatedDiagramState,
                            isGenerating = isGeneratingTool,
                            onGenerateStudyGuide = viewModel::generateAndSaveStudyResource,
                            onGenerateReport = viewModel::generateAiProgressReport,
                            onGenerateDiagram = viewModel::generateEducationalDiagram,
                            onDeleteResource = viewModel::deleteResource,
                            onOpenSettings = { navController.navigate(RouteSettings) },
                            onShowMessage = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }

                    composable<RouteSettings> {
                        SettingsScreen(
                            profileState = profileState,
                            darkThemeEnabled = darkThemeEnabled,
                            updateState = updateState,
                            onCheckForUpdates = onCheckForUpdates,
                            onToggleDarkTheme = onToggleDarkTheme,
                            onSavePreferences = viewModel::savePreferences,
                            onUpdateMemoryNotes = viewModel::updateMemoryNotes,
                            onBack = { navController.popBackStack() },
                            onSignOut = {
                                signOut(
                                    credentialManager = credentialManager,
                                    scope = scope,
                                    onBeforeAuthSignOut = {
                                        viewModel.cleanupBeforeSignOut()
                                    }
                                )
                            }
                        )
                    }

                    composable<RouteLiveVoice> { entry ->
                        val args = entry.toRoute<RouteLiveVoice>()
                        LiveVoiceScreen(
                            subject = args.subject,
                            topic = args.topic,
                            liveState = liveVoiceState,
                            onStartSession = viewModel::startLiveVoiceSession,
                            onStopSession = { viewModel.stopLiveVoiceSession(saveSummaryToChat = true) },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
