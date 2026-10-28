package com.example.viewmodel

import com.example.data.model.InvoiceEntity

sealed class ContinuousScanStatus {
    object Idle : ContinuousScanStatus()
    object Detecting : ContinuousScanStatus()
    data class Reading(val progressText: String) : ContinuousScanStatus()
    data class Saved(val invoice: InvoiceEntity, val count: Int, val message: String) : ContinuousScanStatus()
    data class DuplicateDetected(val message: String) : ContinuousScanStatus()
    data class FailedToReview(val reason: String) : ContinuousScanStatus()
    object Paused : ContinuousScanStatus()
}
