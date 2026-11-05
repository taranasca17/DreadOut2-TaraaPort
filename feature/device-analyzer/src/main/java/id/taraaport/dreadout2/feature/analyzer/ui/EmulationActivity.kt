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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import id.taraaport.dreadout2.feature.analyzer.nativebridge.VulkanNativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

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
                DreadOutMobileHudScreen(
                    romName = romName,
                    resMultiplier = resMult,
                    targetFps = targetFps,
                    driverName = if (driverPath == "SYSTEM_LIBVULKAN") "OEM System" else File(driverPath).name,
                    initialBootLog = bootLogState,
                    onExitEmulation = { finish() }
                )
            }
        }

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
private fun DreadOutMobileHudScreen(
    romName: String,
    resMultiplier: Double,
    targetFps: Int,
    driverName: String,
    initialBootLog: String,
    onExitEmulation: () -> Unit
) {
    var showDiagnosticsOverlay by remember { mutableStateOf(false) }
    var showTouchControls by remember { mutableStateOf(true) }
    var isIrisCameraMode by remember { mutableStateOf(false) }
    var isFlashlightOn by remember { mutableStateOf(false) }
    var lastActionText by remember { mutableStateOf("Mode Eksplorasi") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090E))
    ) {
        // 1. Layar Render Utama (SurfaceView)
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            runCatching {
                                val canvas = holder.lockCanvas()
                                if (canvas != null) {
                                    canvas.drawRGB(9, 11, 16)
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

        // 2. Efek Bingkai Viewfinder saat Mode Kamera IrisPhone Aktif
        if (isIrisCameraMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .border(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            ) {
                Text(
                    text = "● REC [IRIS-GHOST-CAM]",
                    color = Color(0xFFFF5252),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 42.dp, start = 18.dp)
                )
            }
        }

        // 3. Top Status Pill Minimalis (Bergaya Game Mobile)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kiri Atas: Tombol Jeda & Peta/Jurnal
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MobileTopPillButton(icon = "⏸️", label = "Jeda") {
                    lastActionText = "Jeda Permainan (+)"
                }
                MobileTopPillButton(icon = "🗺️", label = "Jurnal") {
                    lastActionText = "Buka Jurnal Linda (-)"
                }
                // Indikator FPS & Aksi
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "$targetFps FPS • ${resMultiplier}x • $lastActionText",
                        color = Color(0xFF81C784),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Kanan Atas: Pengaturan HUD, Log, & Keluar
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MobileTopPillButton(
                    icon = "⚙️",
                    label = if (showDiagnosticsOverlay) "Tutup Info" else "Info Sistem"
                ) {
                    showDiagnosticsOverlay = !showDiagnosticsOverlay
                }
                MobileTopPillButton(
                    icon = "🎮",
                    label = if (showTouchControls) "HUD: ON" else "HUD: OFF"
                ) {
                    showTouchControls = !showTouchControls
                }
                MobileTopPillButton(
                    icon = "🚪",
                    label = "Keluar",
                    bgColor = Color(0xFFC62828).copy(alpha = 0.75f)
                ) {
                    onExitEmulation()
                }
            }
        }

        // 4. Panel Diagnostik Sistem (Muncul bila tombol Info Sistem diklik)
        if (showDiagnosticsOverlay) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 520.dp)
                    .heightIn(max = 260.dp)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141824).copy(alpha = 0.94f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Native Emulation Pipeline — $romName (Driver: $driverName)",
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
                }
            }
        }

        // 5. KONTROLER MOBILE DREADOUT 2 (Ergonomis untuk Jempol Kiri & Kanan)
        if (showTouchControls) {

            // === A. SISI KIRI BAWAH: VIRTUAL ANALOG JOYSTICK ===
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 32.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tombol Lari / Crouch di atas Analog Kiri
                MobileActionCircleButton(
                    icon = "👟",
                    label = "SPRINT",
                    size = 48.dp,
                    accentColor = Color(0xFF90CAF9),
                    onClick = { lastActionText = "Lari Cepat (L-Stick / L)" }
                )
                Spacer(modifier = Modifier.height(12.dp))
                VirtualMobileJoystick(
                    size = 135.dp,
                    onMove = { dirLabel ->
                        lastActionText = "Gerak: $dirLabel"
                    }
                )
            }

            // === B. SISI KANAN BAWAH: CLUSTER AKSI MOBILE DREADOUT 2 ===
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 26.dp, bottom = 20.dp)
                    .size(width = 250.dp, height = 210.dp)
            ) {
                // 1. Tombol Utama Besar (Pojok Kanan Bawah): JEPRET KAMERA / SERANG SENJATA
                MobileActionCircleButton(
                    icon = if (isIrisCameraMode) "📸" else "🪓",
                    label = if (isIrisCameraMode) "BLITZ" else "SERANG",
                    size = 84.dp,
                    accentColor = if (isIrisCameraMode) Color(0xFF00E5FF) else Color(0xFFEF5350),
                    isPrimary = true,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-6).dp, y = (-6).dp),
                    onClick = {
                        lastActionText = if (isIrisCameraMode) "ZR: Jepret Flash IrisPhone!" else "Y: Ayun Senjata Melee!"
                    }
                )

                // 2. Tombol Kiri Bawah (Di sebelah kiri tombol Serang): HINDAR / DODGE (B)
                MobileActionCircleButton(
                    icon = "💨",
                    label = "HINDAR",
                    size = 58.dp,
                    accentColor = Color(0xFFFFB74D),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = 24.dp, y = (-4).dp),
                    onClick = {
                        lastActionText = "B: Menghindar (Dodge)"
                    }
                )

                // 3. Tombol Tengah Melengkung: INTERAKSI / AMBIL BARANG (A)
                MobileActionCircleButton(
                    icon = "🖐️",
                    label = "INTERAKSI",
                    size = 58.dp,
                    accentColor = Color(0xFF81C784),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = (-18).dp, y = 14.dp),
                    onClick = {
                        lastActionText = "A: Periksa / Buka Pintu"
                    }
                )

                // 4. Tombol Atas Tengah: BUKA HP IRIS CAMERA (X)
                MobileActionCircleButton(
                    icon = "📱",
                    label = if (isIrisCameraMode) "TUTUP HP" else "IRIS CAM",
                    size = 60.dp,
                    accentColor = if (isIrisCameraMode) Color(0xFF00E5FF) else Color(0xFFCE93D8),
                    isToggled = isIrisCameraMode,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(x = 18.dp, y = 12.dp),
                    onClick = {
                        isIrisCameraMode = !isIrisCameraMode
                        lastActionText = if (isIrisCameraMode) "X: Mode Kamera Iris Aktif" else "X: Mode Eksplorasi"
                    }
                )

                // 5. Tombol Kanan Atas: BIDIK / FOKUS KAMERA (ZL)
                MobileActionCircleButton(
                    icon = "🎯",
                    label = "FOKUS",
                    size = 56.dp,
                    accentColor = Color(0xFF4DD0E1),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-4).dp, y = 4.dp),
                    onClick = {
                        lastActionText = "ZL: Fokus Bidikan Hantu"
                    }
                )

                // 6. Tombol Senter (R) di Sisi Kiri Atas Cluster
                MobileActionCircleButton(
                    icon = "🔦",
                    label = if (isFlashlightOn) "SENTER ON" else "SENTER",
                    size = 52.dp,
                    accentColor = Color(0xFFFFF176),
                    isToggled = isFlashlightOn,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 8.dp, y = 26.dp),
                    onClick = {
                        isFlashlightOn = !isFlashlightOn
                        lastActionText = if (isFlashlightOn) "R: Senter Dinyalakan" else "R: Senter Dimatikan"
                    }
                )
            }
        }
    }
}

