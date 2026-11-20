package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Conversation
import com.example.data.FlashcardDeck
import com.example.data.QuizRecord
import com.example.data.SubjectCatalog
import com.example.data.TopicProgress
import com.example.data.UserProfile
import com.example.ui.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    profileState: UiState<UserProfile?>,
    conversationsState: UiState<List<Conversation>>,
    quizzesState: UiState<List<QuizRecord>>,
    decksState: UiState<List<FlashcardDeck>>,
    progressState: UiState<List<TopicProgress>>,
    onOpenTutor: (String?) -> Unit,
    onStartSubjectChat: (String) -> Unit,
    onOpenLiveVoice: (String, String) -> Unit,
    onOpenPractice: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val profile = (profileState as? UiState.Success)?.data
    val conversations = (conversationsState as? UiState.Success)?.data ?: emptyList()
    val quizzes = (quizzesState as? UiState.Success)?.data ?: emptyList()
    val decks = (decksState as? UiState.Success)?.data ?: emptyList()
    val progressList = (progressState as? UiState.Success)?.data ?: emptyList()

    val avgScore = quizzes.filter { it.completed }.let { done ->
        if (done.isEmpty()) 0 else done.map { it.score.toInt() }.average().toInt()
    }
    val completedLessons = progressList.count { it.status == "COMPLETED" }
    val streak = profile?.streakDays ?: 1L
    val totalMinutes = profile?.totalMinutesStudied ?: 0L
    val goalMinutes = (profile?.dailyGoalMinutes ?: 30L).coerceAtLeast(1L)

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Welcome back, ${profile?.displayName?.substringBefore(" ") ?: "Scholar"}",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "${profile?.learningLevel ?: "Intermediate"} • ${profile?.learningStyle ?: "Visual"} Mode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = onOpenAnalytics,
                    modifier = Modifier.testTag("dashboard_analytics_button")
                ) {
                    Icon(Icons.Default.Analytics, contentDescription = "Progress & AI Reports")
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("dashboard_settings_button")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings & AI Memory")
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Chalkboard Classroom Card
            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenTutor(conversations.firstOrNull()?.id) }
                        .testTag("hero_continue_tutor")
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_chalkboard_banner),
                            contentDescription = "Interactive chalkboard banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(168.dp)
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xE60B1E1A),
                                            Color(0x990B1E1A),
                                            Color(0x440B1E1A)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .padding(20.dp)
                                .fillMaxWidth()
                        ) {
                            Surface(
                                color = Color(0x33F4B860),
                                shape = RoundedCornerShape(50)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFFF4B860),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        "AI CLASSROOM READY",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFF4B860)
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = if (conversations.isNotEmpty())
                                    "Resume: ${conversations.first().title}"
                                else
                                    "Start Your First Visual Lesson",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Interactive chalkboard, step-by-step reasoning & voice Q&A",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilledTonalButton(
                                    onClick = { onOpenTutor(conversations.firstOrNull()?.id) },
                                    modifier = Modifier.testTag("open_tutor_workspace_btn")
                                ) {
                                    Text("Open Workspace")
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                FilledTonalButton(
                                    onClick = {
                                        val first = conversations.firstOrNull()
                                        onOpenLiveVoice(
                                            first?.subject ?: "Mathematics",
                                            first?.topic ?: "Interactive Coaching"
                                        )
                                    },
                                    modifier = Modifier.testTag("dashboard_live_voice_btn")
                                ) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Live Voice")
                                }
                            }
                        }
                    }
                }
            }

            // Metrics & Daily Goal Ring Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ElevatedCard(
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val progress = (totalMinutes.toFloat() / goalMinutes.toFloat()).coerceIn(0.08f, 1f)
                            val ringColor = MaterialTheme.colorScheme.primary
                            val trackColor = MaterialTheme.colorScheme.surfaceVariant
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawArc(
                                        color = trackColor,
                                        startAngle = 0f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        color = ringColor,
                                        startAngle = -90f,
                                        sweepAngle = 360f * progress,
                                        useCenter = false,
                                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                                Text(
                                    text = "${totalMinutes}m",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Study Time", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Goal: ${goalMinutes}m/day",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.LocalFireDepartment,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        "$streak day streak",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }

                    ElevatedCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onOpenAnalytics),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Mastery Snapshot", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (quizzes.any { it.completed }) "$avgScore% Quiz Avg" else "${decks.size} Decks Ready",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "$completedLessons lessons • ${quizzes.size} quizzes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Quick Tools Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionTile(
                        icon = Icons.Default.ChatBubbleOutline,
                        label = "New Chat",
                        modifier = Modifier.weight(1f).testTag("quick_new_chat"),
                        onClick = { onStartSubjectChat("Mathematics") }
                    )
                    QuickActionTile(
                        icon = Icons.Default.Quiz,
                        label = "AI Quiz",
                        modifier = Modifier.weight(1f).testTag("quick_ai_quiz"),
                        onClick = onOpenPractice
                    )
                    QuickActionTile(
                        icon = Icons.Default.Analytics,
                        label = "AI Report",
                        modifier = Modifier.weight(1f).testTag("quick_ai_report"),
                        onClick = onOpenAnalytics
                    )
                }
            }

            // Subject Mastery Bar Chart
            item {
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Subject Mastery", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Analytics",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(onClick = onOpenAnalytics)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        val chartSubjects = SubjectCatalog.subjects.take(5)
                        val barValues = chartSubjects.map { subj ->
                            val matching = progressList.filter { it.subject.equals(subj.name, true) }
                            val quizMatch = quizzes.filter { it.completed && it.subject.equals(subj.name, true) }
                            when {
                                matching.isNotEmpty() -> matching.map { it.masteryPercent.toFloat() }.average().toFloat() / 100f
                                quizMatch.isNotEmpty() -> quizMatch.map { it.score.toFloat() }.average().toFloat() / 100f
                                else -> 0.15f
                            }
                        }
                        val barColor = MaterialTheme.colorScheme.primary
                        val track = MaterialTheme.colorScheme.surfaceVariant
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(86.dp)
                        ) {
                            val slotW = size.width / chartSubjects.size
                            val barW = slotW * 0.48f
                            barValues.forEachIndexed { idx, pct ->
                                val left = idx * slotW + (slotW - barW) / 2f
                                drawRoundRect(
                                    color = track,
                                    topLeft = Offset(left, 0f),
                                    size = Size(barW, size.height),
                                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                                )
                                val barH = (size.height * pct.coerceIn(0.08f, 1f))
                                drawRoundRect(
                                    color = barColor,
                                    topLeft = Offset(left, size.height - barH),
                                    size = Size(barW, barH),
                                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            chartSubjects.forEach { subj ->
                                Text(
                                    text = subj.name.take(6),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Explore Subjects Horizontal Strip
            item {
                Text("Start Learning by Subject", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SubjectCatalog.subjects.forEach { subject ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = Color(subject.accentColorHex).copy(alpha = 0.12f),
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { onStartSubjectChat(subject.name) }
                                .testTag("subject_chip_${subject.id}")
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                Text(
                                    text = subject.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${subject.lessons.size} lessons",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Recent Conversations
            item {
                Text("Recent Tutoring Sessions", style = MaterialTheme.typography.titleMedium)
            }

            if (conversations.isEmpty()) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No saved sessions yet", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tap any subject above or open the AI Tutor workspace to begin your first lesson.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(conversations.take(5), key = { it.id }) { conv ->
                    ElevatedCard(
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTutor(conv.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = conv.subject.take(2).uppercase(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = conv.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = conv.lastMessagePreview.ifBlank { conv.subject },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Resume session",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}
