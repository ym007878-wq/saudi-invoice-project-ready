package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "invoices")
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
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
    val lineItemsJson: String = "[]",
    val qrRawData: String? = null,
    val imageUri: String? = null,
    val sourceType: String = "CAMERA", // CAMERA, GALLERY, PDF, XML
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
