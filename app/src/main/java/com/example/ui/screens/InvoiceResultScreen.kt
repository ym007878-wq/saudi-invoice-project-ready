package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.InvoiceItem
import com.example.parser.ParsedInvoiceResult
import com.example.viewmodel.InvoiceViewModel
import com.example.viewmodel.ScreenState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceResultScreen(
    viewModel: InvoiceViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val parsedResult by viewModel.currentParsedResult.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()

    var showImageDialog by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }

    BackHandler {
        onBack()
    }

    if (parsedResult == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا توجد بيانات مستخرجة")
        }
        return
    }

    val res = parsedResult!!

    // Local mutable state for editing fields
    var supplierName by remember(res) { mutableStateOf(res.supplierName) }
    var supplierVat by remember(res) { mutableStateOf(res.supplierVatNumber) }
    var customerName by remember(res) { mutableStateOf(res.customerName) }
    var customerVat by remember(res) { mutableStateOf(res.customerVatNumber) }
    var invoiceNumber by remember(res) { mutableStateOf(res.invoiceNumber) }
    var invoiceDate by remember(res) { mutableStateOf(res.invoiceDate) }
    var amountBeforeTaxStr by remember(res) { mutableStateOf(if (res.amountBeforeTax > 0) res.amountBeforeTax.toString() else "") }
    var discountStr by remember(res) { mutableStateOf(if (res.discountAmount > 0) res.discountAmount.toString() else "0.0") }
    var vatRate by remember(res) { mutableStateOf(res.vatRate) }
    var vatAmountStr by remember(res) { mutableStateOf(if (res.vatAmount > 0) res.vatAmount.toString() else "") }
    var totalWithTaxStr by remember(res) { mutableStateOf(if (res.totalWithTax > 0) res.totalWithTax.toString() else "") }
    var itemsList by remember(res) { mutableStateOf(res.items) }
    var notes by remember { mutableStateOf("") }

    // Sync back to ViewModel helper
    fun syncToViewModel() {
        val beforeTax = amountBeforeTaxStr.toDoubleOrNull() ?: 0.0
        val discount = discountStr.toDoubleOrNull() ?: 0.0
        val vatAmt = vatAmountStr.toDoubleOrNull() ?: 0.0
        val total = totalWithTaxStr.toDoubleOrNull() ?: 0.0

        viewModel.updateCurrentInvoice(
            res.copy(
                supplierName = supplierName,
                supplierVatNumber = supplierVat,
                customerName = customerName,
                customerVatNumber = customerVat,
                invoiceNumber = invoiceNumber,
                invoiceDate = invoiceDate,
                amountBeforeTax = beforeTax,
                discountAmount = discount,
                vatRate = vatRate,
                vatAmount = vatAmt,
                totalWithTax = total,
                items = itemsList
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.invoice_details),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("result_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "العودة")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleEditMode() },
                        modifier = Modifier.testTag("toggle_edit_mode_button")
                    ) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.CheckCircle else Icons.Default.Edit,
                            contentDescription = if (isEditMode) "تم التعديل" else "تعديل البيانات",
                            tint = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Sticky Bottom Action Bar: Save / Edit / Re-read
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Re-read button
                    OutlinedButton(
                        onClick = {
                            viewModel.processCapturedImages(context)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("re_read_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.re_read_invoice), maxLines = 1)
                    }

                    // Toggle Edit button
                    FilledTonalButton(
                        onClick = {
                            viewModel.toggleEditMode()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_data_button")
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isEditMode) "إنهاء التعديل" else stringResource(R.string.edit_data), maxLines = 1)
                    }

                    // Save Invoice button
                    Button(
                        onClick = {
                            syncToViewModel()
                            viewModel.saveCurrentInvoice(context, notes)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("save_invoice_button")
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.save_invoice),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Original Image Card & ZATCA Badge Header
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (res.isZatcaQrDetected) Color(0xFF0D5C3A) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (res.isZatcaQrDetected) "رمز ZATCA معتمد" else "استخراج تلقائي",
                                    fontWeight = FontWeight.Bold,
                                    color = if (res.isZatcaQrDetected) Color(0xFF0D5C3A) else MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }

                            if (res.imageUri != null) {
                                TextButton(
                                    onClick = { showImageDialog = true },
                                    modifier = Modifier.testTag("view_original_image_button")
                                ) {
                                    Icon(imageVector = Icons.Default.ZoomIn, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("عرض الأصل")
                                }
                            }
                        }

                        // Thumbnail preview if available
                        if (res.imageUri != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.05f))
                                    .clickable { showImageDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = res.imageUri,
                                    contentDescription = "صورة الفاتورة الأصلية",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("تكبير", color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Calculation Warnings & Missing Fields Alerts
            if (res.validationWarnings.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("warning_card")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "تنبيهات التدقيق الحسابي",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            res.validationWarnings.forEach { warning ->
                                Text(
                                    text = "• $warning",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (res.unrecognizedFields.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "حقول غير مؤكدة: ${res.unrecognizedFields.joinToString("، ")} (يمكنك إدخالها يدويًا)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // 1. بيانات المورد والمشتري (Supplier & Buyer)
            item {
                SectionHeader(title = "المورد والمشتري")
            }

            item {
                OutlinedTextField(
                    value = supplierName,
                    onValueChange = { supplierName = it; syncToViewModel() },
                    label = { Text(stringResource(R.string.supplier_name)) },
                    readOnly = !isEditMode,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_supplier_name")
                )
            }

            item {
                OutlinedTextField(
                    value = supplierVat,
                    onValueChange = { supplierVat = it; syncToViewModel() },
                    label = { Text(stringResource(R.string.supplier_vat)) },
                    readOnly = !isEditMode,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("15 رقم يبدأ وينتهي بـ 3 في السعودية") },
                    modifier = Modifier.fillMaxWidth().testTag("input_supplier_vat")
                )
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it; syncToViewModel() },
                        label = { Text(stringResource(R.string.customer_name)) },
                        readOnly = !isEditMode,
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("input_customer_name")
                    )
                    OutlinedTextField(
                        value = customerVat,
                        onValueChange = { customerVat = it; syncToViewModel() },
                        label = { Text(stringResource(R.string.customer_vat)) },
                        readOnly = !isEditMode,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("input_customer_vat")
                    )
                }
            }

            // 2. بيانات الفاتورة الأساسية
            item {
                SectionHeader(title = "بيانات الفاتورة وتاريخها")
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = invoiceNumber,
                        onValueChange = { invoiceNumber = it; syncToViewModel() },
                        label = { Text(stringResource(R.string.invoice_number)) },
                        readOnly = !isEditMode,
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("input_invoice_number")
                    )
                    OutlinedTextField(
                        value = invoiceDate,
                        onValueChange = { invoiceDate = it; syncToViewModel() },
                        label = { Text(stringResource(R.string.invoice_date)) },
                        readOnly = !isEditMode,
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("input_invoice_date")
                    )
                }
            }

            // 3. المبالغ والضريبة
            item {
                SectionHeader(title = "المبالغ والضريبة")
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountBeforeTaxStr,
                        onValueChange = {
                            amountBeforeTaxStr = it
                            // Auto recalculate VAT and Total if VAT rate is 15%
                            val amt = it.toDoubleOrNull() ?: 0.0
                            val disc = discountStr.toDoubleOrNull() ?: 0.0
                            if (amt > 0) {
                                val autoVat = (amt - disc) * (vatRate / 100.0)
                                vatAmountStr = String.format("%.2f", autoVat)
                                totalWithTaxStr = String.format("%.2f", amt - disc + autoVat)
                            }
                            syncToViewModel()
                        },
                        label = { Text(stringResource(R.string.amount_before_tax)) },
                        readOnly = !isEditMode,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("input_amount_before_tax")
                    )

                    OutlinedTextField(
                        value = discountStr,
                        onValueChange = {
                            discountStr = it
                            val amt = amountBeforeTaxStr.toDoubleOrNull() ?: 0.0
                            val disc = it.toDoubleOrNull() ?: 0.0
                            if (amt > 0) {
                                val autoVat = (amt - disc) * (vatRate / 100.0)
                                vatAmountStr = String.format("%.2f", autoVat)
                                totalWithTaxStr = String.format("%.2f", amt - disc + autoVat)
                            }
                            syncToViewModel()
                        },
                        label = { Text(stringResource(R.string.discount_amount)) },
                        readOnly = !isEditMode,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("input_discount_amount")
                    )
                }
            }

            // VAT Rate Chips (15%, 5%, 0%)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "نسبة الضريبة:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    listOf(15.0, 5.0, 0.0).forEach { rate ->
                        FilterChip(
                            selected = vatRate == rate,
                            onClick = {
                                if (isEditMode) {
                                    vatRate = rate
                                    val amt = amountBeforeTaxStr.toDoubleOrNull() ?: 0.0
                                    val disc = discountStr.toDoubleOrNull() ?: 0.0
                                    val autoVat = (amt - disc) * (rate / 100.0)
                                    vatAmountStr = String.format("%.2f", autoVat)
                                    totalWithTaxStr = String.format("%.2f", amt - disc + autoVat)
                                    syncToViewModel()
                                }
                            },
                            label = { Text("${rate.toInt()}%") },
                            enabled = isEditMode
                        )
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = vatAmountStr,
                        onValueChange = { vatAmountStr = it; syncToViewModel() },
                        label = { Text(stringResource(R.string.vat_amount)) },
                        readOnly = !isEditMode,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("input_vat_amount")
                    )

                    OutlinedTextField(
                        value = totalWithTaxStr,
                        onValueChange = { totalWithTaxStr = it; syncToViewModel() },
                        label = { Text(stringResource(R.string.total_with_tax)) },
                        readOnly = !isEditMode,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("input_total_with_tax")
                    )
                }
            }

            // 4. الأصناف والبنود (Line Items)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader(title = "الأصناف والبنود (${itemsList.size})")

                    if (isEditMode) {
                        TextButton(
                            onClick = { showAddItemDialog = true },
                            modifier = Modifier.testTag("add_item_button")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.add_item))
                        }
                    }
                }
            }

            if (itemsList.isEmpty()) {
                item {
                    Text(
                        text = "لم يتم تفصيل أصناف الفاتورة (مكتفى بالإجماليات أعلاه)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                itemsIndexed(itemsList) { index, item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "الكمية: ${item.quantity} × ${item.unitPrice} ر.س = ${item.total} ر.س",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isEditMode) {
                                IconButton(
                                    onClick = {
                                        val mutable = itemsList.toMutableList()
                                        mutable.removeAt(index)
                                        itemsList = mutable
                                        syncToViewModel()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "حذف الصنف",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. ملاحظات
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات إضافية على الفاتورة") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                )
            }
        }
    }

    // Original Image Zoom Modal Dialog
    if (showImageDialog && res.imageUri != null) {
        Dialog(onDismissRequest = { showImageDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = res.imageUri,
                    contentDescription = "الفاتورة الأصلية بالحجم الكامل",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { showImageDialog = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }
        }
    }

    // Add Item Dialog
    if (showAddItemDialog) {
        var newItemName by remember { mutableStateOf("") }
        var newItemQty by remember { mutableStateOf("1") }
        var newItemPrice by remember { mutableStateOf("0") }

        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = { Text("إضافة صنف جديد للفاتورة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newItemName,
                        onValueChange = { newItemName = it },
                        label = { Text("اسم الصنف") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newItemQty,
                        onValueChange = { newItemQty = it },
                        label = { Text("الكمية") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newItemPrice,
                        onValueChange = { newItemPrice = it },
                        label = { Text("سعر الوحدة (ر.س)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = newItemQty.toDoubleOrNull() ?: 1.0
                        val price = newItemPrice.toDoubleOrNull() ?: 0.0
                        val total = qty * price
                        val updated = itemsList.toMutableList()
                        updated.add(InvoiceItem(name = newItemName.ifBlank { "صنف #${itemsList.size + 1}" }, quantity = qty, unitPrice = price, total = total))
                        itemsList = updated
                        syncToViewModel()
                        showAddItemDialog = false
                    }
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}
