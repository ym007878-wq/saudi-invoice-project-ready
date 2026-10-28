package com.example.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object PdfExtractor {

    data class PdfExtractResult(
        val embeddedXml: String? = null,
        val renderedPages: List<Bitmap> = emptyList(),
        val rawText: String? = null
    )

    fun processPdf(context: Context, uri: Uri): PdfExtractResult {
        val bytes = readBytes(context, uri) ?: return PdfExtractResult()
        val textContent = String(bytes, Charsets.ISO_8859_1)

        // 1. Check for embedded ZATCA XML in hybrid PDF/A-3
        val embeddedXml = extractEmbeddedXml(textContent)

        // 2. Render pages to Bitmaps using native Android PdfRenderer
        val bitmaps = renderPdfPages(context, uri)

        // 3. Extract visible plain text streams from PDF if available
        val extractedText = extractBasicPdfText(textContent)

        return PdfExtractResult(
            embeddedXml = embeddedXml,
            renderedPages = bitmaps,
            rawText = extractedText
        )
    }

    private fun extractEmbeddedXml(pdfContent: String): String? {
        val invoiceStart = pdfContent.indexOf("<Invoice")
        if (invoiceStart != -1) {
            val invoiceEnd = pdfContent.indexOf("</Invoice>", invoiceStart)
            if (invoiceEnd != -1) {
                return pdfContent.substring(invoiceStart, invoiceEnd + 10)
            }
        }
        return null
    }

    private fun extractBasicPdfText(pdfContent: String): String {
        val sb = StringBuilder()
        // Extract text inside parentheses in TJ or Tj PDF operators
        val tjRegex = Regex("""\((.*?)\)\s*T[jJ]""")
        tjRegex.findAll(pdfContent).forEach { match ->
            val text = match.groupValues[1]
            if (text.isNotBlank()) {
                sb.append(text).append(" ")
            }
        }
        return sb.toString().trim()
    }

    private fun renderPdfPages(context: Context, uri: Uri): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        try {
            // Copy to temp file to obtain seekable FileDescriptor for PdfRenderer
            val tempFile = File(context.cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val maxPages = minOf(renderer.pageCount, 5) // Render up to 5 pages

            for (i in 0 until maxPages) {
                val page = renderer.openPage(i)
                // Render at 2x density for sharp OCR
                val width = page.width * 2
                val height = page.height * 2
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmaps.add(bitmap)
                page.close()
            }
            renderer.close()
            pfd.close()
            tempFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return bitmaps
    }

    private fun readBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
