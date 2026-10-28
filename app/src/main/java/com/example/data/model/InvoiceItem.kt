package com.example.data.model

data class InvoiceItem(
    val name: String = "",
    val quantity: Double = 1.0,
    val unitPrice: Double = 0.0,
    val total: Double = 0.0
)
