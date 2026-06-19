package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Keep screen on for standby mode
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    
    // Hide system bars for immersive feel
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    insetsController.hide(WindowInsetsCompat.Type.systemBars())
    
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme(darkTheme = true) {
        StandbyScreen()
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StandbyScreen(viewModel: StandbyViewModel = viewModel()) {
    val plugins by viewModel.plugins.collectAsState()
    val pagerState = rememberPagerState(pageCount = { plugins.size })
    val context = LocalContext.current
    
    val serverIp by viewModel.serverIp.collectAsState()
    val serverPort by viewModel.serverPort.collectAsState()
    val serverPin by viewModel.serverPin.collectAsState()
    
    // Launcher to pick custom HTML/CSS plugin
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.loadPluginFromFile(context, it)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pluginContent = plugins[page]
            PluginWebView(
                htmlContent = pluginContent,
                modifier = Modifier.fillMaxSize()
            )
        }

        1
        // Unobtrusive + button to add a new layout plugin
        IconButton(
            onClick = { filePickerLauncher.launch("text/html") },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Load Custom Plugin",
                tint = Color.White.copy(alpha = 0.3f)
            )
        }
        
        // Native bubble on top indicating the uploader server URL and PIN passcode
        if (serverPort > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .background(
                        color = Color(0xCC121214),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0x40D0BCFF),
                                Color(0x10D0BCFF)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Status dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF8FFF9F), shape = CircleShape)
                    )
                    
                    // IP / Port Text
                    Text(
                        text = "Uploader: http://$serverIp:$serverPort",
                        color = Color(0xFFE6E1E5),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    // Vertical Divider
                    Box(
                        modifier = Modifier
                            .height(16.dp)
                            .width(1.dp)
                            .background(Color(0x33E6E1E5))
                    )
                    
                    // PIN code
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "PIN:",
                            color = Color(0xFF938F99),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Normal
                        )
                        Text(
                            text = serverPin,
                            color = Color(0xFFD0BCFF),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
        
        // Pager indicator logic could go here, but omitted for immersive look
    }
}

