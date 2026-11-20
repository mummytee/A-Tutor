package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ai.GeneratedLessonPayload
import com.example.data.BoardElement
import com.example.data.BoardType
import com.example.data.ProgressStatus
import com.example.data.SubjectCatalog
import com.example.data.TopicProgress
import com.example.export.PdfExporter
import com.example.ui.UiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectsScreen(
    progressState: UiState<List<TopicProgress>>,
    onSelectSubject: (String) -> Unit
) {
    val progress = (progressState as? UiState.Success)?.data ?: emptyList()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Subject Library", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Structured topics, lessons & visual boards",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(SubjectCatalog.subjects, key = { it.id }) { subject ->
                val doneCount = progress.count {
                    it.subject.equals(subject.name, true) && it.status == ProgressStatus.COMPLETED.name
                }
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSubject(subject.id) }
                        .testTag("subject_card_${subject.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(subject.accentColorHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.School,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(subject.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                subject.tagline,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${subject.lessons.size} interactive lessons • $doneCount completed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open ${subject.name}")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectDetailScreen(
    subjectId: String,
    progressState: UiState<List<TopicProgress>>,
    onBack: () -> Unit,
    onOpenLesson: (String) -> Unit,
    onAskTutorAboutSubject: (String) -> Unit
) {
    BackHandler { onBack() }
    val subject = SubjectCatalog.findSubject(subjectId)
    val progress = (progressState as? UiState.Success)?.data ?: emptyList()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("subject_detail_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            title = { Text(subject.name, style = MaterialTheme.typography.headlineMedium) }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = Color(subject.accentColorHex).copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(subject.tagline, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Categories: ${subject.categories.joinToString(" • ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { onAskTutorAboutSubject(subject.name) },
                            modifier = Modifier.testTag("open_subject_in_tutor")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Start Open ${subject.name} Tutoring")
                        }
                    }
                }
            }

            items(subject.lessons, key = { it.id }) { lesson ->
                val isCompleted = progress.any {
                    it.topicId == lesson.id && it.status == ProgressStatus.COMPLETED.name
                }
                ElevatedCard(
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenLesson(lesson.id) }
                        .testTag("lesson_card_${lesson.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        lesson.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${lesson.durationMinutes} min",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(lesson.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                lesson.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isCompleted) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Completed",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open lesson")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonDetailScreen(
    lessonId: String,
    lessonState: UiState<GeneratedLessonPayload>?,
    onBack: () -> Unit,
    onLoadLesson: (String, String) -> Unit,
    onMarkCompleted: (String, String, String, Long) -> Unit,
    onLaunchTutorWithBoard: (String, String, BoardType, List<BoardElement>) -> Unit,
    onGenerateLessonQuiz: (String, String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    BackHandler { onBack() }
    val pair = SubjectCatalog.findLesson(lessonId)
    val subjectName = pair?.first?.name ?: "Mathematics"
    val lessonTitle = pair?.second?.title ?: "Interactive Lesson"
    val duration = pair?.second?.durationMinutes?.toLong() ?: 20L

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(lessonId) {
        onLoadLesson(subjectName, lessonTitle)
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        val payload = (lessonState as? UiState.Success)?.data
        if (uri != null && payload != null) {
            scope.launch {
                val body = buildString {
                    appendLine(payload.overview)
                    appendLine()
                    payload.sections.forEach { (h, b) ->
                        appendLine(h.uppercase())
                        appendLine(b)
                        appendLine()
                    }
                }
                PdfExporter.exportDocumentToUri(context, uri, lessonTitle, subjectName, body)
                    .onSuccess { onShowMessage("Lesson PDF exported!") }
                    .onFailure { onShowMessage(it.localizedMessage ?: "PDF export failed.") }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("lesson_back_btn")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            title = {
                Column {
                    Text(lessonTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(subjectName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            },
            actions = {
                if (lessonState is UiState.Success) {
                    IconButton(
                        onClick = { pdfLauncher.launch("${lessonTitle.take(24).replace(" ", "_")}.pdf") },
                        modifier = Modifier.testTag("export_lesson_pdf_btn")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export Lesson PDF")
                    }
                }
            }
        )

        when (lessonState) {
            null, is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Preparing tailored lesson & visual chalkboard...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(lessonState.message, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { onLoadLesson(subjectName, lessonTitle) }) {
                            Text("Retry Lesson")
                        }
                    }
                }
            }
            is UiState.Success -> {
                val payload = lessonState.data
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Overview", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(Modifier.height(4.dp))
                                Text(payload.overview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }

                    items(payload.sections) { (heading, body) ->
                        ElevatedCard(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(heading, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(6.dp))
                                Text(body, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    onLaunchTutorWithBoard(
                                        subjectName,
                                        lessonTitle,
                                        payload.boardType,
                                        payload.boardElements
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().testTag("lesson_open_board_btn")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Open Chalkboard & Discuss with A-Tutor")
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { onGenerateLessonQuiz(subjectName, lessonTitle) },
                                    modifier = Modifier.weight(1f).testTag("lesson_quiz_btn")
                                ) {
                                    Icon(Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Take Quiz")
                                }
                                OutlinedButton(
                                    onClick = { onMarkCompleted(subjectName, lessonId, lessonTitle, duration) },
                                    modifier = Modifier.weight(1f).testTag("lesson_complete_btn")
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Complete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
