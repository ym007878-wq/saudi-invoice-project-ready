package com.example.parser

import android.content.Context
import android.graphics.Bitmap
import com.example.data.model.InvoiceItem
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.tasks.await
import java.util.regex.Pattern

object InvoiceOcrExtractor {

    suspend fun extractFromBitmap(context: Context, bitmap: Bitmap, imageUri: String? = null): ParsedInvoiceResult {
        // Step 1: Attempt to detect and decode ZATCA QR Code via ZXing first (fast & reliable)
        var qrResult: ZatcaQrResult? = scanQrWithZxing(bitmap)

        // Step 2: If ZXing didn't catch it, try ML Kit Barcode Scanner
        if (qrResult == null || !qrResult.isValidZatca) {
            val mlkitQr = scanQrWithMlKit(bitmap)
            if (mlkitQr != null && mlkitQr.isValidZatca) {
                qrResult = mlkitQr
            }
        }

        // Step 3: Run OCR for full textual content, line items, buyer info, invoice number
        val ocrText = runMlKitOcr(bitmap)

        // Step 4: Parse structured fields from OCR text and merge with QR data
        val parsed = parseArabicInvoiceText(ocrText, qrResult)

        val (unrecognized, warnings) = ParsedInvoiceResult.computeValidation(
            supplierName = parsed.supplierName,
            supplierVat = parsed.supplierVatNumber,
            invoiceNumber = parsed.invoiceNumber,
            amountBeforeTax = parsed.amountBeforeTax,
            vatAmount = parsed.vatAmount,
            discountAmount = parsed.discountAmount,
            totalWithTax = parsed.totalWithTax,
            vatRate = parsed.vatRate
        )

        return parsed.copy(
            imageUri = imageUri,
            rawContent = ocrText,
            isZatcaQrDetected = qrResult?.isValidZatca == true,
            unrecognizedFields = unrecognized,
            validationWarnings = warnings
        )
    }

