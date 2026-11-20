package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.AiTutorService
import com.example.data.FlashcardDeck
import com.example.data.QuizRecord
import com.example.data.ResourceType
import com.example.data.StudyResource
import com.example.data.SubjectCatalog
import com.example.data.TopicProgress
import com.example.data.UserProfile
import com.example.export.PdfExporter
import com.example.ui.UiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryAndAnalyticsScreen(
    initialTab: Int = 0,
    resourcesState: UiState<List<StudyResource>>,
    quizzesState: UiState<List<QuizRecord>>,
    decksState: UiState<List<FlashcardDeck>>,
    progressState: UiState<List<TopicProgress>>,
    generatedDiagramState: UiState<Bitmap>?,
    isGenerating: Boolean,
    onGenerateStudyGuide: (String, String, ResourceType, String) -> Unit,
    onGenerateReport: () -> Unit,
    onGenerateDiagram: (String) -> Unit,
    onDeleteResource: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var viewingResource by remember { mutableStateOf<StudyResource?>(null) }
    var showCreateGuide by remember { mutableStateOf(false) }
    var diagramPrompt by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingPdfResource by remember { mutableStateOf<StudyResource?>(null) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        val res = pendingPdfResource
        if (uri != null && res != null) {
            scope.launch {
                PdfExporter.exportDocumentToUri(context, uri, res.title, res.subject, res.content)
                    .onSuccess { onShowMessage("Saved PDF to device!") }
                    .onFailure { onShowMessage(it.localizedMessage ?: "PDF export failed.") }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Library & Mastery", style = MaterialTheme.typography.headlineMedium) },
            actions = {
                IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("library_settings_btn")) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings & AI Memory")
                }
            }
        )

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Notes & PDFs") },
                icon = { Icon(Icons.Default.Book, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Progress & Reports") },
                icon = { Icon(Icons.Default.Analytics, contentDescription = null) }
            )
        }

        if (selectedTab == 0) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Study Guides & Notes", style = MaterialTheme.typography.titleMedium)
                        FilledTonalButton(
                            onClick = { showCreateGuide = true },
                            enabled = !isGenerating,
                            modifier = Modifier.testTag("new_study_guide_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Generate Guide")
                        }
                    }
                }

                // Educational AI Visual Generator Card
                item {
                    ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("AI Educational Diagram Studio", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = diagramPrompt,
                                    onValueChange = { diagramPrompt = it },
                                    placeholder = { Text("e.g. Plant photosynthesis diagram") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (diagramPrompt.isNotBlank()) onGenerateDiagram(diagramPrompt)
                                    },
                                    modifier = Modifier.testTag("generate_diagram_btn")
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = "Generate Diagram")
                                }
                            }
                            if (generatedDiagramState != null) {
                                Spacer(Modifier.height(10.dp))
                                when (generatedDiagramState) {
                                    is UiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Synthesizing educational diagram (30-60s)...", style = MaterialTheme.typography.bodySmall)
                                    }
                                    is UiState.Success -> Image(
                                        bitmap = generatedDiagramState.data.asImageBitmap(),
                                        contentDescription = "Generated diagram",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.medium)
                                    )
                                    is UiState.Error -> Text(
                                        generatedDiagramState.message,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                when (resourcesState) {
                    is UiState.Loading -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    is UiState.Error -> item { Text(resourcesState.message, color = MaterialTheme.colorScheme.error) }
                    is UiState.Success -> {
                        val itemsList = resourcesState.data
                        if (itemsList.isEmpty()) {
                            item {
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "No saved notes or guides yet. Tap 'Generate Guide' or save any tutor explanation.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(18.dp)
                                    )
                                }
                            }
                        } else {
                            items(itemsList, key = { it.id }) { res ->
                                ElevatedCard(
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewingResource = res }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "${res.parsedType().label} • ${res.subject}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(res.title, style = MaterialTheme.typography.titleSmall)
                                            if (res.summary.isNotBlank()) {
                                                Text(
                                                    res.summary,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2
                                                )
                                            }
                                        }
                                        IconButton(onClick = {
                                            pendingPdfResource = res
                                            pdfLauncher.launch("${res.title.take(24).replace(" ", "_")}.pdf")
                                        }) {
                                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                                        }
                                        IconButton(onClick = { onDeleteResource(res.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete resource")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val quizzes = (quizzesState as? UiState.Success)?.data ?: emptyList()
            val decks = (decksState as? UiState.Success)?.data ?: emptyList()
            val progress = (progressState as? UiState.Success)?.data ?: emptyList()
            val reports = (resourcesState as? UiState.Success)?.data
                ?.filter { it.resourceType == ResourceType.PROGRESS_REPORT.name } ?: emptyList()

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    ElevatedCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_mastery_emblem),
                                contentDescription = "Mastery achievement emblem",
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Growth Analytics", style = MaterialTheme.typography.titleLarge)
                                val completedQuizzes = quizzes.count { it.completed }
                                val avg = if (completedQuizzes == 0) 0 else quizzes.filter { it.completed }.map { it.score.toInt() }.average().toInt()
                                Text(
                                    "Avg Score: $avg% • ${progress.size} topics tracked • ${decks.sumOf { it.masteredCount }} cards mastered",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = onGenerateReport,
                        enabled = !isGenerating,
                        modifier = Modifier.fillMaxWidth().testTag("generate_ai_report_btn")
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Generate Fresh AI Progress Report")
                    }
                }

                if (progress.isNotEmpty()) {
                    item { Text("Topic Understanding", style = MaterialTheme.typography.titleMedium) }
                    items(progress, key = { it.id }) { tp ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${tp.subject}: ${tp.topicTitle}", style = MaterialTheme.typography.titleSmall)
                                    Text("${tp.masteryPercent}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { tp.masteryPercent.toFloat() / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                item { Text("AI Progress Reports", style = MaterialTheme.typography.titleMedium) }
                items(reports, key = { it.id }) { rep ->
                    ElevatedCard(
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().clickable { viewingResource = rep }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(rep.title, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(rep.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showCreateGuide) {
        var subject by remember { mutableStateOf("Physics") }
        var title by remember { mutableStateOf("") }
        var type by remember { mutableStateOf(ResourceType.STUDY_GUIDE) }
        AlertDialog(
            onDismissRequest = { showCreateGuide = false },
            title = { Text("Generate Study Resource") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SubjectCatalog.subjects.forEach { s ->
                            FilterChip(selected = subject == s.name, onClick = { subject = s.name }, label = { Text(s.name, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(ResourceType.STUDY_GUIDE, ResourceType.CONCEPT_MAP, ResourceType.LESSON_NOTES).forEach { rt ->
                            FilterChip(selected = type == rt, onClick = { type = rt }, label = { Text(rt.label, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Topic / Title") },
                        placeholder = { Text("e.g. Electromagnetism Summary") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val finalTitle = title.ifBlank { "$subject ${type.label}" }
                    showCreateGuide = false
                    onGenerateStudyGuide(
                        finalTitle,
                        subject,
                        type,
                        "Create a clear, structured ${type.label} for $finalTitle in $subject with key concepts, formulas, examples, and common mistakes."
                    )
                }) { Text("Generate") }
            },
            dismissButton = { TextButton(onClick = { showCreateGuide = false }) { Text("Cancel") } }
        )
    }

    viewingResource?.let { res ->
        AlertDialog(
            onDismissRequest = { viewingResource = null },
            title = { Text(res.title) },
            text = {
                Column(modifier = Modifier.height(340.dp).verticalScroll(rememberScrollState())) {
                    Text(res.content, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                Button(onClick = {
                    pendingPdfResource = res
                    pdfLauncher.launch("${res.title.take(24).replace(" ", "_")}.pdf")
                }) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export PDF")
                }
            },
            dismissButton = { TextButton(onClick = { viewingResource = null }) { Text("Close") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profileState: UiState<UserProfile?>,
    darkThemeEnabled: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onSavePreferences: (String, String, String, Long, String, List<String>) -> Unit,
    onUpdateMemoryNotes: (List<String>) -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit
) {
    BackHandler { onBack() }
    val profile = (profileState as? UiState.Success)?.data
    var displayName by remember(profile?.displayName) { mutableStateOf(profile?.displayName ?: "Student") }
    var level by remember(profile?.learningLevel) { mutableStateOf(profile?.learningLevel ?: "Intermediate") }
    var style by remember(profile?.learningStyle) { mutableStateOf(profile?.learningStyle ?: "Visual") }
    var goalMinutes by remember(profile?.dailyGoalMinutes) { mutableFloatStateOf((profile?.dailyGoalMinutes ?: 30L).toFloat()) }
    var model by remember(profile?.preferredModel) { mutableStateOf(profile?.preferredModel ?: AiTutorService.MODEL_DEFAULT) }
    val memoryNotes = remember(profile?.memoryNotes) {
        mutableStateListOf<String>().apply { addAll(profile?.memoryNotes ?: emptyList()) }
    }
    var newMemoryText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_btn")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            title = { Text("Settings & AI Memory", style = MaterialTheme.typography.headlineMedium) }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Student Profile", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Learning Level", style = MaterialTheme.typography.labelLarge)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Beginner", "Intermediate", "Advanced", "University").forEach { lv ->
                                FilterChip(selected = level == lv, onClick = { level = lv }, label = { Text(lv) })
                            }
                        }
                        Text("Preferred Teaching Style", style = MaterialTheme.typography.labelLarge)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Visual", "Step-by-Step", "Socratic", "Analogy-Rich").forEach { st ->
                                FilterChip(selected = style == st, onClick = { style = st }, label = { Text(st) })
                            }
                        }
                        Text("Daily Study Goal: ${goalMinutes.toInt()} minutes", style = MaterialTheme.typography.labelLarge)
                        Slider(value = goalMinutes, onValueChange = { goalMinutes = it }, valueRange = 10f..120f)
                        Text("AI Tutor Model", style = MaterialTheme.typography.labelLarge)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AiTutorService.AVAILABLE_TUTOR_MODELS.forEach { (id, label) ->
                                FilterChip(selected = model == id, onClick = { model = id }, label = { Text(label) })
                            }
                        }
                        Button(
                            onClick = {
                                onSavePreferences(
                                    displayName,
                                    level,
                                    style,
                                    goalMinutes.toLong(),
                                    model,
                                    profile?.focusSubjects ?: listOf("Mathematics", "Physics")
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("save_preferences_btn")
                        ) { Text("Save Learning Preferences") }
                    }
                }
            }

            item {
                ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Obsidian Dark Theme", style = MaterialTheme.typography.titleSmall)
                            Text("Easier on eyes during late-night study", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = darkThemeEnabled, onCheckedChange = onToggleDarkTheme)
                    }
                }
            }

            item {
                ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Persistent AI Memory & Privacy", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "A-Tutor remembers your pace and study needs across sessions. View, add, or delete memories anytime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newMemoryText,
                                onValueChange = { newMemoryText = it },
                                placeholder = { Text("Add a learning preference or goal...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = {
                                if (newMemoryText.isNotBlank()) {
                                    memoryNotes.add(newMemoryText.trim())
                                    newMemoryText = ""
                                    onUpdateMemoryNotes(memoryNotes.toList())
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add memory note")
                            }
                        }
                        memoryNotes.forEachIndexed { idx, note ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    IconButton(onClick = {
                                        memoryNotes.removeAt(idx)
                                        onUpdateMemoryNotes(memoryNotes.toList())
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete memory note", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth().testTag("sign_out_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out (${profile?.email ?: "Google Account"})")
                }
            }
        }
    }
}
