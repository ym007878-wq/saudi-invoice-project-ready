package com.example.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.viewmodel.InvoiceViewModel
import com.example.viewmodel.ScreenState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: InvoiceViewModel,
    onNavigateToCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val invoiceCount by viewModel.invoiceCount.collectAsState()
    val totalVat by viewModel.totalVat.collectAsState()

    // Activity result launcher for picking images from gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.handleGalleryUris(context, uris)
        }
    }

    // Activity result launcher for picking PDF or XML files
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.handlePdfOrXmlUri(context, uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "شعار هيئة الزكاة",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "متوافق مع متطلبات ZATCA",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Stats & Hero Card
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("summary_hero_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        Color(0xFF0F5132)
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "إحصائيات الفواتير والضريبة",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Surface(
                                    color = Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VerifiedUser,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "مفوتر ZATCA",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "إجمالي ضريبة المدخلات",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = "${String.format("%.2f", totalVat)} ر.س",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "الفواتير المحفوظة",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = "$invoiceCount",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section Title
            item {
                Text(
                    text = "الإجراءات والمسح الضريبي",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // 1. زر «تصوير فاتورة»
            item {
                ActionCard(
                    title = stringResource(R.string.btn_camera_capture),
                    subtitle = "فتح الكاميرا ومسح رمز QR والبيانات الضريبية مباشرة",
                    icon = Icons.Default.CameraAlt,
                    badgeColor = MaterialTheme.colorScheme.primary,
                    iconBgColor = MaterialTheme.colorScheme.primaryContainer,
                    testTag = "btn_camera_capture",
                    onClick = onNavigateToCamera
                )
            }

            // 2. زر «اختيار صورة من المعرض»
            item {
                ActionCard(
                    title = stringResource(R.string.btn_gallery_pick),
                    subtitle = "استيراد صور الفواتير أو الإيصالات المحفوظة في الهاتف",
                    icon = Icons.Default.PhotoLibrary,
                    badgeColor = MaterialTheme.colorScheme.secondary,
                    iconBgColor = MaterialTheme.colorScheme.secondaryContainer,
                    testTag = "btn_gallery_pick",
                    onClick = {
                        galleryLauncher.launch("image/*")
                    }
                )
            }

            // 3. زر «اختيار ملفات PDF وXML»
            item {
                ActionCard(
                    title = stringResource(R.string.btn_pick_pdf_xml),
                    subtitle = "استخراج الفاتورة الإلكترونية مباشرة من ملفات UBL XML أو PDF",
                    icon = Icons.Default.Description,
                    badgeColor = Color(0xFF2E6F65),
                    iconBgColor = Color(0xFFD4EDE8),
                    testTag = "btn_pick_pdf_xml",
                    onClick = {
                        documentLauncher.launch(arrayOf("application/pdf", "text/xml", "application/xml"))
                    }
                )
            }

            // 4. زر «قراءة الفاتورة»
            item {
                ActionCard(
                    title = stringResource(R.string.btn_read_invoice),
                    subtitle = "قراءة وتدقيق آخر فاتورة تم التقاطها أو اختيارها",
                    icon = Icons.Default.QrCodeScanner,
                    badgeColor = Color(0xFF0D5C3A),
                    iconBgColor = MaterialTheme.colorScheme.primaryContainer,
                    testTag = "btn_read_invoice",
                    onClick = {
                        if (viewModel.capturedBitmaps.value.isNotEmpty()) {
                            viewModel.processCapturedImages(context)
                        } else {
                            galleryLauncher.launch("image/*")
                        }
                    }
                )
            }

            // 5. زر «فواتيري السابقة»
            item {
                ActionCard(
                    title = stringResource(R.string.btn_previous_invoices),
                    subtitle = "عرض سجل الفواتير المدققة، البحث والتصفية وحساب الضريبة",
                    icon = Icons.Default.History,
                    badgeColor = MaterialTheme.colorScheme.outline,
                    iconBgColor = MaterialTheme.colorScheme.surfaceVariant,
                    testTag = "btn_previous_invoices",
                    onClick = {
                        viewModel.navigateTo(ScreenState.InvoiceList)
                    }
                )
            }

            // 6. زر «تصدير إلى Excel»
            item {
                ActionCard(
                    title = stringResource(R.string.btn_export_excel),
                    subtitle = "تصدير جميع الفواتير إلى ملف Excel (CSV) متوافق مع الحسابات",
                    icon = Icons.Default.FileDownload,
                    badgeColor = Color(0xFF1B5E20),
                    iconBgColor = Color(0xFFC8E6C9),
                    testTag = "btn_export_excel",
                    onClick = {
                        viewModel.exportToExcel(context)
                    }
                )
            }

            // Footer note
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "يدعم التطبيق الفاتورة الضريبية والفاتورة الضريبية المبسطة وتشفير TLV Base64 المعتمد من هيئة الزكاة والضريبة والجمارك.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeColor: Color,
    iconBgColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = iconBgColor,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = badgeColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