    private fun scanQrWithZxing(bitmap: Bitmap): ZatcaQrResult? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = MultiFormatReader().decode(binaryBitmap)
            if (!result.text.isNullOrBlank()) {
                ZatcaQrDecoder.decode(result.text)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun scanQrWithMlKit(bitmap: Bitmap): ZatcaQrResult? {
        return try {
            val scanner = BarcodeScanning.getClient()
            val image = InputImage.fromBitmap(bitmap, 0)
            val barcodes = scanner.process(image).await()
            for (barcode in barcodes) {
                val rawVal = barcode.rawValue
                if (!rawVal.isNullOrBlank()) {
                    val decoded = ZatcaQrDecoder.decode(rawVal)
                    if (decoded.isValidZatca) return decoded
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun runMlKitOcr(bitmap: Bitmap): String {
        return try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(image).await()
            visionText.text
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun parseArabicInvoiceText(ocrText: String, qrResult: ZatcaQrResult?): ParsedInvoiceResult {
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }

        var supplierName = qrResult?.sellerName ?: ""
        var supplierVat = qrResult?.vatRegistrationNumber ?: ""
        var customerName = ""
        var customerVat = ""
        var invoiceNumber = ""
        var invoiceDate = qrResult?.timestamp ?: ""
        var amountBeforeTax = 0.0
        var discountAmount = 0.0
        var vatRate = 15.0
        var vatAmount = qrResult?.vatTotal ?: 0.0
        var totalWithTax = qrResult?.invoiceTotal ?: 0.0

        val vatRegex = Regex("""\b(3\d{13}3)\b|\b(\d{15})\b""")
        val dateRegex = Regex("""\b(\d{4}[-/.]\d{1,2}[-/.]\d{1,2}|\d{1,2}[-/.]\d{1,2}[-/.]\d{4})\b""")

        // Find VAT numbers in text
        val vatMatches = vatRegex.findAll(ocrText).map { it.value }.distinct().toList()
        if (supplierVat.isBlank() && vatMatches.isNotEmpty()) {
            supplierVat = vatMatches[0]
            if (vatMatches.size > 1) {
                customerVat = vatMatches[1]
            }
        } else if (vatMatches.size > 1 && customerVat.isBlank()) {
            val second = vatMatches.firstOrNull { it != supplierVat }
            if (second != null) customerVat = second
        }

        // Find Date in text if not extracted from QR
        if (invoiceDate.isBlank()) {
            dateRegex.find(ocrText)?.let {
                invoiceDate = it.value
            }
        }

        // Extract Invoice Number
        val invNumRegex = Regex("""(?:فاتورة رقم|رقم الفاتورة|Invoice\s*No|Inv\s*#|INV-?)[:\s]*([A-Za-z0-9\-_]+)""", RegexOption.IGNORE_CASE)
        invNumRegex.find(ocrText)?.let {
            invoiceNumber = it.groupValues[1].trim()
        }

        // Parse line by line for financial amounts & labels
        for (i in lines.indices) {
            val line = lines[i]

            // Look for supplier name in top header if missing
            if (supplierName.isBlank() && i < 4 && !line.contains(Regex("""\d{4}|فاتورة|Invoice|TAX|VAT"""))) {
                supplierName = line
            }

            // Customer Name
            if (line.contains("العميل") || line.contains("المشتري") || line.contains("Customer") || line.contains("Bill to")) {
                val cleaned = line.replace(Regex("""(?:العميل|المشتري|Customer|Bill to|السيد|السادة)[:\s]*"""), "").trim()
                if (cleaned.isNotBlank()) {
                    customerName = cleaned
                } else if (i + 1 < lines.size) {
                    customerName = lines[i + 1]
                }
            }

            // Amount before tax / Subtotal
            if (amountBeforeTax == 0.0 && (line.contains("قبل الضريبة") || line.contains("غير شامل") || line.contains("Subtotal") || line.contains("Excl. VAT"))) {
                extractFirstNumber(line)?.let { amountBeforeTax = it }
                if (amountBeforeTax == 0.0 && i + 1 < lines.size) {
                    extractFirstNumber(lines[i + 1])?.let { amountBeforeTax = it }
                }
            }

            // Discount
            if (discountAmount == 0.0 && (line.contains("الخصم") || line.contains("تخفيض") || line.contains("Discount"))) {
                extractFirstNumber(line)?.let { discountAmount = it }
            }

            // VAT Amount
            if (vatAmount == 0.0 && (line.contains("ضريبة القيمة المضافة") || line.contains("مبلغ الضريبة") || line.contains("VAT Amount") || line.contains("Tax Amount"))) {
                extractFirstNumber(line)?.let { vatAmount = it }
                if (vatAmount == 0.0 && i + 1 < lines.size) {
                    extractFirstNumber(lines[i + 1])?.let { vatAmount = it }
                }
            }

            // Total With Tax
            if (totalWithTax == 0.0 && (line.contains("الإجمالي شامل") || line.contains("المجموع الكلي") || line.contains("المبلغ المستحق") || line.contains("Total Incl. VAT") || line.contains("Net Amount") || line.contains("Grand Total"))) {
                extractFirstNumber(line)?.let { totalWithTax = it }
                if (totalWithTax == 0.0 && i + 1 < lines.size) {
                    extractFirstNumber(lines[i + 1])?.let { totalWithTax = it }
                }
            }
        }

        // Mathematical inference if some numbers are missing
        if (totalWithTax > 0 && amountBeforeTax == 0.0 && vatAmount > 0) {
            amountBeforeTax = totalWithTax - vatAmount + discountAmount
        } else if (totalWithTax > 0 && amountBeforeTax > 0 && vatAmount == 0.0) {
            vatAmount = totalWithTax - amountBeforeTax + discountAmount
        } else if (totalWithTax == 0.0 && amountBeforeTax > 0 && vatAmount > 0) {
            totalWithTax = amountBeforeTax + vatAmount - discountAmount
        } else if (totalWithTax > 0 && amountBeforeTax == 0.0 && vatAmount == 0.0) {
            // Assume Saudi 15% VAT standard
            amountBeforeTax = Math.round((totalWithTax / 1.15) * 100.0) / 100.0
            vatAmount = Math.round((totalWithTax - amountBeforeTax) * 100.0) / 100.0
        }

        // Line items extraction
        val items = extractItemsFromText(lines)

        return ParsedInvoiceResult(
            isSuccess = true,
            supplierName = supplierName,
            supplierVatNumber = supplierVat,
            customerName = customerName,
            customerVatNumber = customerVat,
            invoiceNumber = invoiceNumber,
            invoiceDate = invoiceDate,
            amountBeforeTax = amountBeforeTax,
            discountAmount = discountAmount,
            vatRate = vatRate,
            vatAmount = vatAmount,
            totalWithTax = totalWithTax,
            items = items
        )
    }

    private fun extractFirstNumber(text: String): Double? {
        val pattern = Pattern.compile("""[^\d]*(\d{1,7}(?:[.,]\d{1,3})?)""")
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            val numStr = matcher.group(1)?.replace(",", ".") ?: return null
            return numStr.toDoubleOrNull()
        }
        return null
    }

    private fun extractItemsFromText(lines: List<String>): List<InvoiceItem> {
        val items = mutableListOf<InvoiceItem>()
        val itemLinePattern = Pattern.compile("""^(.*?)\s+(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)$""")

        for (line in lines) {
            // Check if line contains digits indicating qty and price
            val matcher = itemLinePattern.matcher(line)
            if (matcher.matches()) {
                val name = matcher.group(1)?.trim() ?: ""
                val qty = matcher.group(2)?.toDoubleOrNull() ?: 1.0
                val price = matcher.group(3)?.toDoubleOrNull() ?: 0.0
                val total = matcher.group(4)?.toDoubleOrNull() ?: (qty * price)
                if (name.isNotBlank() && !name.contains(Regex("""المجموع|الإجمالي|ضريبة|Total|Tax""", RegexOption.IGNORE_CASE))) {
                    items.add(InvoiceItem(name = name, quantity = qty, unitPrice = price, total = total))
                }
            }
        }
        return items
    }
}
