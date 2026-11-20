package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BoardElement
import com.example.data.BoardType
import com.example.ui.theme.ChalkCoral
import com.example.ui.theme.ChalkGold
import com.example.ui.theme.ChalkLavender
import com.example.ui.theme.ChalkMint
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.ChalkboardGreenSurface
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.WhiteboardSurface
import java.util.UUID
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class BoardTool {
    PEN,
    MOVE,
    TEXT,
    FORMULA,
    SHAPE
}

private val CHALK_PALETTE = listOf(
    "#F4F3EE" to ChalkWhite,
    "#F4B860" to ChalkGold,
    "#5CE0D2" to ChalkMint,
    "#FF8A75" to ChalkCoral,
    "#C5B4FF" to ChalkLavender
)

@Composable
fun InteractiveChalkboard(
    boardType: BoardType,
    elements: List<BoardElement>,
    onBoardChanged: (BoardType, List<BoardElement>) -> Unit,
    onAskAiExplainBoard: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTool by remember { mutableStateOf(BoardTool.PEN) }
    var selectedColorHex by remember { mutableStateOf("#F4B860") }
    var canvasSize by remember { mutableStateOf(IntSize(800, 600)) }
    val activeStrokePoints = remember { mutableStateListOf<Offset>() }
    var draggedElementId by remember { mutableStateOf<String?>(null) }
    var showAddTextDialog by remember { mutableStateOf(false) }
    var pendingKind by remember { mutableStateOf("LABEL") }
    var draftText by remember { mutableStateOf("") }

    val textMeasurer = rememberTextMeasurer()
    val isLightBoard = boardType == BoardType.GENERAL
    val bgColor = if (isLightBoard) WhiteboardSurface else ChalkboardGreenSurface
    val defaultInkColor = if (isLightBoard) Color(0xFF172221) else ChalkWhite

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        // Board Mode Switcher Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BoardType.entries.forEach { type ->
                FilterChip(
                    selected = boardType == type,
                    onClick = { onBoardChanged(type, elements) },
                    label = { Text(type.label, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.testTag("board_type_${type.name.lowercase()}")
                )
            }
        }

        // Contextual Chalkboard Toolbar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolIconButton(
                    selected = activeTool == BoardTool.PEN,
                    icon = Icons.Default.Brush,
                    label = "Draw",
                    tag = "tool_pen",
                    onClick = { activeTool = BoardTool.PEN }
                )
                ToolIconButton(
                    selected = activeTool == BoardTool.MOVE,
                    icon = Icons.Default.OpenWith,
                    label = "Move",
                    tag = "tool_move",
                    onClick = { activeTool = BoardTool.MOVE }
                )
                ToolIconButton(
                    selected = activeTool == BoardTool.FORMULA,
                    icon = Icons.Default.Functions,
                    label = "Formula",
                    tag = "tool_formula",
                    onClick = {
                        pendingKind = "FORMULA"
                        draftText = ""
                        showAddTextDialog = true
                    }
                )
                ToolIconButton(
                    selected = activeTool == BoardTool.TEXT,
                    icon = Icons.Default.TextFields,
                    label = "Label",
                    tag = "tool_text",
                    onClick = {
                        pendingKind = "LABEL"
                        draftText = ""
                        showAddTextDialog = true
                    }
                )
                IconButton(
                    onClick = {
                        val el = BoardElement(
                            id = UUID.randomUUID().toString().take(8),
                            kind = "RECT",
                            text = "Block",
                            x = 0.25,
                            y = 0.3,
                            x2 = 0.55,
                            y2 = 0.5,
                            colorHex = selectedColorHex
                        )
                        onBoardChanged(boardType, elements + el)
                    },
                    modifier = Modifier.testTag("tool_add_rect")
                ) {
                    Icon(Icons.Default.CropSquare, contentDescription = "Add rectangle")
                }
                IconButton(
                    onClick = {
                        val el = BoardElement(
                            id = UUID.randomUUID().toString().take(8),
                            kind = "CIRCLE",
                            text = "",
                            x = 0.5,
                            y = 0.5,
                            x2 = 0.65,
                            y2 = 0.65,
                            colorHex = selectedColorHex
                        )
                        onBoardChanged(boardType, elements + el)
                    },
                    modifier = Modifier.testTag("tool_add_circle")
                ) {
                    Icon(Icons.Default.Circle, contentDescription = "Add circle")
                }
                IconButton(
                    onClick = {
                        pendingKind = "GRAPH_CURVE"
                        draftText = "x^2"
                        showAddTextDialog = true
                    },
                    modifier = Modifier.testTag("tool_add_curve")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Plot curve")
                }

                Spacer(Modifier.width(6.dp))

                // Color Swatches
                CHALK_PALETTE.forEach { (hex, color) ->
                    val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                            .clickable { selectedColorHex = hex }
                            .testTag("chalk_color_${hex.drop(1)}")
                    )
                }

                Spacer(Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        if (elements.isNotEmpty()) {
                            onBoardChanged(boardType, elements.dropLast(1))
                        }
                    },
                    modifier = Modifier.testTag("tool_undo")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo board action")
                }
                IconButton(
                    onClick = { onBoardChanged(boardType, emptyList()) },
                    modifier = Modifier.testTag("tool_clear")
                ) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear board")
                }

                Button(
                    onClick = {
                        val bmp = renderBoardToBitmap(
                            boardType = boardType,
                            elements = elements,
                            width = canvasSize.width.coerceAtLeast(800),
                            height = canvasSize.height.coerceAtLeast(600)
                        )
                        onAskAiExplainBoard(bmp)
                    },
                    modifier = Modifier.testTag("ask_ai_explain_board")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("AI Check", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Main Interactive Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(
                    width = 2.dp,
                    color = if (isLightBoard) MaterialTheme.colorScheme.outlineVariant else Color(0xFF284F45),
                    shape = RoundedCornerShape(18.dp)
                )
                .background(bgColor)
                .onSizeChanged { canvasSize = it }
                .pointerInput(activeTool, elements, boardType, selectedColorHex) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            if (activeTool == BoardTool.PEN) {
                                activeStrokePoints.clear()
                                activeStrokePoints.add(startOffset)
                            } else if (activeTool == BoardTool.MOVE && canvasSize.width > 0 && canvasSize.height > 0) {
                                val nx = startOffset.x / canvasSize.width
                                val ny = startOffset.y / canvasSize.height
                                draggedElementId = elements.minByOrNull { el ->
                                    val dx = el.x - nx
                                    val dy = el.y - ny
                                    dx * dx + dy * dy
                                }?.takeIf { el ->
                                    val dx = el.x - nx
                                    val dy = el.y - ny
                                    sqrt(dx * dx + dy * dy) < 0.16
                                }?.id
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (activeTool == BoardTool.PEN) {
                                activeStrokePoints.add(change.position)
                            } else if (activeTool == BoardTool.MOVE && draggedElementId != null && canvasSize.width > 0 && canvasSize.height > 0) {
                                val dx = (dragAmount.x / canvasSize.width).toDouble()
                                val dy = (dragAmount.y / canvasSize.height).toDouble()
                                val updated = elements.map { el ->
                                    if (el.id == draggedElementId) {
                                        el.copy(
                                            x = (el.x + dx).coerceIn(0.05, 0.95),
                                            y = (el.y + dy).coerceIn(0.05, 0.95),
                                            x2 = (el.x2 + dx).coerceIn(0.05, 0.98),
                                            y2 = (el.y2 + dy).coerceIn(0.05, 0.98)
                                        )
                                    } else el
                                }
                                onBoardChanged(boardType, updated)
                            }
                        },
                        onDragEnd = {
                            if (activeTool == BoardTool.PEN && activeStrokePoints.size >= 2 && canvasSize.width > 0 && canvasSize.height > 0) {
                                val sampled = downsamplePoints(activeStrokePoints, maxCount = 60).map { pt ->
                                    mapOf(
                                        "x" to (pt.x / canvasSize.width.toDouble()).coerceIn(0.0, 1.0),
                                        "y" to (pt.y / canvasSize.height.toDouble()).coerceIn(0.0, 1.0)
                                    )
                                }
                                val strokeEl = BoardElement(
                                    id = UUID.randomUUID().toString().take(8),
                                    kind = "STROKE",
                                    colorHex = selectedColorHex,
                                    points = sampled
                                )
                                activeStrokePoints.clear()
                                onBoardChanged(boardType, elements + strokeEl)
                            }
                            draggedElementId = null
                        },
                        onDragCancel = {
                            activeStrokePoints.clear()
                            draggedElementId = null
                        }
                    )
                }
                .testTag("interactive_chalkboard_canvas")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawBoardBackgroundGrid(boardType, isLightBoard)

                elements.forEach { el ->
                    drawBoardElement(el, isLightBoard, defaultInkColor, textMeasurer)
                }

                // Active in-progress pen stroke
                if (activeStrokePoints.size >= 2) {
                    val path = Path().apply {
                        moveTo(activeStrokePoints.first().x, activeStrokePoints.first().y)
                        for (i in 1 until activeStrokePoints.size) {
                            lineTo(activeStrokePoints[i].x, activeStrokePoints[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = parseChalkColor(selectedColorHex, defaultInkColor),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }

            if (elements.isEmpty() && activeStrokePoints.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Brush,
                        contentDescription = null,
                        tint = defaultInkColor.copy(alpha = 0.35f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${boardType.label} Ready",
                        style = MaterialTheme.typography.titleMedium,
                        color = defaultInkColor.copy(alpha = 0.65f)
                    )
                    Text(
                        text = "Draw freely, add equations, or ask A-Tutor to illustrate a concept here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = defaultInkColor.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }

    if (showAddTextDialog) {
        val label = when (pendingKind) {
            "FORMULA" -> "Add Mathematical Formula"
            "GRAPH_CURVE" -> "Plot Function (e.g. x^2, sin(x), 2x+1)"
            else -> "Add Board Label / Note"
        }
        AlertDialog(
            onDismissRequest = { showAddTextDialog = false },
            title = { Text(label) },
            text = {
                OutlinedTextField(
                    value = draftText,
                    onValueChange = { draftText = it },
                    label = { Text("Content") },
                    singleLine = pendingKind == "GRAPH_CURVE",
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("board_text_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (draftText.isNotBlank()) {
                            val newEl = BoardElement(
                                id = UUID.randomUUID().toString().take(8),
                                kind = pendingKind,
                                text = draftText.trim(),
                                x = if (pendingKind == "GRAPH_CURVE") 0.5 else 0.18,
                                y = if (pendingKind == "GRAPH_CURVE") 0.15 else (0.18 + (elements.size % 5) * 0.13),
                                colorHex = selectedColorHex,
                                highlighted = pendingKind == "FORMULA"
                            )
                            val targetBoard = if (pendingKind == "GRAPH_CURVE") BoardType.GRAPH else boardType
                            onBoardChanged(targetBoard, elements + newEl)
                        }
                        showAddTextDialog = false
                    },
                    modifier = Modifier.testTag("confirm_board_element")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Place on Board")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTextDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ToolIconButton(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun DrawScope.drawBoardBackgroundGrid(boardType: BoardType, isLightBoard: Boolean) {
    val gridColor = if (isLightBoard) Color(0x180F4C4C) else Color(0x20FFFFFF)
    val axisColor = if (isLightBoard) Color(0x660F4C4C) else Color(0x885CE0D2)
    when (boardType) {
        BoardType.GRAPH -> {
            val step = 36.dp.toPx()
            var x = step
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = step
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawLine(axisColor, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 2.dp.toPx())
            drawLine(axisColor, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 2.dp.toPx())
        }
        BoardType.MATH, BoardType.SCIENCE -> {
            val step = 28.dp.toPx()
            var x = step
            while (x < size.width) {
                var y = step
                while (y < size.height) {
                    drawCircle(gridColor, radius = 1.5.dp.toPx(), center = Offset(x, y))
                    y += step
                }
                x += step
            }
        }
        BoardType.MINDMAP -> {
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawCircle(gridColor, radius = size.minDimension * 0.22f, center = Offset(cx, cy), style = Stroke(1.5f))
            drawCircle(gridColor, radius = size.minDimension * 0.40f, center = Offset(cx, cy), style = Stroke(1.5f))
        }
        else -> {
            // Clean board
        }
    }
}

private fun DrawScope.drawBoardElement(
    el: BoardElement,
    isLightBoard: Boolean,
    defaultInkColor: Color,
    textMeasurer: TextMeasurer
) {
    val ink = parseChalkColor(el.colorHex, defaultInkColor)
    val px = (el.x.toFloat() * size.width).coerceIn(16f, size.width - 16f)
    val py = (el.y.toFloat() * size.height).coerceIn(16f, size.height - 16f)
    val px2 = (el.x2.toFloat() * size.width).coerceIn(16f, size.width - 16f)
    val py2 = (el.y2.toFloat() * size.height).coerceIn(16f, size.height - 16f)

    when (el.kind) {
        "STROKE" -> {
            if (el.points.size >= 2) {
                val path = Path().apply {
                    val first = el.points.first()
                    moveTo(
                        ((first["x"] ?: 0.0).toFloat() * size.width),
                        ((first["y"] ?: 0.0).toFloat() * size.height)
                    )
                    for (i in 1 until el.points.size) {
                        val pt = el.points[i]
                        lineTo(
                            ((pt["x"] ?: 0.0).toFloat() * size.width),
                            ((pt["y"] ?: 0.0).toFloat() * size.height)
                        )
                    }
                }
                drawPath(path, color = ink, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        "LINE" -> {
            drawLine(ink, Offset(px, py), Offset(px2, py2), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        "ARROW" -> {
            drawLine(ink, Offset(px, py), Offset(px2, py2), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            val angle = kotlin.math.atan2((py2 - py).toDouble(), (px2 - px).toDouble())
            val headLen = 12.dp.toPx()
            val p1 = Offset(
                (px2 - headLen * cos(angle - 0.45)).toFloat(),
                (py2 - headLen * sin(angle - 0.45)).toFloat()
            )
            val p2 = Offset(
                (px2 - headLen * cos(angle + 0.45)).toFloat(),
                (py2 - headLen * sin(angle + 0.45)).toFloat()
            )
            drawLine(ink, Offset(px2, py2), p1, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawLine(ink, Offset(px2, py2), p2, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        "RECT" -> {
            val left = minOf(px, px2)
            val top = minOf(py, py2)
            val w = kotlin.math.abs(px2 - px).coerceAtLeast(60.dp.toPx())
            val h = kotlin.math.abs(py2 - py).coerceAtLeast(40.dp.toPx())
            if (el.highlighted) {
                drawRect(ink.copy(alpha = 0.16f), topLeft = Offset(left, top), size = Size(w, h))
            }
            drawRect(ink, topLeft = Offset(left, top), size = Size(w, h), style = Stroke(2.5.dp.toPx()))
            if (el.text.isNotBlank()) {
                val res = textMeasurer.measure(
                    text = el.text,
                    style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                )
                drawText(res, topLeft = Offset(left + 8.dp.toPx(), top + 8.dp.toPx()))
            }
        }
        "CIRCLE" -> {
            val r = (kotlin.math.hypot((px2 - px).toDouble(), (py2 - py).toDouble()).toFloat())
                .coerceIn(22.dp.toPx(), size.minDimension * 0.35f)
            if (el.highlighted) {
                drawCircle(ink.copy(alpha = 0.16f), radius = r, center = Offset(px, py))
            }
            drawCircle(ink, radius = r, center = Offset(px, py), style = Stroke(2.5.dp.toPx()))
            if (el.text.isNotBlank()) {
                val res = textMeasurer.measure(
                    text = el.text,
                    style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                )
                drawText(res, topLeft = Offset(px - res.size.width / 2f, py - res.size.height / 2f))
            }
        }
        "GRAPH_CURVE" -> {
            val path = Path()
            val steps = 80
            val cx = size.width / 2f
            val cy = size.height / 2f
            val scale = size.minDimension / 10f
            var started = false
            for (i in 0..steps) {
                val mathX = -5.0 + (10.0 * i / steps)
                val mathY = evaluateMathExpression(el.text, mathX)
                if (mathY != null && mathY.isFinite()) {
                    val sx = cx + (mathX * scale).toFloat()
                    val sy = (cy - (mathY * scale).toFloat()).coerceIn(-40f, size.height + 40f)
                    if (!started) {
                        path.moveTo(sx, sy)
                        started = true
                    } else {
                        path.lineTo(sx, sy)
                    }
                }
            }
            if (started) {
                drawPath(path, color = ink, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            val labelRes = textMeasurer.measure(
                text = "y = ${el.text}",
                style = TextStyle(color = ink, fontSize = 13.sp, fontFamily = JetBrainsMonoFontFamily)
            )
            drawText(labelRes, topLeft = Offset(16.dp.toPx(), 14.dp.toPx()))
        }
        else -> {
            // FORMULA, LABEL, NODE
            val isFormula = el.kind == "FORMULA"
            val isNode = el.kind == "NODE"
            val maxBoxW = (size.width - px - 16f).coerceAtLeast(120f).toInt()
            val textRes = textMeasurer.measure(
                text = el.text.ifBlank { "Note" },
                style = TextStyle(
                    color = if (isLightBoard && el.colorHex == "#F4F3EE") Color(0xFF172221) else ink,
                    fontSize = if (isFormula) 15.sp else 14.sp,
                    fontWeight = if (isFormula || isNode) FontWeight.SemiBold else FontWeight.Normal,
                    fontFamily = if (isFormula) JetBrainsMonoFontFamily else FontFamily.SansSerif
                ),
                constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxBoxW)
            )
            val padH = 10.dp.toPx()
            val padV = 6.dp.toPx()
            val boxW = textRes.size.width + padH * 2
            val boxH = textRes.size.height + padV * 2
            val boxTopLeft = Offset(px.coerceAtMost(size.width - boxW - 8f).coerceAtLeast(8f), py.coerceAtMost(size.height - boxH - 8f).coerceAtLeast(8f))
            if (el.highlighted || isNode || isFormula) {
                drawRoundRect(
                    color = ink.copy(alpha = if (el.highlighted) 0.18f else 0.10f),
                    topLeft = boxTopLeft,
                    size = Size(boxW, boxH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx())
                )
                drawRoundRect(
                    color = ink.copy(alpha = 0.7f),
                    topLeft = boxTopLeft,
                    size = Size(boxW, boxH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                    style = Stroke(1.5.dp.toPx())
                )
            }
            drawText(
                textLayoutResult = textRes,
                topLeft = Offset(boxTopLeft.x + padH, boxTopLeft.y + padV)
            )
        }
    }
}

private fun parseChalkColor(hex: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

private fun evaluateMathExpression(exprRaw: String, x: Double): Double? {
    val expr = exprRaw.lowercase().replace("y=", "").replace("f(x)=", "").replace(" ", "")
    return when {
        expr.contains("sin") -> sin(x)
        expr.contains("cos") -> cos(x)
        expr.contains("x^3") -> x.pow(3)
        expr.contains("x^2") -> x.pow(2) - 1.0
        expr.contains("sqrt") -> if (x >= 0) sqrt(x) else null
        expr.contains("2x+1") -> 2 * x + 1
        expr.contains("-x") -> -x
        expr == "x" -> x
        else -> x.pow(2) * 0.4 - 1.0
    }
}

private fun downsamplePoints(points: List<Offset>, maxCount: Int): List<Offset> {
    if (points.size <= maxCount) return points
    val step = points.size.toFloat() / maxCount
    return (0 until maxCount).map { i -> points[(i * step).toInt().coerceAtMost(points.lastIndex)] }
}

private fun renderBoardToBitmap(
    boardType: BoardType,
    elements: List<BoardElement>,
    width: Int,
    height: Int
): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val isLight = boardType == BoardType.GENERAL
    canvas.drawColor(if (isLight) WhiteboardSurface.toArgb() else ChalkboardGreenSurface.toArgb())

    val paint = AndroidPaint().apply {
        isAntiAlias = true
        strokeWidth = 5f
        textSize = 34f
    }
    elements.forEach { el ->
        val inkArgb = try {
            android.graphics.Color.parseColor(el.colorHex)
        } catch (_: Exception) {
            if (isLight) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        paint.color = inkArgb
        val px = (el.x.toFloat() * width)
        val py = (el.y.toFloat() * height)
        if (el.kind == "STROKE" && el.points.size >= 2) {
            paint.style = AndroidPaint.Style.STROKE
            val path = AndroidPath()
            path.moveTo((el.points[0]["x"] ?: 0.0).toFloat() * width, (el.points[0]["y"] ?: 0.0).toFloat() * height)
            for (i in 1 until el.points.size) {
                path.lineTo((el.points[i]["x"] ?: 0.0).toFloat() * width, (el.points[i]["y"] ?: 0.0).toFloat() * height)
            }
            canvas.drawPath(path, paint)
        } else if (el.text.isNotBlank()) {
            paint.style = AndroidPaint.Style.FILL
            canvas.drawText(el.text, px, py, paint)
        }
    }
    return bmp
}
