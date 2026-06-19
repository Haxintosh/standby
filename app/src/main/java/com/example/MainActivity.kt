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
import androidx.compose.material.icons.filled.Settings
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.delay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
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
    
    var burnInProtectionEnabled by remember { mutableStateOf(true) }
    var protectionRatio by remember { mutableStateOf(1) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var delayAfterInteraction by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var isInactive by remember { mutableStateOf(true) }

    LaunchedEffect(lastInteractionTime, delayAfterInteraction) {
        if (delayAfterInteraction) {
            isInactive = false
            val elapsed = System.currentTimeMillis() - lastInteractionTime
            val remaining = 5000L - elapsed
            if (remaining > 0) {
                delay(remaining)
            }
            isInactive = true
        } else {
            isInactive = true
        }
    }
    
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
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastInteractionTime = System.currentTimeMillis()
                    }
                }
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pluginContent = plugins[page]
            Box(modifier = Modifier.fillMaxSize()) {
                PluginWebView(
                    htmlContent = pluginContent,
                    modifier = Modifier.fillMaxSize()
                )
                if (burnInProtectionEnabled && isInactive) {
                    PixelPerfectBurnInMask(
                        modifier = Modifier.fillMaxSize(),
                        protectionRatio = protectionRatio
                    )
                }
            }
        }

        // Unobtrusive Settings/Shield button to toggle OLED protection
        IconButton(
            onClick = { showSettingsDialog = true },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "OLED Protection Settings",
                tint = if (burnInProtectionEnabled) Color(0xFFD0BCFF) else Color.White.copy(alpha = 0.3f)
            )
        }

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
        
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = {
                    Text(
                        text = "OLED Burn-in Protection",
                        color = Color(0xFFE6E1E5),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Enable Protection",
                                color = Color(0xFFE6E1E5),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = burnInProtectionEnabled,
                                onCheckedChange = { burnInProtectionEnabled = it }
                            )
                        }

                        if (burnInProtectionEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Hide on touch (5s delay)",
                                    color = Color(0xFFE6E1E5),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Switch(
                                    checked = delayAfterInteraction,
                                    onCheckedChange = { delayAfterInteraction = it }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))

                            val percentage = (protectionRatio.toFloat() / (protectionRatio + 1) * 100).toInt()
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Protection Strength",
                                        color = Color(0xFF938F99),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "$percentage% Pixels Off",
                                        color = Color(0xFFD0BCFF),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Slider(
                                    value = protectionRatio.toFloat(),
                                    onValueChange = { protectionRatio = it.toInt() },
                                    valueRange = 1f..5f,
                                    steps = 3,
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = Color(0xFFD0BCFF),
                                        inactiveTrackColor = Color(0x33D0BCFF),
                                        thumbColor = Color(0xFFD0BCFF)
                                    )
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Minimal (50%)",
                                        color = Color(0xFF938F99),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        text = "Maximum (83%)",
                                        color = Color(0xFF938F99),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Warning: Disabling protection may lead to screen burn-in on OLED displays.",
                                color = Color(0xFFFFB4AB),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { showSettingsDialog = false }
                    ) {
                        Text("Close", color = Color(0xFFD0BCFF))
                    }
                },
                containerColor = Color(0xFF1C1B1F),
                textContentColor = Color(0xFFE6E1E5)
            )
        }
    }
}

@Composable
fun PixelPerfectBurnInMask(
    modifier: Modifier = Modifier,
    protectionRatio: Int = 1,
    shiftIntervalMs: Long = 10000L
) {
    val n = (protectionRatio + 1).coerceAtLeast(2)
    var shift by remember(n) { mutableStateOf(0) }

    LaunchedEffect(n, shiftIntervalMs) {
        while (true) {
            delay(shiftIntervalMs)
            shift = (shift + 1) % n
        }
    }

    val shaderPaint = remember(n, shift) {
        android.graphics.Paint().apply {
            val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            val off = android.graphics.Color.BLACK
            val on = android.graphics.Color.TRANSPARENT

            for (y in 0 until n) {
                val activeX = (y + shift) % n
                for (x in 0 until n) {
                    if (x == activeX) {
                        bmp.setPixel(x, y, on)
                    } else {
                        bmp.setPixel(x, y, off)
                    }
                }
            }

            shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(
                0f, 0f, size.width, size.height, shaderPaint
            )
        }
    }
}