@Composable
private fun VirtualMobileJoystick(
    size: Dp,
    onMove: (String) -> Unit
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    val maxRadiusPx = 110f

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.06f),
                        Color.White.copy(alpha = 0.18f)
                    )
                )
            )
            .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        knobOffset = Offset.Zero
                        onMove("Diam")
                    },
                    onDragCancel = {
                        knobOffset = Offset.Zero
                        onMove("Diam")
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val next = knobOffset + dragAmount
                        val dist = sqrt(next.x * next.x + next.y * next.y)
                        knobOffset = if (dist <= maxRadiusPx) {
                            next
                        } else {
                            val angle = atan2(next.y, next.x)
                            Offset(cos(angle) * maxRadiusPx, sin(angle) * maxRadiusPx)
                        }

                        val dir = when {
                            knobOffset.y < -35f -> "Maju"
                            knobOffset.y > 35f -> "Mundur"
                            knobOffset.x < -35f -> "Kiri"
                            knobOffset.x > 35f -> "Kanan"
                            else -> "Jalan"
                        }
                        onMove(dir)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Cincin Arah Dalam
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = this.size.minDimension / 3.2f
            )
        }

        // Tuas Geser (Thumbstick Knob)
        Box(
            modifier = Modifier
                .offset { IntOffset(knobOffset.x.roundToInt(), knobOffset.y.roundToInt()) }
                .size(54.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.38f))
                .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "✥", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MobileActionCircleButton(
    icon: String,
    label: String,
    size: Dp,
    accentColor: Color,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    isToggled: Boolean = false,
    onClick: () -> Unit
) {
    val bgAlpha = if (isToggled) 0.42f else if (isPrimary) 0.30f else 0.18f
    val borderColor = if (isToggled || isPrimary) accentColor.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.45f)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = bgAlpha))
            .border(if (isPrimary) 2.5.dp else 1.5.dp, borderColor, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = icon,
                fontSize = if (isPrimary) 24.sp else 16.sp
            )
            Text(
                text = label,
                color = Color.White,
                fontSize = if (isPrimary) 10.sp else 8.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                lineHeight = 9.sp
            )
        }
    }
}

@Composable
private fun MobileTopPillButton(
    icon: String,
    label: String,
    bgColor: Color = Color.Black.copy(alpha = 0.55f),
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = icon, fontSize = 11.sp)
        Text(text = label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}