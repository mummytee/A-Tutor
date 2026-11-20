package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.BoardElement
import com.example.data.BoardType
import com.example.data.ChatMessage
import com.example.data.Conversation
import com.example.data.ResourceType
import com.example.data.SubjectCatalog
import com.example.ui.UiState
import com.example.ui.components.Interactive3DModelStudio
import com.example.ui.components.InteractiveChalkboard
import kotlinx.coroutines.launch

enum class WorkspaceMode(val label: String) {
    CHAT("Chat"),
    BOARD("2D Board"),
    MODEL_3D("3D Model"),
    SPLIT("Split")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorWorkspaceScreen(
    conversationsState: UiState<List<Conversation>>,
    activeConversation: Conversation?,
    messages: List<ChatMessage>,
    streamingReply: String?,
    isThinking: Boolean,
    tutorError: String?,
    groundingEnabled: Boolean,
    quickHints: List<String>,
    speakingMessageId: String?,
    isRecordingAudio: Boolean,
    isTranscribing: Boolean,
    transcribedDraft: String?,
    generatedDiagramState: UiState<Bitmap>?,
    onClearTranscribedDraft: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onCreateConversation: suspend (String, String, String, BoardType) -> String?,
    onRenameConversation: (String, String, String, String) -> Unit,
    onToggleStar: (Conversation) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onSendMessage: (String, Bitmap?, String) -> Unit,
    onToggleGrounding: () -> Unit,
    onUpdateBoard: (BoardType, List<BoardElement>) -> Unit,
    onUpdateScene3D: (String) -> Unit,
    onExplainBoard: (Bitmap) -> Unit,
    onSpeakMessage: (String, String) -> Unit,
    onStartVoiceRecording: () -> Unit,
    onStopVoiceRecording: () -> Unit,
    onOpenLiveVoice: (String, String) -> Unit,
    onSaveMessageToLibrary: (String, String, ResourceType, String) -> Unit,
    onGenerateQuizFromTopic: (String, String) -> Unit,
    onGenerateDeckFromTopic: (String, String) -> Unit,
    onGenerateDiagram: (String) -> Unit,
    onDismissDiagram: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var workspaceMode by remember { mutableStateOf(WorkspaceMode.CHAT) }
    var wideVisualIs3D by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var showToolsMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var selectedSubject by remember { mutableStateOf(activeConversation?.subject ?: "Mathematics") }

    LaunchedEffect(activeConversation?.id) {
        if (activeConversation != null) {
            selectedSubject = activeConversation.subject
        }
    }

    LaunchedEffect(transcribedDraft) {
        if (!transcribedDraft.isNullOrBlank()) {
            inputText = if (inputText.isBlank()) transcribedDraft else "$inputText $transcribedDraft"
            onClearTranscribedDraft()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            attachedBitmap = loadScaledBitmap(context, uri)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onStartVoiceRecording()
    }

    val conversations = (conversationsState as? UiState.Success)?.data ?: emptyList()
    val boardType = activeConversation?.parsedBoardType() ?: BoardType.GENERAL
    val boardElements = activeConversation?.parsedBoardElements() ?: emptyList()
    val scene3dJson = activeConversation?.scene3dJson ?: ""
    val hasScene3D = scene3dJson.isNotBlank()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = activeConversation?.title ?: "A-Tutor Classroom",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${activeConversation?.subject ?: selectedSubject} • ${if (groundingEnabled) "Search & Maps ON" else "Chalkboard Tools ON"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = { showHistorySheet = true },
                    modifier = Modifier.testTag("open_chat_history_btn")
                ) {
                    Icon(Icons.Default.History, contentDescription = "Conversation History")
                }
            },
            actions = {
                IconButton(
                    onClick = { showNewChatDialog = true },
                    modifier = Modifier.testTag("new_conversation_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Lesson Chat")
                }
                IconButton(
                    onClick = {
                        onOpenLiveVoice(
                            activeConversation?.subject ?: selectedSubject,
                            activeConversation?.topic ?: "Voice Coaching"
                        )
                    },
                    modifier = Modifier.testTag("tutor_live_voice_btn")
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = "Live Voice Session", tint = MaterialTheme.colorScheme.primary)
                }
                Box {
                    IconButton(
                        onClick = { showToolsMenu = true },
                        modifier = Modifier.testTag("tutor_tools_menu_btn")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Generate Learning Materials")
                    }
                    DropdownMenu(
                        expanded = showToolsMenu,
                        onDismissRequest = { showToolsMenu = false }
                    ) {
                        val subj = activeConversation?.subject ?: selectedSubject
                        val topic = activeConversation?.topic?.ifBlank { activeConversation.title } ?: subj
                        DropdownMenuItem(
                            text = { Text("Generate Quiz from Lesson") },
                            leadingIcon = { Icon(Icons.Default.Quiz, contentDescription = null) },
                            onClick = {
                                showToolsMenu = false
                                onGenerateQuizFromTopic(subj, topic)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Create Flashcards from Lesson") },
                            leadingIcon = { Icon(Icons.Default.Style, contentDescription = null) },
                            onClick = {
                                showToolsMenu = false
                                onGenerateDeckFromTopic(subj, topic)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Generate Visual Diagram (AI Image)") },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                            onClick = {
                                showToolsMenu = false
                                onGenerateDiagram("$subj concept diagram: $topic")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Save Lesson Summary to Library") },
                            leadingIcon = { Icon(Icons.Default.BookmarkAdd, contentDescription = null) },
                            onClick = {
                                showToolsMenu = false
                                val transcript = messages.joinToString("\n\n") {
                                    "${if (it.role == "user") "Student" else "A-Tutor"}: ${it.text}"
                                }.ifBlank { "Study notes for $topic" }
                                onSaveMessageToLibrary(
                                    activeConversation?.title ?: "$subj Notes",
                                    subj,
                                    ResourceType.LESSON_NOTES,
                                    transcript
                                )
                            }
                        )
                    }
                }
            }
        )

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val isWide = maxWidth >= 820.dp

            if (isWide) {
                // Wide screen (Tablet / Foldable): Side-by-side Split Classroom
                Row(modifier = Modifier.fillMaxSize()) {
                    ChatPane(
                        messages = messages,
                        streamingReply = streamingReply,
                        isThinking = isThinking,
                        tutorError = tutorError,
                        groundingEnabled = groundingEnabled,
                        quickHints = quickHints,
                        speakingMessageId = speakingMessageId,
                        isRecordingAudio = isRecordingAudio,
                        isTranscribing = isTranscribing,
                        inputText = inputText,
                        attachedBitmap = attachedBitmap,
                        onInputChange = { inputText = it },
                        onClearBitmap = { attachedBitmap = null },
                        onPickImage = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onToggleMic = {
                            if (isRecordingAudio) {
                                onStopVoiceRecording()
                            } else {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) onStartVoiceRecording()
                                else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onToggleGrounding = onToggleGrounding,
                        onSend = {
                            val bmp = attachedBitmap
                            val textToSend = inputText
                            inputText = ""
                            attachedBitmap = null
                            onSendMessage(textToSend, bmp, selectedSubject)
                        },
                        onQuickHintClick = { hint ->
                            onSendMessage(hint, null, selectedSubject)
                        },
                        onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } },
                        onSpeakMessage = onSpeakMessage,
                        onSaveToLibrary = { msgText ->
                            onSaveMessageToLibrary(
                                "${activeConversation?.subject ?: selectedSubject} Explanation",
                                activeConversation?.subject ?: selectedSubject,
                                ResourceType.STUDY_GUIDE,
                                msgText
                            )
                        },
                        onSwitchToBoard = { wideVisualIs3D = false },
                        onSwitchTo3D = { wideVisualIs3D = true },
                        hasScene3D = hasScene3D,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !wideVisualIs3D,
                                onClick = { wideVisualIs3D = false },
                                leadingIcon = { Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("2D Chalkboard") }
                            )
                            FilterChip(
                                selected = wideVisualIs3D,
                                onClick = { wideVisualIs3D = true },
                                leadingIcon = { Icon(Icons.Default.ViewInAr, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("3D Model Lab") }
                            )
                        }
                        if (wideVisualIs3D) {
                            Interactive3DModelStudio(
                                scene3dJson = scene3dJson,
                                subject = activeConversation?.subject ?: selectedSubject,
                                onSelectPreset = onUpdateScene3D,
                                onAskAiBuild3D = { prompt ->
                                    onSendMessage(prompt, null, activeConversation?.subject ?: selectedSubject)
                                },
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                        } else {
                            InteractiveChalkboard(
                                boardType = boardType,
                                elements = boardElements,
                                onBoardChanged = onUpdateBoard,
                                onAskAiExplainBoard = onExplainBoard,
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
                // Phone layout: Clean Mode Switcher (Chat | Chalkboard | Split)
                Column(modifier = Modifier.fillMaxSize()) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        WorkspaceMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = workspaceMode == mode,
                                onClick = { workspaceMode = mode },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = WorkspaceMode.entries.size
                                ),
                                modifier = Modifier.testTag("workspace_tab_${mode.name.lowercase()}")
                            ) {
                                Text(mode.label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    when (workspaceMode) {
                        WorkspaceMode.CHAT -> {
                            ChatPane(
                                messages = messages,
                                streamingReply = streamingReply,
                                isThinking = isThinking,
                                tutorError = tutorError,
                                groundingEnabled = groundingEnabled,
                                quickHints = quickHints,
                                speakingMessageId = speakingMessageId,
                                isRecordingAudio = isRecordingAudio,
                                isTranscribing = isTranscribing,
                                inputText = inputText,
                                attachedBitmap = attachedBitmap,
                                onInputChange = { inputText = it },
                                onClearBitmap = { attachedBitmap = null },
                                onPickImage = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onToggleMic = {
                                    if (isRecordingAudio) {
                                        onStopVoiceRecording()
                                    } else {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) onStartVoiceRecording()
                                        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                onToggleGrounding = onToggleGrounding,
                                onSend = {
                                    val bmp = attachedBitmap
                                    val textToSend = inputText
                                    inputText = ""
                                    attachedBitmap = null
                                    onSendMessage(textToSend, bmp, selectedSubject)
                                },
                                onQuickHintClick = { hint -> onSendMessage(hint, null, selectedSubject) },
                                onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } },
                                onSpeakMessage = onSpeakMessage,
                                onSaveToLibrary = { msgText ->
                                    onSaveMessageToLibrary(
                                        "${activeConversation?.subject ?: selectedSubject} Explanation",
                                        activeConversation?.subject ?: selectedSubject,
                                        ResourceType.STUDY_GUIDE,
                                        msgText
                                    )
                                },
                                onSwitchToBoard = { workspaceMode = WorkspaceMode.BOARD },
                                onSwitchTo3D = { workspaceMode = WorkspaceMode.MODEL_3D },
                                hasScene3D = hasScene3D,
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                        }
                        WorkspaceMode.BOARD -> {
                            InteractiveChalkboard(
                                boardType = boardType,
                                elements = boardElements,
                                onBoardChanged = onUpdateBoard,
                                onAskAiExplainBoard = { bmp ->
                                    onExplainBoard(bmp)
                                    workspaceMode = WorkspaceMode.CHAT
                                },
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                        }
                        WorkspaceMode.MODEL_3D -> {
                            Interactive3DModelStudio(
                                scene3dJson = scene3dJson,
                                subject = activeConversation?.subject ?: selectedSubject,
                                onSelectPreset = onUpdateScene3D,
                                onAskAiBuild3D = { prompt ->
                                    onSendMessage(prompt, null, activeConversation?.subject ?: selectedSubject)
                                    workspaceMode = WorkspaceMode.CHAT
                                },
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                        }
                        WorkspaceMode.SPLIT -> {
                            InteractiveChalkboard(
                                boardType = boardType,
                                elements = boardElements,
                                onBoardChanged = onUpdateBoard,
                                onAskAiExplainBoard = onExplainBoard,
                                modifier = Modifier.weight(0.48f).fillMaxWidth()
                            )
                            HorizontalDivider()
                            ChatPane(
                                messages = messages,
                                streamingReply = streamingReply,
                                isThinking = isThinking,
                                tutorError = tutorError,
                                groundingEnabled = groundingEnabled,
                                quickHints = emptyList(),
                                speakingMessageId = speakingMessageId,
                                isRecordingAudio = isRecordingAudio,
                                isTranscribing = isTranscribing,
                                inputText = inputText,
                                attachedBitmap = attachedBitmap,
                                onInputChange = { inputText = it },
                                onClearBitmap = { attachedBitmap = null },
                                onPickImage = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onToggleMic = {
                                    if (isRecordingAudio) {
                                        onStopVoiceRecording()
                                    } else {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) onStartVoiceRecording()
                                        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                onToggleGrounding = onToggleGrounding,
                                onSend = {
                                    val bmp = attachedBitmap
                                    val textToSend = inputText
                                    inputText = ""
                                    attachedBitmap = null
                                    onSendMessage(textToSend, bmp, selectedSubject)
                                },
                                onQuickHintClick = {},
                                onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } },
                                onSpeakMessage = onSpeakMessage,
                                onSaveToLibrary = {},
                                onSwitchToBoard = { workspaceMode = WorkspaceMode.BOARD },
                                onSwitchTo3D = { workspaceMode = WorkspaceMode.MODEL_3D },
                                hasScene3D = hasScene3D,
                                modifier = Modifier.weight(0.52f).fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    // Conversation History Drawer/Sheet
    if (showHistorySheet) {
        var searchQuery by remember { mutableStateOf("") }
        var filterSubject by remember { mutableStateOf("All") }
        ModalBottomSheet(onDismissRequest = { showHistorySheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Conversations", style = MaterialTheme.typography.headlineSmall)
                    Button(
                        onClick = {
                            showHistorySheet = false
                            showNewChatDialog = true
                        },
                        modifier = Modifier.testTag("sheet_new_chat_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Lesson")
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search chats or topics...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("All", "Starred").plus(SubjectCatalog.subjects.map { it.name }).forEach { chip ->
                        FilterChip(
                            selected = filterSubject == chip,
                            onClick = { filterSubject = chip },
                            label = { Text(chip, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                val filtered = conversations.filter { conv ->
                    val matchesQuery = searchQuery.isBlank() ||
                        conv.title.contains(searchQuery, true) ||
                        conv.subject.contains(searchQuery, true) ||
                        conv.topic.contains(searchQuery, true)
                    val matchesFilter = when (filterSubject) {
                        "All" -> true
                        "Starred" -> conv.starred
                        else -> conv.subject.equals(filterSubject, true)
                    }
                    matchesQuery && matchesFilter
                }
                LazyColumn(
                    modifier = Modifier.height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { conv ->
                        val isActive = conv.id == activeConversation?.id
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectConversation(conv.id)
                                    showHistorySheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(conv.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${conv.subject} • ${conv.messageCount} msgs",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onToggleStar(conv) }) {
                                    Icon(
                                        if (conv.starred) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Star conversation",
                                        tint = if (conv.starred) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { renameTarget = conv }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename conversation")
                                }
                                IconButton(onClick = { onDeleteConversation(conv.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete conversation")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // New Conversation Dialog
    if (showNewChatDialog) {
        var newTitle by remember { mutableStateOf("") }
        var newTopic by remember { mutableStateOf("") }
        var newSubject by remember { mutableStateOf(selectedSubject) }
        AlertDialog(
            onDismissRequest = { showNewChatDialog = false },
            title = { Text("Start New Tutoring Session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SubjectCatalog.subjects.forEach { subj ->
                            FilterChip(
                                selected = newSubject == subj.name,
                                onClick = { newSubject = subj.name },
                                label = { Text(subj.name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Session Title") },
                        placeholder = { Text("e.g. Calculus Chain Rule") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_chat_title_input")
                    )
                    OutlinedTextField(
                        value = newTopic,
                        onValueChange = { newTopic = it },
                        label = { Text("Topic / Goal") },
                        placeholder = { Text("What would you like to master?") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val title = newTitle.ifBlank { "$newSubject Study" }
                            val subjDef = SubjectCatalog.findSubject(newSubject)
                            onCreateConversation(title, newSubject, newTopic, subjDef.defaultBoardType)
                            showNewChatDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_new_chat_btn")
                ) {
                    Text("Start Session")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewChatDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Rename Conversation Dialog
    renameTarget?.let { target ->
        var editedTitle by remember(target.id) { mutableStateOf(target.title) }
        var editedTopic by remember(target.id) { mutableStateOf(target.topic) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editedTitle,
                        onValueChange = { editedTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editedTopic,
                        onValueChange = { editedTopic = it },
                        label = { Text("Topic") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onRenameConversation(target.id, editedTitle, target.subject, editedTopic)
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    // AI-Generated Educational Diagram Modal
    if (generatedDiagramState != null) {
        AlertDialog(
            onDismissRequest = onDismissDiagram,
            title = { Text("AI Educational Illustration") },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (generatedDiagramState) {
                        is UiState.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(10.dp))
                            Text("Generating diagram (may take 30-60s)...", style = MaterialTheme.typography.bodySmall)
                        }
                        is UiState.Success -> Image(
                            bitmap = generatedDiagramState.data.asImageBitmap(),
                            contentDescription = "AI Generated Educational Diagram",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium)
                        )
                        is UiState.Error -> Text(
                            generatedDiagramState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissDiagram) { Text("Close") }
            }
        )
    }
}

@Composable
private fun ChatPane(
    messages: List<ChatMessage>,
    streamingReply: String?,
    isThinking: Boolean,
    tutorError: String?,
    groundingEnabled: Boolean,
    quickHints: List<String>,
    speakingMessageId: String?,
    isRecordingAudio: Boolean,
    isTranscribing: Boolean,
    inputText: String,
    attachedBitmap: Bitmap?,
    onInputChange: (String) -> Unit,
    onClearBitmap: () -> Unit,
    onPickImage: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleGrounding: () -> Unit,
    onSend: () -> Unit,
    onQuickHintClick: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onSpeakMessage: (String, String) -> Unit,
    onSaveToLibrary: (String) -> Unit,
    onSwitchToBoard: () -> Unit,
    onSwitchTo3D: () -> Unit,
    hasScene3D: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, streamingReply) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty() && streamingReply == null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Hello! I'm A-Tutor.", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Ask any question, upload a photo of your problem, speak into the mic, or ask me to draw 2D diagrams & interactive 3D models!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "Draw a parabola y = x^2 - 4 on the board",
                                    "Build an interactive 3D Carbon-12 atom model",
                                    "Build a 3D DNA double helix and explain base pairs",
                                    "How do Newton's 3 laws work?"
                                ).forEach { sample ->
                                    AssistChip(
                                        onClick = { onQuickHintClick(sample) },
                                        label = { Text(sample, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                val isUser = msg.role == "user"
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    Surface(
                        color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isUser) 18.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 18.dp
                        ),
                        modifier = Modifier.widthIn(max = 560.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(text = msg.text, style = MaterialTheme.typography.bodyMedium)
                            if (msg.hasBoardAction) {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AssistChip(
                                        onClick = onSwitchToBoard,
                                        leadingIcon = { Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        label = { Text("View 2D Board", style = MaterialTheme.typography.labelSmall) }
                                    )
                                    if (hasScene3D) {
                                        AssistChip(
                                            onClick = onSwitchTo3D,
                                            leadingIcon = { Icon(Icons.Default.ViewInAr, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                            label = { Text("Inspect 3D Model", style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                            if (msg.groundingSources.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    msg.groundingSources.forEach { raw ->
                                        val parts = raw.split(" | ", limit = 2)
                                        val title = parts.firstOrNull() ?: "Source"
                                        val url = parts.getOrNull(1) ?: ""
                                        AssistChip(
                                            onClick = { if (url.isNotBlank()) onOpenUrl(url) },
                                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                            label = { Text(title.take(26), style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!isUser) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isSpeaking = speakingMessageId == msg.id
                            TextButton(onClick = { onSpeakMessage(msg.id, msg.text) }) {
                                Icon(
                                    if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Listen",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (isSpeaking) "Stop" else "Listen", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { onSaveToLibrary(msg.text) }) {
                                Icon(Icons.Default.BookmarkAdd, contentDescription = "Save", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save Note", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            if (streamingReply != null || isThinking) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.widthIn(max = 560.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (streamingReply.isNullOrBlank()) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("A-Tutor is thinking & sketching...", style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text(streamingReply, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            if (tutorError != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = tutorError,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Quick Suggested Follow-up Chips
        if (quickHints.isNotEmpty() && !isThinking) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickHints.forEach { hint ->
                    AssistChip(
                        onClick = { onQuickHintClick(hint) },
                        label = { Text(hint, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        // Attached Image Preview
        if (attachedBitmap != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    bitmap = attachedBitmap.asImageBitmap(),
                    contentDescription = "Attached study photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text("Homework image attached", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onClearBitmap) {
                    Icon(Icons.Default.Close, contentDescription = "Remove attachment")
                }
            }
        }

        // Composer Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = groundingEnabled,
                        onClick = onToggleGrounding,
                        leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Search & Maps", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("toggle_grounding_chip")
                    )
                    if (isRecordingAudio) {
                        Text(
                            "Listening... tap mic to transcribe",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (isTranscribing) {
                        Text(
                            "Transcribing speech...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPickImage,
                        modifier = Modifier.testTag("attach_image_btn")
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach photo")
                    }
                    IconButton(
                        onClick = onToggleMic,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (isRecordingAudio) MaterialTheme.colorScheme.errorContainer
                                else Color.Transparent
                            )
                            .testTag("mic_transcribe_btn")
                    ) {
                        Icon(
                            if (isRecordingAudio) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Record voice question",
                            tint = if (isRecordingAudio) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        placeholder = { Text("Ask A-Tutor anything...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tutor_chat_input")
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = onSend,
                        enabled = !isThinking && (inputText.isNotBlank() || attachedBitmap != null),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("send_tutor_message_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

private fun loadScaledBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        val maxDim = 1024
        if (raw.width <= maxDim && raw.height <= maxDim) raw
        else {
            val scale = maxDim.toFloat() / maxOf(raw.width, raw.height)
            Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true)
        }
    } catch (_: Exception) {
        null
    }
}
