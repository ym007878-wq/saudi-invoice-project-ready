package com.example.parser

import com.example.data.model.InvoiceEntity
import com.example.data.model.InvoiceItem

data class ParsedInvoiceResult(
    val isSuccess: Boolean = true,
    val errorMessage: String? = null,
    val supplierName: String = "",
    val supplierVatNumber: String = "",
    val customerName: String = "",
    val customerVatNumber: String = "",
    val invoiceNumber: String = "",
    val invoiceDate: String = "",
    val amountBeforeTax: Double = 0.0,
    val discountAmount: Double = 0.0,
    val vatRate: Double = 15.0,
    val vatAmount: Double = 0.0,
    val totalWithTax: Double = 0.0,
    val items: List<InvoiceItem> = emptyList(),
    val isZatcaQrDetected: Boolean = false,
    val rawContent: String? = null,
    val imageUri: String? = null,
    val sourceType: String = "CAMERA",
    val unrecognizedFields: List<String> = emptyList(),
    val validationWarnings: List<String> = emptyList()
) {
    fun toEntity(notes: String = ""): InvoiceEntity {
        // Convert items to JSON string representation
        val itemsJson = buildString {
            append("[")
            items.forEachIndexed { idx, it ->
                append("{\"name\":\"${escape(it.name)}\",\"quantity\":${it.quantity},\"unitPrice\":${it.unitPrice},\"total\":${it.total}}")
                if (idx < items.size - 1) append(",")
            }
            append("]")
        }

        return InvoiceEntity(
            supplierName = supplierName,
            supplierVatNumber = supplierVatNumber,
            customerName = customerName,
            customerVatNumber = customerVatNumber,
            invoiceNumber = invoiceNumber,
            invoiceDate = invoiceDate,
            amountBeforeTax = amountBeforeTax,
            discountAmount = discountAmount,
            vatRate = vatRate,
            vatAmount = vatAmount,
            totalWithTax = totalWithTax,
            lineItemsJson = itemsJson,
            qrRawData = rawContent,
            imageUri = imageUri,
            sourceType = sourceType,
            notes = notes
        )
    }

    private fun escape(s: String): String {
        return s.replace("\"", "\\\"").replace("\n", " ").replace("\r", "")
    }

    companion object {
        fun computeValidation(
            supplierName: String,
            supplierVat: String,
            invoiceNumber: String,
            amountBeforeTax: Double,
            vatAmount: Double,
            discountAmount: Double,
            totalWithTax: Double,
            vatRate: Double
        ): Pair<List<String>, List<String>> {
            val unrecognized = mutableListOf<String>()
            val warnings = mutableListOf<String>()

            if (supplierName.isBlank()) unrecognized.add("اسم المورد")
            if (supplierVat.isBlank()) unrecognized.add("الرقم الضريبي للمورد")
            if (invoiceNumber.isBlank()) unrecognized.add("رقم الفاتورة")
            if (amountBeforeTax <= 0.0 && totalWithTax <= 0.0) unrecognized.add("المبالغ المالية")

            // VAT 15-digit check in Saudi Arabia
            if (supplierVat.isNotBlank()) {
                val cleanVat = supplierVat.replace(" ", "").replace("-", "")
                if (cleanVat.length != 15 || !cleanVat.startsWith("3") || !cleanVat.endsWith("3")) {
                    warnings.add("الرقم الضريبي للمورد غير متطابق مع نسق هيئة الزكاة (15 رقم يبدأ وينتهي بـ 3)")
                }
            }

            // Mathematical calculation check: Subtotal + VAT - Discount ≈ Total
            if (amountBeforeTax > 0 && totalWithTax > 0) {
                val calculatedTotal = amountBeforeTax + vatAmount - discountAmount
                val diff = Math.abs(calculatedTotal - totalWithTax)
                if (diff > 0.05) {
                    warnings.add("تنبيه حسابي: (المبلغ قبل الضريبة: ${String.format("%.2f", amountBeforeTax)} + الضريبة: ${String.format("%.2f", vatAmount)} - الخصم: ${String.format("%.2f", discountAmount)}) = ${String.format("%.2f", calculatedTotal)} لا يساوي الإجمالي المسجل: ${String.format("%.2f", totalWithTax)}")
                }

                // Check VAT rate percentage (Saudi standard 15%)
                if (vatAmount > 0 && vatRate > 0) {
                    val expectedVat = (amountBeforeTax - discountAmount) * (vatRate / 100.0)
                    if (Math.abs(expectedVat - vatAmount) > 0.10) {
                        warnings.add("تنبيه: قيمة الضريبة (${String.format("%.2f", vatAmount)}) تختلف عن نسبة ${vatRate}% المقدرة (${String.format("%.2f", expectedVat)})")
                    }
                }
            }

            return Pair(unrecognized, warnings)
        }
    }
}
