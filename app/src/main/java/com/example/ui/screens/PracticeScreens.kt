package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.FlashcardDeck
import com.example.data.QuizRecord
import com.example.data.SubjectCatalog
import com.example.ui.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeHubScreen(
    quizzesState: UiState<List<QuizRecord>>,
    decksState: UiState<List<FlashcardDeck>>,
    isGenerating: Boolean,
    onGenerateQuiz: (String, String, String, Int) -> Unit,
    onGenerateDeck: (String, String, Int) -> Unit,
    onOpenQuiz: (String) -> Unit,
    onOpenDeck: (String) -> Unit,
    onDeleteQuiz: (String) -> Unit,
    onDeleteDeck: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateQuiz by remember { mutableStateOf(false) }
    var showCreateDeck by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Practice Studio", style = MaterialTheme.typography.headlineMedium) },
            actions = {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 12.dp), strokeWidth = 2.dp)
                } else {
                    FilledTonalButton(
                        onClick = {
                            if (selectedTab == 0) showCreateQuiz = true else showCreateDeck = true
                        },
                        modifier = Modifier.padding(end = 8.dp).testTag("create_practice_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (selectedTab == 0) "New Quiz" else "New Deck")
                    }
                }
            }
        )

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Quizzes") },
                icon = { Icon(Icons.Default.Quiz, contentDescription = null) },
                modifier = Modifier.testTag("practice_tab_quizzes")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Flashcards") },
                icon = { Icon(Icons.Default.Style, contentDescription = null) },
                modifier = Modifier.testTag("practice_tab_flashcards")
            )
        }

        if (selectedTab == 0) {
            when (quizzesState) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is UiState.Error -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    Text(quizzesState.message, color = MaterialTheme.colorScheme.error)
                }
                is UiState.Success -> {
                    val quizzes = quizzesState.data
                    if (quizzes.isEmpty()) {
                        EmptyPracticeCard(
                            title = "No Quizzes Yet",
                            body = "Generate an AI-tailored quiz on any topic with multiple-choice and short-answer questions.",
                            actionLabel = "Generate AI Quiz",
                            onAction = { showCreateQuiz = true }
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(quizzes, key = { it.id }) { quiz ->
                                ElevatedCard(
                                    shape = MaterialTheme.shapes.large,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenQuiz(quiz.id) }
                                        .testTag("quiz_card_${quiz.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(quiz.title, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "${quiz.subject} • ${quiz.difficulty} • ${quiz.totalCount} questions",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Surface(
                                                color = if (quiz.completed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = if (quiz.completed) "Score: ${quiz.score}% (${quiz.correctCount}/${quiz.totalCount})" else "Ready to Take",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        IconButton(onClick = { onDeleteQuiz(quiz.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete quiz")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            when (decksState) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is UiState.Error -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    Text(decksState.message, color = MaterialTheme.colorScheme.error)
                }
                is UiState.Success -> {
                    val decks = decksState.data
                    if (decks.isEmpty()) {
                        EmptyPracticeCard(
                            title = "No Flashcard Decks Yet",
                            body = "Create interactive reveal-answer flashcards to master key formulas, vocabulary, and definitions.",
                            actionLabel = "Generate Flashcards",
                            onAction = { showCreateDeck = true }
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(decks, key = { it.id }) { deck ->
                                ElevatedCard(
                                    shape = MaterialTheme.shapes.large,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenDeck(deck.id) }
                                        .testTag("deck_card_${deck.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(deck.title, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "${deck.subject} • ${deck.cards.size} cards",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            val total = deck.cards.size.coerceAtLeast(1)
                                            LinearProgressIndicator(
                                                progress = { deck.masteredCount.toFloat() / total.toFloat() },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "${deck.masteredCount}/$total mastered",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(onClick = { onDeleteDeck(deck.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete deck")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateQuiz) {
        var subject by remember { mutableStateOf("Mathematics") }
        var topic by remember { mutableStateOf("Quadratic Equations") }
        var difficulty by remember { mutableStateOf("Medium") }
        var count by remember { mutableIntStateOf(5) }
        AlertDialog(
            onDismissRequest = { showCreateQuiz = false },
            title = { Text("Generate AI Quiz") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SubjectCatalog.subjects.forEach { s ->
                            FilterChip(selected = subject == s.name, onClick = { subject = s.name }, label = { Text(s.name, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it },
                        label = { Text("Topic") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("quiz_topic_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Easy", "Medium", "Hard").forEach { d ->
                            FilterChip(selected = difficulty == d, onClick = { difficulty = d }, label = { Text(d) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, 8).forEach { c ->
                            FilterChip(selected = count == c, onClick = { count = c }, label = { Text("$c Qs") })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCreateQuiz = false
                        onGenerateQuiz(subject, topic.ifBlank { subject }, difficulty, count)
                    },
                    modifier = Modifier.testTag("confirm_generate_quiz_btn")
                ) { Text("Generate") }
            },
            dismissButton = { TextButton(onClick = { showCreateQuiz = false }) { Text("Cancel") } }
        )
    }

    if (showCreateDeck) {
        var subject by remember { mutableStateOf("Biology") }
        var topic by remember { mutableStateOf("Cell Organelles & ATP") }
        var count by remember { mutableIntStateOf(6) }
        AlertDialog(
            onDismissRequest = { showCreateDeck = false },
            title = { Text("Generate Flashcard Deck") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SubjectCatalog.subjects.forEach { s ->
                            FilterChip(selected = subject == s.name, onClick = { subject = s.name }, label = { Text(s.name, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it },
                        label = { Text("Topic") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("deck_topic_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(4, 6, 10).forEach { c ->
                            FilterChip(selected = count == c, onClick = { count = c }, label = { Text("$c Cards") })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCreateDeck = false
                        onGenerateDeck(subject, topic.ifBlank { subject }, count)
                    },
                    modifier = Modifier.testTag("confirm_generate_deck_btn")
                ) { Text("Generate") }
            },
            dismissButton = { TextButton(onClick = { showCreateDeck = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EmptyPracticeCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizPlayScreen(
    quiz: QuizRecord?,
    onBack: () -> Unit,
    onSubmitScore: (QuizRecord, Int) -> Unit
) {
    BackHandler { onBack() }
    val questions = remember(quiz?.id) { quiz?.parsedQuestions() ?: emptyList() }
    val selectedOptions = remember(quiz?.id) { mutableStateMapOf<Int, Int>() }
    val typedAnswers = remember(quiz?.id) { mutableStateMapOf<Int, String>() }
    var submitted by remember(quiz?.id) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("quiz_back_btn")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            title = { Text(quiz?.title ?: "Quiz", style = MaterialTheme.typography.titleMedium) }
        )

        if (quiz == null || questions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Quiz not found.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(questions.indices.toList()) { idx ->
                    val q = questions[idx]
                    val isMcq = q.type == "MCQ" && q.options.isNotEmpty()
                    val isCorrect = if (isMcq) {
                        selectedOptions[idx] == q.correctIndex.toInt()
                    } else {
                        typedAnswers[idx]?.trim()?.equals(q.expectedAnswer.trim(), ignoreCase = true) == true ||
                            (typedAnswers[idx]?.trim()?.length ?: 0) >= 3 &&
                            q.expectedAnswer.lowercase().contains(typedAnswers[idx]?.trim()?.lowercase() ?: "___")
                    }

                    ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Question ${idx + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Text(q.prompt, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(10.dp))

                            if (isMcq) {
                                q.options.forEachIndexed { optIdx, opt ->
                                    val chosen = selectedOptions[idx] == optIdx
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !submitted) { selectedOptions[idx] = optIdx }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = chosen,
                                            onClick = { if (!submitted) selectedOptions[idx] = optIdx }
                                        )
                                        Text(opt, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            } else {
                                OutlinedTextField(
                                    value = typedAnswers[idx] ?: "",
                                    onValueChange = { if (!submitted) typedAnswers[idx] = it },
                                    label = { Text("Your Written Answer") },
                                    enabled = !submitted,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (submitted) {
                                Spacer(Modifier.height(10.dp))
                                Surface(
                                    color = if (isCorrect) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = if (isCorrect) "Correct!" else "Expected: ${if (isMcq) q.options.getOrNull(q.correctIndex.toInt()) ?: q.expectedAnswer else q.expectedAnswer}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (q.explanation.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(q.explanation, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    if (!submitted) {
                        Button(
                            onClick = {
                                submitted = true
                                val correct = questions.indices.count { idx ->
                                    val q = questions[idx]
                                    if (q.type == "MCQ" && q.options.isNotEmpty()) {
                                        selectedOptions[idx] == q.correctIndex.toInt()
                                    } else {
                                        val typed = typedAnswers[idx]?.trim().orEmpty()
                                        typed.isNotEmpty() && (
                                            typed.equals(q.expectedAnswer.trim(), ignoreCase = true) ||
                                                q.expectedAnswer.lowercase().contains(typed.lowercase())
                                            )
                                    }
                                }
                                onSubmitScore(quiz, correct)
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("submit_quiz_btn")
                        ) {
                            Text("Submit & Grade Assessment")
                        }
                    } else {
                        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                            Text("Return to Practice Studio")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardStudyScreen(
    deck: FlashcardDeck?,
    onBack: () -> Unit,
    onToggleMastered: (FlashcardDeck, String) -> Unit
) {
    BackHandler { onBack() }
    val cards = deck?.parsedCards() ?: emptyList()
    var currentIndex by remember(deck?.id) { mutableIntStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(350),
        label = "card_flip"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("flashcard_back_btn")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            title = { Text(deck?.title ?: "Flashcards", style = MaterialTheme.typography.titleMedium) }
        )

        if (deck == null || cards.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Flashcard deck not found.")
            }
        } else {
            val safeIdx = currentIndex.coerceIn(0, cards.lastIndex)
            val card = cards[safeIdx]

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Card ${safeIdx + 1} of ${cards.size}", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${deck.masteredCount}/${cards.size} Mastered",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (safeIdx + 1).toFloat() / cards.size.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ElevatedCard(
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp)
                        .graphicsLayer {
                            rotationY = rotation
                            cameraDistance = 12f * density
                        }
                        .clickable { isFlipped = !isFlipped }
                        .testTag("flashcard_flip_surface")
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (rotation <= 90f) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("PROMPT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(14.dp))
                                Text(card.front, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(18.dp))
                                Text("Tap card to reveal answer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.graphicsLayer { rotationY = 180f }
                            ) {
                                Text("ANSWER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.height(14.dp))
                                Text(card.back, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (card.hint.isNotBlank()) {
                        if (showHint) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Hint: ${card.hint}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            TextButton(onClick = { showHint = true }) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Reveal Hint")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (safeIdx > 0) {
                                    currentIndex = safeIdx - 1
                                    isFlipped = false
                                    showHint = false
                                }
                            },
                            enabled = safeIdx > 0,
                            modifier = Modifier.weight(1f)
                        ) { Text("Previous") }

                        FilledTonalButton(
                            onClick = { onToggleMastered(deck, card.id) },
                            modifier = Modifier.weight(1.2f).testTag("toggle_card_mastered_btn")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (card.mastered) "Mastered" else "Mark Mastered")
                        }

                        Button(
                            onClick = {
                                if (safeIdx < cards.lastIndex) {
                                    currentIndex = safeIdx + 1
                                    isFlipped = false
                                    showHint = false
                                } else onBack()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (safeIdx < cards.lastIndex) "Next" else "Done")
                        }
                    }
                }
            }
        }
    }
}
