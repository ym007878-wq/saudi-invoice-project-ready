package com.example.parser

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.InvoiceEntity
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExcelCsvExporter {

    fun exportInvoicesToExcelCsv(context: Context, invoices: List<InvoiceEntity>): Uri? {
        if (invoices.isEmpty()) return null

        try {
            val fileName = "فواتير_ضريبية_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()
            val file = File(exportDir, fileName)

            FileOutputStream(file).use { fos ->
                // Write UTF-8 BOM so Excel opens Arabic correctly
                fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    // CSV Headers
                    writer.write("م,رقم الفاتورة,تاريخ الفاتورة,اسم المورد,الرقم الضريبي للمورد,اسم المشتري,الرقم الضريبي للمشتري,المبلغ قبل الضريبة (ر.س),قيمة الخصم (ر.س),نسبة الضريبة (%),قيمة الضريبة (ر.س),الإجمالي شامل الضريبة (ر.س),نوع المصدر,ملاحظات\n")

                    var totalAmountBefore = 0.0
                    var totalDiscounts = 0.0
                    var totalVat = 0.0
                    var grandTotal = 0.0

                    invoices.forEachIndexed { index, inv ->
                        totalAmountBefore += inv.amountBeforeTax
                        totalDiscounts += inv.discountAmount
                        totalVat += inv.vatAmount
                        grandTotal += inv.totalWithTax

                        writer.write(
                            "${index + 1}," +
                            "\"${escapeCsv(inv.invoiceNumber)}\"," +
                            "\"${escapeCsv(inv.invoiceDate)}\"," +
                            "\"${escapeCsv(inv.supplierName)}\"," +
                            "\"${escapeCsv(inv.supplierVatNumber)}\"," +
                            "\"${escapeCsv(inv.customerName)}\"," +
                            "\"${escapeCsv(inv.customerVatNumber)}\"," +
                            "${String.format(Locale.US, "%.2f", inv.amountBeforeTax)}," +
                            "${String.format(Locale.US, "%.2f", inv.discountAmount)}," +
                            "${String.format(Locale.US, "%.1f", inv.vatRate)}," +
                            "${String.format(Locale.US, "%.2f", inv.vatAmount)}," +
                            "${String.format(Locale.US, "%.2f", inv.totalWithTax)}," +
                            "\"${escapeCsv(inv.sourceType)}\"," +
                            "\"${escapeCsv(inv.notes)}\"\n"
                        )
                    }

                    // Summary Row
                    writer.write(
                        "المجموع,-,-,-,-,-,-," +
                        "${String.format(Locale.US, "%.2f", totalAmountBefore)}," +
                        "${String.format(Locale.US, "%.2f", totalDiscounts)}," +
                        "-," +
                        "${String.format(Locale.US, "%.2f", totalVat)}," +
                        "${String.format(Locale.US, "%.2f", grandTotal)},-,\"تقرير هيئة الزكاة والضريبة والجمارك\"\n"
                    )
                }
            }

            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun shareExportedFile(context: Context, fileUri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, "تصدير الفواتير الضريبية السعودية")
            putExtra(Intent.EXTRA_TEXT, "مرفق سجل الفواتير الضريبية بصيغة CSV المتوافقة مع Microsoft Excel.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "مشاركة تقرير الفواتير (Excel)")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun escapeCsv(str: String): String {
        return str.replace("\"", "\"\"").replace("\n", " ").replace("\r", "")
    }
}
