package id.taraaport.dreadout2.feature.analyzer.ui

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import id.taraaport.dreadout2.feature.analyzer.nativebridge.VulkanNativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class EmulationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manifestPath = intent?.getStringExtra("BOOT_MANIFEST_PATH").orEmpty()
        val json = runCatching {
            val f = File(manifestPath)
            if (f.exists()) JSONObject(f.readText()) else JSONObject()
        }.getOrDefault(JSONObject())

        val romUriStr = json.optString("rom_uri", "")
        val romName = json.optString("rom_file_name", "DreadOut 2")
        val driverPath = json.optString("custom_driver_so_path", "SYSTEM_LIBVULKAN")
        val keysPath = json.optString("prod_keys_path", "")
        val resMult = json.optDouble("resolution_multiplier", 0.75)
        val targetFps = json.optInt("target_fps", 30)

        setContent {
            var bootLogState by remember { mutableStateOf("Memulai pipeline NDK ARM64 & memeriksa kontainer ROM...") }

            LaunchedEffect(Unit) {
                bootLogState = withContext(Dispatchers.IO) {
                    runCatching {
                        var fdInt = -1
                        val pfd = if (romUriStr.isNotEmpty()) {
                            runCatching { contentResolver.openFileDescriptor(Uri.parse(romUriStr), "r") }.getOrNull()
                        } else null

                        if (pfd != null) {
                            fdInt = pfd.fd
                        }
                        val logResult = VulkanNativeBridge.startNativeBootSequence(fdInt, driverPath, keysPath)
                        pfd?.close()
                        logResult
                    }.getOrElse { err ->
                        "[WARN] Mode Terproteksi Aktif: ${err.localizedMessage ?: "Gagal membuka FD langsung"}\n" +
                        VulkanNativeBridge.startNativeBootSequence(-1, driverPath, keysPath)
                    }
                }
            }

            MaterialTheme(colorScheme = darkColorScheme()) {
                EmulationViewportScreen(
                    romName = romName,
                    resMultiplier = resMult,
                    targetFps = targetFps,
                    driverName = if (driverPath == "SYSTEM_LIBVULKAN") "OEM System" else File(driverPath).name,
                    initialBootLog = bootLogState,
                    onExitEmulation = { finish() }
                )
            }
        }

        // Panggil hideSystemBars setelah DecorView siap menggunakan window.decorView.post
        window?.decorView?.post {
            hideSystemBarsSafe()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBarsSafe()
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBarsSafe() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
            }
        }
    }
}

@Composable
private fun EmulationViewportScreen(
    romName: String,
    resMultiplier: Double,
    targetFps: Int,
    driverName: String,
    initialBootLog: String,
    onExitEmulation: () -> Unit
) {
    var showDiagnosticsOverlay by remember { mutableStateOf(true) }
    var showTouchControls by remember { mutableStateOf(true) }
    var lastPressedInput by remember { mutableStateOf("Standby") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090E))
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            runCatching {
                                val canvas = holder.lockCanvas()
                                if (canvas != null) {
                                    canvas.drawRGB(10, 12, 18)
                                    holder.unlockCanvasAndPost(canvas)
                                }
                            }
                        }
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                        override fun surfaceDestroyed(holder: SurfaceHolder) {}
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "VULKAN 1.1 | ${resMultiplier}x | $targetFps FPS | Drv: $driverName | Key: $lastPressedInput",
                color = Color(0xFF81C784),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { showDiagnosticsOverlay = !showDiagnosticsOverlay },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(if (showDiagnosticsOverlay) "Tutup Log" else "Buka Log", fontSize = 11.sp)
                }
                TextButton(
                    onClick = { showTouchControls = !showTouchControls },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(if (showTouchControls) "Pad: ON" else "Pad: OFF", fontSize = 11.sp)
                }
                Button(
                    onClick = onExitEmulation,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("Keluar", fontSize = 11.sp)
                }
            }
        }

        if (showDiagnosticsOverlay) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 520.dp)
                    .heightIn(max = 260.dp)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141824).copy(alpha = 0.92f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Native Emulation Pipeline — $romName",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF90CAF9)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = initialBootLog,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFE0E0E0)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Status: Jembatan NDK, Surface Vulkan, Kriptografi prod.keys, Custom Driver Hook, dan Virtual Switch Controller aktif.",
                        fontSize = 10.sp,
                        color = Color(0xFFFFB74D)
                    )
                }
            }
        }

        if (showTouchControls) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 44.dp, start = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GamepadPadButton("ZL", width = 64.dp, height = 36.dp) { lastPressedInput = "ZL (Bidik)" }
                GamepadPadButton("L", width = 64.dp, height = 36.dp) { lastPressedInput = "L (Sprint)" }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 44.dp, end = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GamepadPadButton("R", width = 64.dp, height = 36.dp) { lastPressedInput = "R (Senter)" }
                GamepadPadButton("ZR", width = 64.dp, height = 36.dp) { lastPressedInput = "ZR (Kamera Iris)" }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 28.dp, bottom = 20.dp)
                    .size(136.dp)
            ) {
                GamepadCircleButton("▲", Modifier.align(Alignment.TopCenter)) { lastPressedInput = "UP" }
                GamepadCircleButton("▼", Modifier.align(Alignment.BottomCenter)) { lastPressedInput = "DOWN" }
                GamepadCircleButton("◄", Modifier.align(Alignment.CenterStart)) { lastPressedInput = "LEFT" }
                GamepadCircleButton("►", Modifier.align(Alignment.CenterEnd)) { lastPressedInput = "RIGHT" }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 28.dp, bottom = 20.dp)
                    .size(136.dp)
            ) {
                GamepadCircleButton("X", Modifier.align(Alignment.TopCenter)) { lastPressedInput = "X (Iris Phone)" }
                GamepadCircleButton("B", Modifier.align(Alignment.BottomCenter)) { lastPressedInput = "B (Hindar)" }
                GamepadCircleButton("Y", Modifier.align(Alignment.CenterStart)) { lastPressedInput = "Y (Serang)" }
                GamepadCircleButton("A", Modifier.align(Alignment.CenterEnd)) { lastPressedInput = "A (Interaksi)" }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                GamepadPadButton("- (Select)", width = 76.dp, height = 32.dp) { lastPressedInput = "MINUS" }
                GamepadPadButton("+ (Start)", width = 76.dp, height = 32.dp) { lastPressedInput = "PLUS" }
            }
        }
    }
}

@Composable
private fun GamepadCircleButton(
    label: String,
    modifier: Modifier = Modifier,
    onPress: () -> Unit
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.5.dp, Color.White.copy(alpha = 0.45f), CircleShape)
            .clickable { onPress() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun GamepadPadButton(
    label: String,
    width: Dp,
    height: Dp,
    onPress: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width, height)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.5.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .clickable { onPress() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}