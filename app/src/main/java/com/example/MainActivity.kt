package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.ui.screens.CameraScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImageEditScreen
import com.example.ui.screens.InvoiceDetailScreen
import com.example.ui.screens.InvoiceListScreen
import com.example.ui.screens.InvoiceResultScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.InvoiceViewModel
import com.example.viewmodel.ScreenState

class MainActivity : ComponentActivity() {

    private val viewModel: InvoiceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Ensure RTL layout for authentic Saudi Arabic experience
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MainAppContent(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: InvoiceViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadingMessage by viewModel.loadingMessage.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                is ScreenState.Home -> {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToCamera = {
                            viewModel.navigateTo(ScreenState.Camera)
                        }
                    )
                }
                is ScreenState.Camera -> {
                    CameraScreen(
                        viewModel = viewModel,
                        onBack = {
                            viewModel.navigateTo(ScreenState.Home)
                        }
                    )
                }
                is ScreenState.ImageEdit -> {
                    ImageEditScreen(
                        viewModel = viewModel,
                        onBack = {
                            viewModel.navigateTo(ScreenState.Camera)
                        }
                    )
                }
                is ScreenState.InvoiceResult -> {
                    InvoiceResultScreen(
                        viewModel = viewModel,
                        onBack = {
                            viewModel.navigateTo(ScreenState.Home)
                        }
                    )
                }
                is ScreenState.InvoiceList -> {
                    InvoiceListScreen(
                        viewModel = viewModel,
                        onBack = {
                            viewModel.navigateTo(ScreenState.Home)
                        }
                    )
                }
                is ScreenState.InvoiceDetail -> {
                    InvoiceDetailScreen(
                        invoice = screen.invoice,
                        viewModel = viewModel,
                        onBack = {
                            viewModel.navigateTo(ScreenState.InvoiceList)
                        }
                    )
                }
            }
        }

        // Global Loading Dialog
        if (isLoading) {
            Dialog(onDismissRequest = { /* Non-cancelable during critical extraction */ }) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = loadingMessage ?: "جارٍ المعالجة...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
