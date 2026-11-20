package com.example.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {
    suspend fun exportDocumentToUri(
        context: Context,
        uri: Uri,
        title: String,
        subject: String,
        body: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val document = PdfDocument()
            val pageWidth = 595 // A4 points
            val pageHeight = 842
            val margin = 44f
            val maxWidth = pageWidth - margin * 2

            val headerPaint = Paint().apply {
                color = Color.rgb(15, 76, 76)
                textSize = 20f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            val metaPaint = Paint().apply {
                color = Color.rgb(181, 110, 21)
                textSize = 11f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            val bodyPaint = Paint().apply {
                color = Color.rgb(28, 34, 33)
                textSize = 11.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                isAntiAlias = true
            }
            val rulePaint = Paint().apply {
                color = Color.rgb(210, 206, 196)
                strokeWidth = 1f
            }

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas

            val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
            canvas.drawText("A-TUTOR STUDY NOTES  •  ${subject.uppercase()}  •  $dateStr", margin, margin + 8f, metaPaint)

            var y = margin + 36f
            for (line in wrapText(title, headerPaint, maxWidth)) {
                canvas.drawText(line, margin, y, headerPaint)
                y += 26f
            }
            canvas.drawLine(margin, y, pageWidth - margin, y, rulePaint)
            y += 22f

            val paragraphs = body.lines()
            for (paragraph in paragraphs) {
                val clean = paragraph.trim()
                if (clean.isEmpty()) {
                    y += 10f
                    continue
                }
                val wrapped = wrapText(clean, bodyPaint, maxWidth)
                for (line in wrapped) {
                    if (y > pageHeight - margin - 20f) {
                        document.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        page = document.startPage(pageInfo)
                        canvas = page.canvas
                        y = margin + 12f
                    }
                    canvas.drawText(line, margin, y, bodyPaint)
                    y += 17f
                }
                y += 4f
            }
            document.finishPage(page)

            context.contentResolver.openOutputStream(uri)?.use { out ->
                document.writeTo(out)
            } ?: error("Unable to open output stream for PDF export.")

            document.close()
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current.clear()
                current.append(candidate)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current.clear()
                current.append(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return if (lines.isEmpty()) listOf("") else lines
    }
}
