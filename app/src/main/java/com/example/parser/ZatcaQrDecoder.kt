package com.example.parser

import android.util.Base64
import java.nio.charset.StandardCharsets

data class ZatcaQrResult(
    val isValidZatca: Boolean,
    val sellerName: String? = null,
    val vatRegistrationNumber: String? = null,
    val timestamp: String? = null,
    val invoiceTotal: Double? = null,
    val vatTotal: Double? = null,
    val rawData: String? = null
)

object ZatcaQrDecoder {

    /**
     * Decodes a ZATCA TLV Base64-encoded string according to Saudi E-Invoicing specifications.
     */
    fun decode(input: String): ZatcaQrResult {
        val trimmed = input.trim()
        val bytes: ByteArray = try {
            Base64.decode(trimmed, Base64.DEFAULT)
        } catch (e: Exception) {
            // Not a valid base64 or might be already bytes/plain text
            return parseFallback(trimmed)
        }

        if (bytes.isEmpty() || bytes.size < 4) {
            return parseFallback(trimmed)
        }

        var sellerName: String? = null
        var vatNumber: String? = null
        var timestamp: String? = null
        var invoiceTotal: Double? = null
        var vatTotal: Double? = null
        var matchedTags = 0

        var index = 0
        while (index < bytes.size - 1) {
            val tag = bytes[index].toInt() and 0xFF
            val length = bytes[index + 1].toInt() and 0xFF
            index += 2

            if (index + length > bytes.size) {
                break
            }

            val valueBytes = bytes.copyOfRange(index, index + length)
            val value = String(valueBytes, StandardCharsets.UTF_8).trim()
            index += length

            when (tag) {
                1 -> {
                    sellerName = value
                    matchedTags++
                }
                2 -> {
                    vatNumber = value
                    matchedTags++
                }
                3 -> {
                    timestamp = value
                    matchedTags++
                }
                4 -> {
                    invoiceTotal = value.toDoubleOrNull()
                    matchedTags++
                }
                5 -> {
                    vatTotal = value.toDoubleOrNull()
                    matchedTags++
                }
            }
        }

        val isValid = matchedTags >= 2 // At least 2 ZATCA tags matched (e.g. seller + VAT)
        return if (isValid) {
            ZatcaQrResult(
                isValidZatca = true,
                sellerName = sellerName,
                vatRegistrationNumber = vatNumber,
                timestamp = timestamp,
                invoiceTotal = invoiceTotal,
                vatTotal = vatTotal,
                rawData = trimmed
            )
        } else {
            parseFallback(trimmed)
        }
    }

    private fun parseFallback(text: String): ZatcaQrResult {
        // Fallback for non-standard QR codes or plain text key-value pairs
        var seller: String? = null
        var vat: String? = null
        var total: Double? = null
        var vatVal: Double? = null
        var date: String? = null

        // Look for 15-digit VAT number (typically starts with 3 and ends with 3 in KSA)
        val vatRegex = Regex("""\b3\d{13}3\b|\b\d{15}\b""")
        vat = vatRegex.find(text)?.value

        // Look for amounts
        val totalRegex = Regex("""(?:الإجمالي|المجموع|Total)[^\d]*([\d.,]+)""", RegexOption.IGNORE_CASE)
        totalRegex.find(text)?.let {
            val numStr = it.groupValues[1].replace(",", "")
            total = numStr.toDoubleOrNull()
        }

        return ZatcaQrResult(
            isValidZatca = vat != null || total != null,
            sellerName = seller,
            vatRegistrationNumber = vat,
            timestamp = date,
            invoiceTotal = total,
            vatTotal = vatVal,
            rawData = text
        )
    }
}
