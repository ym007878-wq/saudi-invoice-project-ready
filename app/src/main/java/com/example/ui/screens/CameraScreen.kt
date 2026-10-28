package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.InvoiceEntity
import com.example.parser.ImageProcessor
import com.example.parser.ParsedInvoiceResult
import com.example.parser.ZatcaQrDecoder
import com.example.viewmodel.ContinuousScanStatus
import com.example.viewmodel.InvoiceViewModel
import com.example.viewmodel.ScreenState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraScreen(
    viewModel: InvoiceViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val capturedBitmaps by viewModel.capturedBitmaps.collectAsState()
    val sessionInvoices by viewModel.sessionInvoices.collectAsState()
    val reviewInvoices by viewModel.reviewInvoices.collectAsState()
    val isContinuousMode by viewModel.isContinuousMode.collectAsState()
    val isContinuousPaused by viewModel.isContinuousPaused.collectAsState()
    val scanStatus by viewModel.continuousScanStatus.collectAsState()

    var showFinishSessionDialog by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler {
        if (sessionInvoices.isNotEmpty()) {
            showFinishSessionDialog = true
        } else {
            onBack()
        }
    }

    // Gallery launcher fallback
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.handleGalleryUris(context, uris)
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.camera_permission_required),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.camera_permission_denied_msg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_permission_button")
                ) {
                    Text(stringResource(R.string.grant_permission))
                }

                Spacer(modifier = Modifier.height(12.dp))

                FilledTonalButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pick_from_gallery_instead_button")
                ) {
                    Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_gallery_pick))
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.outlinedButtonColors(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("العودة للرئيسية")
                }
            }
        }
        return
    }

    // Camera Active Screen with Continuous Live Scanning
    var camera by remember { mutableStateOf<Camera?>(null) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var isManualCapturing by remember { mutableStateOf(false) }

    // Anti-duplicate state tracking
    val isProcessingFrame = remember { AtomicBoolean(false) }
    val scannedSignaturesInSession = remember { mutableSetOf<String>() }
    var lastVisibleSignature by remember { mutableStateOf<String?>(null) }
    var emptyFramesCount by remember { mutableIntStateOf(0) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            barcodeScanner.close()
        }
    }

    // Animated laser scanline sweeping vertically
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_animation")
    val scanLineProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_line_progress"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // CameraX Preview View + Real-time Continuous ImageAnalysis
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    // Live Barcode / QR Code Frame Analyzer
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image

                        // Skip processing if paused or already processing a frame
                        if (mediaImage == null || isContinuousPaused || isProcessingFrame.get()) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        barcodeScanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                if (isContinuousPaused || isProcessingFrame.get()) return@addOnSuccessListener

                                var barcodeFoundInFrame = false

                                for (barcode in barcodes) {
                                    val rawVal = barcode.rawValue ?: continue
                                    barcodeFoundInFrame = true
                                    emptyFramesCount = 0

                                    val zatca = ZatcaQrDecoder.decode(rawVal)
                                    val isRecognized = zatca.isValidZatca || zatca.invoiceTotal != null || zatca.vatRegistrationNumber != null || rawVal.length >= 10

                                    if (isRecognized) {
                                        // Compute unique signature for anti-duplicate prevention
                                        val signature = if (zatca.isValidZatca) {
                                            "${zatca.vatRegistrationNumber ?: ""}_${zatca.timestamp ?: ""}_${zatca.invoiceTotal ?: ""}"
                                        } else {
                                            rawVal.trim()
                                        }

                                        // 1. Check if same invoice is still in view
                                        if (signature == lastVisibleSignature) {
                                            ContextCompat.getMainExecutor(ctx).execute {
                                                viewModel.updateContinuousStatus(
                                                    ContinuousScanStatus.DuplicateDetected("تم حفظ هذه الفاتورة - قدّم الفاتورة التالية")
                                                )
                                            }
                                            break
                                        }

                                        // 2. Check if invoice was already scanned earlier in this session
                                        if (scannedSignaturesInSession.contains(signature)) {
                                            ContextCompat.getMainExecutor(ctx).execute {
                                                viewModel.updateContinuousStatus(
                                                    ContinuousScanStatus.DuplicateDetected("هذه الفاتورة محفوظة مسبقًا في الجلسة")
                                                )
                                            }
                                            break
                                        }

                                        // 3. New Invoice Detected -> Process and Auto-Save!
                                        if (isProcessingFrame.compareAndSet(false, true)) {
                                            lastVisibleSignature = signature
                                            scannedSignaturesInSession.add(signature)

                                            // Trigger haptic vibration
                                            try {
                                                val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                    vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                                                } else {
                                                    @Suppress("DEPRECATION")
                                                    vibrator?.vibrate(100)
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }

                                            // Capture snapshot bitmap for invoice record
                                            val frameBitmap = try {
                                                imageProxy.toBitmap()
                                            } catch (e: Exception) {
                                                null
                                            }

                                            val imageUri = frameBitmap?.let { bmp ->
                                                val rotated = if (imageProxy.imageInfo.rotationDegrees != 0) {
                                                    val matrix = Matrix().apply { postRotate(imageProxy.imageInfo.rotationDegrees.toFloat()) }
                                                    Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                                                } else bmp
                                                ImageProcessor.saveBitmapToCache(ctx, rotated).toString()
                                            }

                                            val total = zatca.invoiceTotal ?: 0.0
                                            val vat = zatca.vatTotal ?: 0.0
                                            val beforeTax = if (total > 0 && vat > 0) {
                                                total - vat
                                            } else if (total > 0) {
                                                Math.round((total / 1.15) * 100.0) / 100.0
                                            } else 0.0

                                            val (unrec, warns) = ParsedInvoiceResult.computeValidation(
                                                supplierName = zatca.sellerName ?: "",
                                                supplierVat = zatca.vatRegistrationNumber ?: "",
                                                invoiceNumber = "",
                                                amountBeforeTax = beforeTax,
                                                vatAmount = vat,
                                                discountAmount = 0.0,
                                                totalWithTax = total,
                                                vatRate = 15.0
                                            )

                                            val parsed = ParsedInvoiceResult(
                                                isSuccess = true,
                                                supplierName = zatca.sellerName ?: "مورد معتمد",
                                                supplierVatNumber = zatca.vatRegistrationNumber ?: "",
                                                invoiceDate = zatca.timestamp ?: "",
                                                amountBeforeTax = beforeTax,
                                                discountAmount = 0.0,
                                                vatRate = 15.0,
                                                vatAmount = vat,
                                                totalWithTax = total,
                                                isZatcaQrDetected = zatca.isValidZatca,
                                                rawContent = rawVal,
                                                imageUri = imageUri,
                                                sourceType = "AUTO_CONTINUOUS",
                                                unrecognizedFields = unrec,
                                                validationWarnings = warns
                                            )

                                            // If in single mode, route to edit/result screen
                                            if (!viewModel.isContinuousMode.value) {
                                                ContextCompat.getMainExecutor(ctx).execute {
                                                    viewModel.onLiveQrDetected(parsed)
                                                }
                                                isProcessingFrame.set(false)
                                                break
                                            }

                                            // Continuous Mode: Auto-Save into Room Database and notify!
                                            scope.launch(Dispatchers.Main) {
                                                viewModel.updateContinuousStatus(
                                                    ContinuousScanStatus.Reading("جارٍ قراءة وحفظ الفاتورة...")
                                                )
                                                viewModel.saveContinuousInvoice(parsed)
                                                
                                                // Short cooldown to allow visual confirmation before accepting next invoice
                                                delay(800)
                                                isProcessingFrame.set(false)
                                            }
                                            break
                                        }
                                    } else {
                                        // Barcode detected but not standard tax invoice -> Add to review queue and continue!
                                        val failedParsed = ParsedInvoiceResult(
                                            isSuccess = false,
                                            rawContent = rawVal,
                                            sourceType = "AUTO_CONTINUOUS_REVIEW",
                                            errorMessage = "رمز غير مطابق لمعايير الفوترة الإلكترونية"
                                        )
                                        ContextCompat.getMainExecutor(ctx).execute {
                                            viewModel.addInvoiceToReviewQueue(failedParsed)
                                        }
                                    }
                                }

                                // If no barcode was detected in this frame
                                if (!barcodeFoundInFrame) {
                                    emptyFramesCount++
                                    // When camera sees no barcode for ~3 frames (invoice removed), reset visible signature
                                    if (emptyFramesCount >= 3) {
                                        lastVisibleSignature = null
                                        if (scanStatus !is ContinuousScanStatus.Saved && !isContinuousPaused) {
                                            viewModel.updateContinuousStatus(ContinuousScanStatus.Idle)
                                        }
                                    }
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Viewfinder Guide Overlay with Animated Live Scanline
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val frameWidth = width * 0.85f
            val frameHeight = height * 0.58f
            val left = (width - frameWidth) / 2
            val top = (height - frameHeight) / 2.35f

            // Dark semi-transparent scrim outside viewfinder
            drawRect(
                color = Color.Black.copy(alpha = 0.45f),
                size = size
            )

            // Clear viewfinder inner window
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(frameWidth, frameHeight),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                blendMode = BlendMode.Clear
            )

            // Viewfinder Border Color: changes dynamically depending on state
            val borderColor = when {
                isContinuousPaused -> Color.Gray
                scanStatus is ContinuousScanStatus.Saved -> Color(0xFF00E676)
                scanStatus is ContinuousScanStatus.DuplicateDetected -> Color(0xFFFFB300)
                else -> Color(0xFF0D5C3A)
            }

            drawRoundRect(
                color = borderColor,
                topLeft = Offset(left, top),
                size = Size(frameWidth, frameHeight),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                style = Stroke(width = 3.dp.toPx())
            )

            // Animated Horizontal Laser Line (only when active and not paused)
            if (!isContinuousPaused) {
                val scanLineY = top + (frameHeight * scanLineProgress)
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color(0xFF00FF7F),
                            Color(0xFF69F0AE),
                            Color(0xFF00FF7F),
                            Color.Transparent
                        )
                    ),
                    start = Offset(left + 8.dp.toPx(), scanLineY),
                    end = Offset(left + frameWidth - 8.dp.toPx(), scanLineY),
                    strokeWidth = 3.dp.toPx()
                )
            }

            // QR Target Corner Box
            val qrBoxSize = 90.dp.toPx()
            val qrLeft = left + 14.dp.toPx()
            val qrTop = top + 14.dp.toPx()
            drawRoundRect(
                color = Color(0xFFD4AF37),
                topLeft = Offset(qrLeft, qrTop),
                size = Size(qrBoxSize, qrBoxSize),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Top Controls Header (Back, Session Counter, Flash)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (sessionInvoices.isNotEmpty()) {
                            showFinishSessionDialog = true
                        } else {
                            onBack()
                        }
                    },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .testTag("camera_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "العودة",
                        tint = Color.White
                    )
                }

                // Live Session Counter Pill
                Surface(
                    color = if (sessionInvoices.isNotEmpty()) Color(0xFF0D5C3A).copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تم مسح: ${sessionInvoices.size} فاتورة",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Flash Mode Toggle
                IconButton(
                    onClick = {
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                            else -> ImageCapture.FLASH_MODE_AUTO
                        }
                        imageCapture.flashMode = flashMode
                    },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .testTag("flash_toggle_button")
                ) {
                    val icon = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff
                        else -> Icons.Default.FlashAuto
                    }
                    Icon(imageVector = icon, contentDescription = "الفلاش", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mode Selector: [المسح التلقائي المستمر] vs [المسح الفردي]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        FilterChip(
                            selected = isContinuousMode,
                            onClick = { viewModel.setContinuousMode(true) },
                            label = { Text("المسح المستمر التلقائي", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0D5C3A),
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("chip_continuous_mode")
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        FilterChip(
                            selected = !isContinuousMode,
                            onClick = { viewModel.setContinuousMode(false) },
                            label = { Text("مسح فاتورة واحدة", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("chip_single_mode")
                        )
                    }
                }
            }
        }

        // Bottom Controls & Real-Time Status Area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Status Banner with State Colors & Feedback
            val statusColor = when (scanStatus) {
                is ContinuousScanStatus.Paused -> Color(0xFF5D4037)
                is ContinuousScanStatus.Saved -> Color(0xFF1B5E20)
                is ContinuousScanStatus.DuplicateDetected -> Color(0xFFE65100)
                is ContinuousScanStatus.FailedToReview -> Color(0xFFB71C1C)
                is ContinuousScanStatus.Reading -> Color(0xFF004D40)
                else -> Color.Black.copy(alpha = 0.75f)
            }

            val statusText = when (scanStatus) {
                is ContinuousScanStatus.Paused -> "المسح متوقف مؤقتًا - اضغط «استئناف» للبدء"
                is ContinuousScanStatus.Saved -> (scanStatus as ContinuousScanStatus.Saved).message
                is ContinuousScanStatus.DuplicateDetected -> (scanStatus as ContinuousScanStatus.DuplicateDetected).message
                is ContinuousScanStatus.FailedToReview -> (scanStatus as ContinuousScanStatus.FailedToReview).reason
                is ContinuousScanStatus.Reading -> (scanStatus as ContinuousScanStatus.Reading).progressText
                else -> if (isContinuousMode) "المسح التلقائي مستمر: وجّه الكاميرا للفاتورة وسيتم حفظها فورًا" else "وجّه الكاميرا للفاتورة لقراءتها"
            }

            Surface(
                color = statusColor,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusIcon = when (scanStatus) {
                        is ContinuousScanStatus.Paused -> Icons.Default.Pause
                        is ContinuousScanStatus.Saved -> Icons.Default.CheckCircle
                        is ContinuousScanStatus.DuplicateDetected -> Icons.Default.Warning
                        is ContinuousScanStatus.FailedToReview -> Icons.Default.Info
                        is ContinuousScanStatus.Reading -> Icons.Default.QrCodeScanner
                        else -> Icons.Default.QrCodeScanner
                    }
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = statusText,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Bar: Pause/Resume, Finish Session, Manual Shutter, Gallery
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pause / Resume Toggle Button
                IconButton(
                    onClick = { viewModel.toggleContinuousPause() },
                    modifier = Modifier
                        .size(52.dp)
                        .background(if (isContinuousPaused) Color(0xFF2E7D32) else Color.White.copy(alpha = 0.25f), CircleShape)
                        .testTag("pause_resume_button")
                ) {
                    Icon(
                        imageVector = if (isContinuousPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isContinuousPaused) "استئناف" else "إيقاف مؤقت",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Shutter Button (Manual backup capture)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(if (isManualCapturing) Color.LightGray else Color(0xFF0D5C3A))
                        .clickable(enabled = !isManualCapturing) {
                            isManualCapturing = true
                            imageCapture.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val bitmap = imageProxyToBitmap(image)
                                        image.close()
                                        if (bitmap != null) {
                                            viewModel.addCapturedBitmap(bitmap)
                                        }
                                        isManualCapturing = false
                                        viewModel.navigateTo(ScreenState.ImageEdit)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        exception.printStackTrace()
                                        isManualCapturing = false
                                    }
                                }
                            )
                        }
                        .testTag("camera_shutter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isManualCapturing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "التقاط يدوي",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Finish Session Button (Appears or lights up when session has invoices)
                IconButton(
                    onClick = { showFinishSessionDialog = true },
                    modifier = Modifier
                        .size(52.dp)
                        .background(if (sessionInvoices.isNotEmpty()) Color(0xFF1B5E20) else Color.White.copy(alpha = 0.25f), CircleShape)
                        .testTag("finish_session_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = "إنهاء الجلسة",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }

    // Finish Session Dialog with Summary & Excel Export
    if (showFinishSessionDialog) {
        val totalSessionVat = sessionInvoices.sumOf { it.vatAmount }
        val totalSessionAmount = sessionInvoices.sumOf { it.totalWithTax }

        AlertDialog(
            onDismissRequest = { showFinishSessionDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ملخص جلسة المسح التلقائي", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "تم مسح وحفظ ${sessionInvoices.size} فاتورة في هذه الجلسة بنجاح.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("إجمالي المبالغ:", style = MaterialTheme.typography.bodySmall)
                        Text("${String.format("%.2f", totalSessionAmount)} ر.س", fontWeight = FontWeight.Bold)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("إجمالي ضريبة 15%:", style = MaterialTheme.typography.bodySmall)
                        Text("${String.format("%.2f", totalSessionVat)} ر.س", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    if (reviewInvoices.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "تنبيه: يوجد ${reviewInvoices.size} رمز مسجل في قائمة المراجعة.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFinishSessionDialog = false
                        viewModel.exportSessionToExcel(context)
                    },
                    modifier = Modifier.testTag("dialog_export_excel_button")
                ) {
                    Icon(imageVector = Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تصدير إلى Excel")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            showFinishSessionDialog = false
                            viewModel.navigateTo(ScreenState.InvoiceList)
                        }
                    ) {
                        Text("سجل الفواتير")
                    }
                    TextButton(onClick = { showFinishSessionDialog = false }) {
                        Text("متابعة المسح")
                    }
                }
            }
        )
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    val planeProxy = image.planes[0]
    val buffer = planeProxy.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

    val rotation = image.imageInfo.rotationDegrees
    return if (rotation != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }
}
