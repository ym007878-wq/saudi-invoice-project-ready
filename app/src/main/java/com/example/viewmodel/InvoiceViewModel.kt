package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.model.InvoiceEntity
import com.example.data.model.InvoiceItem
import com.example.parser.ExcelCsvExporter
import com.example.parser.ImageProcessor
import com.example.parser.InvoiceOcrExtractor
import com.example.parser.ParsedInvoiceResult
import com.example.parser.PdfExtractor
import com.example.parser.ZatcaXmlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ScreenState {
    object Home : ScreenState()
    object Camera : ScreenState()
    object ImageEdit : ScreenState()
    object InvoiceResult : ScreenState()
    object InvoiceList : ScreenState()
    data class InvoiceDetail(val invoice: InvoiceEntity) : ScreenState()
}

class InvoiceViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val invoiceDao = db.invoiceDao()

    // Navigation state
    private val _currentScreen = MutableStateFlow<ScreenState>(ScreenState.Home)
    val currentScreen: StateFlow<ScreenState> = _currentScreen.asStateFlow()

    // Image capture / batch processing state
    private val _capturedBitmaps = MutableStateFlow<List<Bitmap>>(emptyList())
    val capturedBitmaps: StateFlow<List<Bitmap>> = _capturedBitmaps.asStateFlow()

    private val _selectedImageIndex = MutableStateFlow(0)
    val selectedImageIndex: StateFlow<Int> = _selectedImageIndex.asStateFlow()

    // Active parsed invoice
    private val _currentParsedResult = MutableStateFlow<ParsedInvoiceResult?>(null)
    val currentParsedResult: StateFlow<ParsedInvoiceResult?> = _currentParsedResult.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    // Loading / error states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingMessage = MutableStateFlow<String?>(null)
    val loadingMessage: StateFlow<String?> = _loadingMessage.asStateFlow()

    private val _infoBanner = MutableStateFlow<String?>(null)
    val infoBanner: StateFlow<String?> = _infoBanner.asStateFlow()

    // Search and list filter
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Continuous Auto-Scan Session States
    private val _sessionInvoices = MutableStateFlow<List<InvoiceEntity>>(emptyList())
    val sessionInvoices: StateFlow<List<InvoiceEntity>> = _sessionInvoices.asStateFlow()

    private val _reviewInvoices = MutableStateFlow<List<ParsedInvoiceResult>>(emptyList())
    val reviewInvoices: StateFlow<List<ParsedInvoiceResult>> = _reviewInvoices.asStateFlow()

    private val _isContinuousMode = MutableStateFlow(true)
    val isContinuousMode: StateFlow<Boolean> = _isContinuousMode.asStateFlow()

    private val _isContinuousPaused = MutableStateFlow(false)
    val isContinuousPaused: StateFlow<Boolean> = _isContinuousPaused.asStateFlow()

    private val _continuousScanStatus = MutableStateFlow<ContinuousScanStatus>(ContinuousScanStatus.Idle)
    val continuousScanStatus: StateFlow<ContinuousScanStatus> = _continuousScanStatus.asStateFlow()


    val allInvoices: StateFlow<List<InvoiceEntity>> = combine(
        invoiceDao.getAllInvoices(),
        _searchQuery
    ) { invoices, query ->
        if (query.isBlank()) {
            invoices
        } else {
            invoices.filter {
                it.supplierName.contains(query, ignoreCase = true) ||
                it.supplierVatNumber.contains(query) ||
                it.customerName.contains(query, ignoreCase = true) ||
                it.invoiceNumber.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalVat: StateFlow<Double> = invoiceDao.getTotalVat()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val invoiceCount: StateFlow<Int> = invoiceDao.getInvoiceCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun navigateTo(screen: ScreenState) {
        _currentScreen.value = screen
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun addCapturedBitmap(bitmap: Bitmap) {
        val current = _capturedBitmaps.value.toMutableList()
        current.add(bitmap)
        _capturedBitmaps.value = current
    }

    fun clearCapturedBitmaps() {
        _capturedBitmaps.value = emptyList()
        _selectedImageIndex.value = 0
    }

    fun selectImageIndex(index: Int) {
        if (index in _capturedBitmaps.value.indices) {
            _selectedImageIndex.value = index
        }
    }

    fun rotateCurrentImage() {
        val list = _capturedBitmaps.value.toMutableList()
        val idx = _selectedImageIndex.value
        if (idx in list.indices) {
            val rotated = ImageProcessor.rotateBitmap(list[idx], 90f)
            list[idx] = rotated
            _capturedBitmaps.value = list
        }
    }

    fun enhanceCurrentImage() {
        val list = _capturedBitmaps.value.toMutableList()
        val idx = _selectedImageIndex.value
        if (idx in list.indices) {
            val enhanced = ImageProcessor.enhanceInvoiceImage(list[idx])
            list[idx] = enhanced
            _capturedBitmaps.value = list
            _infoBanner.value = "تم تحسين التباين وحدّة الخطوط"
        }
    }

    fun onLiveQrDetected(result: ParsedInvoiceResult) {
        _currentParsedResult.value = result
        _isEditMode.value = false
        _currentScreen.value = ScreenState.InvoiceResult
    }

    fun processCapturedImages(context: Context) {
        val bitmaps = _capturedBitmaps.value
        if (bitmaps.isEmpty()) {
            Toast.makeText(context, "الرجاء التقاط صورة الفاتورة أولاً", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _loadingMessage.value = "جارٍ مسح رمز QR وقراءة نصوص الفاتورة..."
            try {
                // Save primary page to cache for preview
                val primaryBitmap = bitmaps.first()
                val imageUri = withContext(Dispatchers.IO) {
                    ImageProcessor.saveBitmapToCache(context, primaryBitmap).toString()
                }

                val result = withContext(Dispatchers.IO) {
                    InvoiceOcrExtractor.extractFromBitmap(context, primaryBitmap, imageUri)
                }

                _currentParsedResult.value = result
                _isEditMode.value = false
                _currentScreen.value = ScreenState.InvoiceResult
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "تعذر قراءة الفاتورة: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                _isLoading.value = false
                _loadingMessage.value = null
            }
        }
    }

    fun handleGalleryUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isLoading.value = true
            _loadingMessage.value = "جارٍ تحميل الصور المحددة..."
            val loaded = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    ImageProcessor.loadBitmapFromUri(context, uri)
                }
            }
            if (loaded.isNotEmpty()) {
                _capturedBitmaps.value = loaded
                _selectedImageIndex.value = 0
                _currentScreen.value = ScreenState.ImageEdit
            } else {
                Toast.makeText(context, "فشل في قراءة الصور المحددة", Toast.LENGTH_SHORT).show()
            }
            _isLoading.value = false
            _loadingMessage.value = null
        }
    }

    fun handlePdfOrXmlUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _loadingMessage.value = "جارٍ فحص الملف واستخراج الفاتورة..."
            try {
                val contentResolver = context.contentResolver
                val type = contentResolver.getType(uri) ?: ""
                val fileName = uri.lastPathSegment ?: ""

                val isXml = type.contains("xml", ignoreCase = true) || fileName.endsWith(".xml", ignoreCase = true)
                val isPdf = type.contains("pdf", ignoreCase = true) || fileName.endsWith(".pdf", ignoreCase = true)

                if (isXml) {
                    val result = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            ZatcaXmlParser.parse(stream)
                        }
                    }
                    if (result != null && result.isSuccess) {
                        val (unrec, warns) = ParsedInvoiceResult.computeValidation(
                            result.supplierName, result.supplierVatNumber, result.invoiceNumber,
                            result.amountBeforeTax, result.vatAmount, result.discountAmount,
                            result.totalWithTax, result.vatRate
                        )
                        _currentParsedResult.value = result.copy(
                            unrecognizedFields = unrec,
                            validationWarnings = warns,
                            sourceType = "XML"
                        )
                        _currentScreen.value = ScreenState.InvoiceResult
                        Toast.makeText(context, "تم تحليل الفاتورة الإلكترونية XML بنجاح", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, result?.errorMessage ?: "ملف XML غير صالح", Toast.LENGTH_LONG).show()
                    }
                } else if (isPdf) {
                    val pdfResult = withContext(Dispatchers.IO) {
                        PdfExtractor.processPdf(context, uri)
                    }

                    if (pdfResult.embeddedXml != null) {
                        // Priority 1: ZATCA Hybrid PDF/A-3 with embedded XML
                        val xmlParsed = ZatcaXmlParser.parse(pdfResult.embeddedXml)
                        if (xmlParsed.isSuccess) {
                            val (unrec, warns) = ParsedInvoiceResult.computeValidation(
                                xmlParsed.supplierName, xmlParsed.supplierVatNumber, xmlParsed.invoiceNumber,
                                xmlParsed.amountBeforeTax, xmlParsed.vatAmount, xmlParsed.discountAmount,
                                xmlParsed.totalWithTax, xmlParsed.vatRate
                            )
                            _currentParsedResult.value = xmlParsed.copy(
                                unrecognizedFields = unrec,
                                validationWarnings = warns,
                                sourceType = "PDF_XML"
                            )
                            _currentScreen.value = ScreenState.InvoiceResult
                            Toast.makeText(context, "تم استخراج الفاتورة الإلكترونية المضمنة في PDF", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                    }

                    // Priority 2: Use rendered PDF pages
                    if (pdfResult.renderedPages.isNotEmpty()) {
                        _capturedBitmaps.value = pdfResult.renderedPages
                        _selectedImageIndex.value = 0
                        processCapturedImages(context)
                    } else {
                        Toast.makeText(context, "لم يتم العثور على صفحات قابلة للقراءة في ملف PDF", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Try parsing as XML, fallback to image
                    val stream = contentResolver.openInputStream(uri)
                    val sample = stream?.bufferedReader()?.use { it.readText() } ?: ""
                    if (sample.startsWith("<?xml") || sample.contains("<Invoice")) {
                        val xmlResult = ZatcaXmlParser.parse(sample)
                        if (xmlResult.isSuccess) {
                            _currentParsedResult.value = xmlResult
                            _currentScreen.value = ScreenState.InvoiceResult
                            return@launch
                        }
                    }
                    Toast.makeText(context, "نوع الملف غير مدعوم (يرجى اختيار PDF أو XML)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "خطأ أثناء معالجة الملف: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                _isLoading.value = false
                _loadingMessage.value = null
            }
        }
    }

    fun toggleEditMode() {
        _isEditMode.value = !_isEditMode.value
    }

    fun updateCurrentInvoice(updated: ParsedInvoiceResult) {
        val (unrec, warns) = ParsedInvoiceResult.computeValidation(
            updated.supplierName, updated.supplierVatNumber, updated.invoiceNumber,
            updated.amountBeforeTax, updated.vatAmount, updated.discountAmount,
            updated.totalWithTax, updated.vatRate
        )
        _currentParsedResult.value = updated.copy(
            unrecognizedFields = unrec,
            validationWarnings = warns
        )
    }

    fun saveCurrentInvoice(context: Context, notes: String = "") {
        val current = _currentParsedResult.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val entity = current.toEntity(notes)
                withContext(Dispatchers.IO) {
                    invoiceDao.insertInvoice(entity)
                }
                Toast.makeText(context, "تم حفظ الفاتورة بنجاح في السجل", Toast.LENGTH_SHORT).show()
                _currentScreen.value = ScreenState.InvoiceList
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "حدث خطأ أثناء حفظ الفاتورة: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteInvoice(invoice: InvoiceEntity, context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                invoiceDao.deleteInvoice(invoice)
            }
            Toast.makeText(context, "تم حذف الفاتورة", Toast.LENGTH_SHORT).show()
            if (_currentScreen.value is ScreenState.InvoiceDetail) {
                _currentScreen.value = ScreenState.InvoiceList
            }
        }
    }

    fun exportToExcel(context: Context) {
        val list = allInvoices.value
        if (list.isEmpty()) {
            Toast.makeText(context, "لا توجد فواتير لتصديرها. احفظ بعض الفواتير أولاً.", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _loadingMessage.value = "جارٍ إنشاء ملف Excel وتنسيق الأعمدة..."
            try {
                val uri = withContext(Dispatchers.IO) {
                    ExcelCsvExporter.exportInvoicesToExcelCsv(context, list)
                }
                if (uri != null) {
                    ExcelCsvExporter.shareExportedFile(context, uri)
                    Toast.makeText(context, "تم تجهيز ملف Excel للمشاركة والتصدير", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "فشل إنشاء ملف Excel", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "خطأ في التصدير: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                _isLoading.value = false
                _loadingMessage.value = null
            }
        }
    }

    fun setContinuousMode(enabled: Boolean) {
        _isContinuousMode.value = enabled
    }

    fun toggleContinuousPause() {
        val next = !_isContinuousPaused.value
        _isContinuousPaused.value = next
        _continuousScanStatus.value = if (next) ContinuousScanStatus.Paused else ContinuousScanStatus.Idle
    }

    fun setContinuousPaused(paused: Boolean) {
        _isContinuousPaused.value = paused
        _continuousScanStatus.value = if (paused) ContinuousScanStatus.Paused else ContinuousScanStatus.Idle
    }

    fun updateContinuousStatus(status: ContinuousScanStatus) {
        _continuousScanStatus.value = status
    }

    suspend fun saveContinuousInvoice(result: ParsedInvoiceResult): InvoiceEntity {
        val entity = result.toEntity("مسح تلقائي مستمر")
        val id = withContext(Dispatchers.IO) {
            invoiceDao.insertInvoice(entity)
        }
        val savedEntity = entity.copy(id = id)
        val currentList = _sessionInvoices.value.toMutableList()
        currentList.add(savedEntity)
        _sessionInvoices.value = currentList
        _continuousScanStatus.value = ContinuousScanStatus.Saved(
            invoice = savedEntity,
            count = currentList.size,
            message = "تم حفظ فاتورة #${currentList.size}: ${savedEntity.supplierName.ifBlank { "مورد" }} بمبلغ ${String.format("%.2f", savedEntity.totalWithTax)} ر.س"
        )
        return savedEntity
    }

    fun addInvoiceToReviewQueue(result: ParsedInvoiceResult) {
        val current = _reviewInvoices.value.toMutableList()
        current.add(result)
        _reviewInvoices.value = current
        _continuousScanStatus.value = ContinuousScanStatus.FailedToReview(
            reason = result.errorMessage ?: "فاتورة تحتاج مراجعة - أضيفت للقائمة"
        )
    }

    fun resetContinuousSession() {
        _sessionInvoices.value = emptyList()
        _reviewInvoices.value = emptyList()
        _continuousScanStatus.value = ContinuousScanStatus.Idle
        _isContinuousPaused.value = false
    }

    fun exportSessionToExcel(context: Context) {
        val list = _sessionInvoices.value.ifEmpty { allInvoices.value }
        if (list.isEmpty()) {
            Toast.makeText(context, "لا توجد فواتير لتصديرها", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _loadingMessage.value = "جارٍ تصدير فواتير الجلسة إلى Excel..."
            try {
                val uri = withContext(Dispatchers.IO) {
                    ExcelCsvExporter.exportInvoicesToExcelCsv(context, list)
                }
                if (uri != null) {
                    ExcelCsvExporter.shareExportedFile(context, uri)
                } else {
                    Toast.makeText(context, "فشل إنشاء ملف Excel", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "خطأ في التصدير: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                _isLoading.value = false
                _loadingMessage.value = null
            }
        }
    }
}
